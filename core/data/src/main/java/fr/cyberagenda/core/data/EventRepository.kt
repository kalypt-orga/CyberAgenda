@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package fr.cyberagenda.core.data

import fr.cyberagenda.core.database.EventDao
import fr.cyberagenda.core.database.EventEntity
import fr.cyberagenda.core.database.FavoriteEntity
import fr.cyberagenda.core.database.SourceSyncDao
import fr.cyberagenda.core.database.SourceSyncEntity
import fr.cyberagenda.core.geo.FrenchGeocoder
import fr.cyberagenda.core.model.EventFormat
import fr.cyberagenda.core.model.Deduplicator
import fr.cyberagenda.core.model.Event
import fr.cyberagenda.core.model.EventFilter
import fr.cyberagenda.core.model.EventValidator
import fr.cyberagenda.core.model.PriceHints
import fr.cyberagenda.core.model.AddressHints
import fr.cyberagenda.core.model.SourceError
import fr.cyberagenda.core.model.SourceState
import fr.cyberagenda.core.network.SafeUrl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EventRepository @Inject constructor(
    private val registry: SourceRegistry,
    private val preferences: SourcePreferences,
    private val eventDao: EventDao,
    private val syncDao: SourceSyncDao,
    private val geocoder: FrenchGeocoder,
    private val settings: AppSettings,
) {
    private val now: Long get() = System.currentTimeMillis()

    /**
     * Heure de référence des listes (« à venir » / « terminés »). Mise à jour à chaque retour
     * de l'app au premier plan : un événement terminé pendant l'absence change de catégorie.
     */
    private val clock = MutableStateFlow(System.currentTimeMillis())

    fun tickClock() { clock.value = System.currentTimeMillis() }

    private val enabledIds: Flow<Set<String>> = preferences.disabledSourceIds.map { disabled ->
        // Une source en travaux n'est jamais active, quel que soit le réglage.
        registry.all.filter { it.meta.available }.map { it.meta.id }.toSet() - disabled
    }

    /** Événements à venir des sources actives, sans doublons entre sources. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeUpcoming(): Flow<List<Event>> = combine(enabledIds, clock, geocoder.ready, ::Triple).flatMapLatest { (ids, t, geoReady) ->
        eventDao.observeUpcoming(ids, t).map { list ->
            Deduplicator.merge(list.map { it.model(geoReady) }, ::sourcePriority)
        }
    }.flowOn(Dispatchers.Default) // calculs hors du thread de l'interface

    /** Filtre commun (persistant) et son application : mêmes résultats dans toutes les vues. */
    val filter: Flow<EventFilter> get() = settings.filter
    suspend fun setFilter(f: EventFilter) = settings.setFilter(f)

    fun observeFiltered(): Flow<List<Event>> =
        combine(observeUpcoming(), observeFavoriteIds(), settings.filter) { events, favorites, f ->
            events.filter { e -> f.matches(e, e.allIds.any { it in favorites }) }
        }.flowOn(Dispatchers.Default)

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeUpcomingFavorites(): Flow<List<Event>> =
        clock.flatMapLatest { t -> eventDao.observeUpcomingFavorites(t) }.map { l -> l.map { it.model() } }.flowOn(Dispatchers.Default)

    /**
     * En cas de doublon, quelle source fait foi (son lien et sa description sont affichés).
     * Ordre de confiance : la sélection signée, puis les listes tenues par une personne ou
     * relues avant publication, et en dernier CTFtime, où n'importe qui peut déclarer un
     * événement : une fiche CTFtime ne peut donc jamais remplacer celle d'une autre source.
     */
    private fun sourcePriority(sourceId: String): Int = when (sourceId) {
        "selection" -> 0
        "frcyberevents" -> 1
        "confstech" -> 2
        "agendadulibre" -> 3
        "openagenda" -> 4
        "ctftime" -> 5
        else -> 6
    }

    fun sourceName(id: String): String = registry[id]?.meta?.name ?: id

    /** « CTFtime » ou, pour un événement fusionné, « CTFtime + Cybersecurity Events FR ». */
    fun sourceLabel(event: Event): String =
        (listOf(event.sourceId) + event.duplicateSourceIds).distinct().joinToString(" + ") { sourceName(it) }

    fun observeEvent(id: String): Flow<Event?> = eventDao.observeById(id).map { it?.model() }.flowOn(Dispatchers.Default)

    fun observeSeries(event: Event): Flow<List<Event>> =
        clock.flatMapLatest { t -> eventDao.observeSeries(event.sourceId, event.title, event.location, t) }.map { l -> l.map { it.model() } }.flowOn(Dispatchers.Default)

    fun observeFavoriteIds(): Flow<Set<String>> = eventDao.observeFavoriteIds().map { it.toSet() }

    fun observeIsFavorite(id: String): Flow<Boolean> = eventDao.observeIsFavorite(id)

    /** Ajoute l'identifiant principal ; retirer un favori retire aussi ceux de ses doublons. */
    suspend fun setFavorite(event: Event, favorite: Boolean) {
        if (favorite) eventDao.addFavorite(FavoriteEntity(event.id, now))
        else event.allIds.forEach { eventDao.removeFavorite(it) }
    }

    fun observeSources(): Flow<List<SourceState>> = combine(
        preferences.disabledSourceIds,
        syncDao.observeAll(),
        clock.flatMapLatest { t -> eventDao.observeCounts(t) },
    ) { disabled, syncs, counts ->
        registry.all.map { source ->
            val id = source.meta.id
            val sync = syncs.firstOrNull { it.sourceId == id }
            SourceState(
                meta = source.meta,
                enabled = source.meta.available && id !in disabled,
                lastSuccessEpochMs = sync?.lastSuccess,
                lastError = sync?.lastError?.let { runCatching { SourceError.valueOf(it) }.getOrNull() },
                eventCount = counts.firstOrNull { it.sourceId == id }?.count ?: 0,
            )
        }
    }

    suspend fun setSourceEnabled(id: String, enabled: Boolean) {
        if (registry[id]?.meta?.available != true) return
        preferences.setEnabled(id, enabled)
        // Source désactivée : on purge son cache (on ne garde que ce qui est utile).
        if (!enabled) eventDao.deleteBySource(id)
    }

    /**
     * Rafraîchit les sources actives en parallèle. Une source en échec n'affecte pas les autres.
     * Tout le traitement (décodage, nettoyage, heuristiques) tourne sur Dispatchers.Default :
     * jamais sur le thread de l'interface, quel que soit l'appelant.
     */
    suspend fun refresh(force: Boolean = false): Unit = withContext(Dispatchers.Default) { coroutineScope {
        val enabled = enabledIds.first()
        registry.all
            .filter { it.meta.id in enabled }
            .map { source -> async { refreshSource(source, force) } }
            .awaitAll()
        // Sources en travaux : rien de leurs anciennes données ne reste affiché.
        registry.all.filterNot { it.meta.available }.forEach { eventDao.deleteBySource(it.meta.id) }
        // Purge : tout ce qui est terminé depuis plus de 3 mois est effacé de l'appareil.
        eventDao.purgeEndedBefore(now - RETENTION_MS)
    } }

    /** Événements terminés depuis moins de 3 mois (sources actives, filtre commun appliqué). */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeRecentPast(): Flow<List<Event>> {
        val past = combine(enabledIds, clock, geocoder.ready, ::Triple).flatMapLatest { (ids, t, geoReady) ->
            eventDao.observePast(ids, t - RETENTION_MS, t).map { l -> Deduplicator.merge(l.map { it.model(geoReady) }, ::sourcePriority) }
        }.flowOn(Dispatchers.Default)
        return combine(past, observeFavoriteIds(), settings.filter) { events, favorites, f ->
            events.filter { e -> f.matches(e, e.allIds.any { it in favorites }) }
        }.flowOn(Dispatchers.Default)
    }

    private suspend fun refreshSource(source: EventSource, force: Boolean) {
        val id = source.meta.id
        val previous = syncDao.get(id)
        val last = previous?.lastSuccess
        if (last != null) {
            val wait = if (force) source.forcedRefreshFloorMs else maxOf(MIN_REFRESH_INTERVAL_MS, source.refreshIntervalMs ?: 0)
            if (now - last < wait) return
        }

        val error: SourceError? = try {
            val validated = source.fetch()
                .take(MAX_EVENTS_PER_SOURCE)
                .filter { it.sourceId == id } // une source ne peut écrire que dans son espace
                .mapNotNull { EventValidator.sanitize(it, SafeUrl::isHttps, now) }
                .map(PriceHints::apply)
                .map(AddressHints::apply)
                .distinctBy { it.id }
                .let { withContext(Dispatchers.IO) { it.map(::geocode) } } // lecture de la table des communes
            // Rien de plus ancien que la durée de conservation n'est enregistré.
            val keepFrom = now - RETENTION_MS
            eventDao.replaceSource(id, validated.filter { it.endsAtEpochMs >= keepFrom }.map { it.toEntity() }, now)
            null
        } catch (e: CancellationException) {
            throw e
        } catch (_: IOException) {
            SourceError.NETWORK
        } catch (_: SignatureException) {
            SourceError.SIGNATURE
        } catch (_: InvalidSourceDataException) {
            SourceError.INVALID_DATA
        } catch (_: SerializationException) {
            SourceError.INVALID_DATA
        } catch (_: Exception) {
            SourceError.UNKNOWN
        }
        syncDao.upsert(
            SourceSyncEntity(
                sourceId = id,
                lastSuccess = if (error == null) now else previous?.lastSuccess,
                lastError = error?.name,
            )
        )
    }

    /**
     * Modèle d'affichage : ajoute le lieu court des miniatures, « (84) Avignon » (géocodage hors
     * ligne). Tant que la table des communes n'est pas chargée ([geoReady] faux, quelques
     * centaines de ms au démarrage), on n'attend pas : la miniature affiche le lieu brut, puis
     * les listes sont recalculées quand la table est prête.
     */
    private fun EventEntity.model(geoReady: Boolean = geocoder.ready.value): Event {
        val e = toModel()
        val location = e.location
        return if (!geoReady || e.format == EventFormat.ONLINE || location.isNullOrBlank()) e
        else e.copy(placeLabel = geocoder.shortLabel(location))
    }

    /** Place sur la carte les événements sur place dont la source ne fournit pas de coordonnées. */
    private fun geocode(event: Event): Event {
        if (event.format == EventFormat.ONLINE || event.latitude != null) return event
        val point = event.location?.let(geocoder::locate) ?: return event
        return event.copy(latitude = point.latitude, longitude = point.longitude)
    }

    val beginnerTipDismissed: Flow<Boolean> get() = preferences.beginnerTipDismissed
    suspend fun dismissBeginnerTip() = preferences.dismissBeginnerTip()

    companion object {
        /** Durée de conservation des événements terminés. */
        const val RETENTION_MS = 90L * 24 * 60 * 60 * 1000
        const val MIN_REFRESH_INTERVAL_MS = 6L * 60 * 60 * 1000
        const val MAX_EVENTS_PER_SOURCE = 500
    }
}
