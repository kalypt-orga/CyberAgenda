package fr.cyberagenda.core.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.cyberagenda.core.model.RecentPlace
import fr.cyberagenda.core.model.RecentSearches
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Historiques de recherche, uniquement sur ce téléphone (préférences locales, exclues des
 * sauvegardes) : recherches texte validées de l'agenda, et villes choisies sur la carte.
 */
@Singleton
class SearchHistory @Inject constructor(@ApplicationContext private val context: Context) {
    private val queriesKey = stringPreferencesKey("history_queries")
    private val placesKey = stringPreferencesKey("history_places")

    val queries: Flow<List<String>> = context.dataStore.data.map { decodeQueries(it[queriesKey]) }
    val places: Flow<List<RecentPlace>> = context.dataStore.data.map { decodePlaces(it[placesKey]) }

    suspend fun addQuery(raw: String) {
        val q = RecentSearches.cleanQuery(raw) ?: return
        context.dataStore.edit { p ->
            p[queriesKey] = encodeQueries(RecentSearches.push(decodeQueries(p[queriesKey]), q, RecentSearches::key))
        }
    }

    suspend fun removeQuery(q: String) {
        context.dataStore.edit { p ->
            p[queriesKey] = encodeQueries(decodeQueries(p[queriesKey]).filter { RecentSearches.key(it) != RecentSearches.key(q) })
        }
    }

    /** Ajoute la zone, ou la remonte en tête avec son nouveau rayon. */
    suspend fun addPlace(place: RecentPlace) {
        context.dataStore.edit { p ->
            p[placesKey] = encodePlaces(RecentSearches.push(decodePlaces(p[placesKey]), place, { it.key }))
        }
    }

    suspend fun removePlace(place: RecentPlace) {
        context.dataStore.edit { p -> p[placesKey] = encodePlaces(decodePlaces(p[placesKey]).filter { it.key != place.key }) }
    }

    suspend fun clear() {
        context.dataStore.edit { it.remove(queriesKey); it.remove(placesKey) }
    }

    internal companion object {
        // Une entrée par ligne ; les champs d'une ville sont séparés par des tabulations
        // (retirés des textes à l'enregistrement, donc sans ambiguïté).
        fun encodeQueries(list: List<String>) = list.joinToString("\n")
        fun decodeQueries(raw: String?): List<String> =
            raw.orEmpty().lines().mapNotNull { RecentSearches.cleanQuery(it) }.take(RecentSearches.MAX_STORED)

        fun encodePlaces(list: List<RecentPlace>) = list.joinToString("\n") { p ->
            listOf(field(p.name), field(p.department), "%.5f".format(Locale.ROOT, p.latitude), "%.5f".format(Locale.ROOT, p.longitude), p.radiusKm)
                .joinToString("\t")
        }
        fun decodePlaces(raw: String?): List<RecentPlace> = raw.orEmpty().lines().mapNotNull { line ->
            val c = line.split('\t')
            if (c.size != 5) return@mapNotNull null
            val lat = c[2].toDoubleOrNull()?.takeIf { it in -90.0..90.0 } ?: return@mapNotNull null
            val lon = c[3].toDoubleOrNull()?.takeIf { it in -180.0..180.0 } ?: return@mapNotNull null
            val radius = c[4].toIntOrNull()?.takeIf { it in 1..500 } ?: return@mapNotNull null
            RecentPlace(c[0].take(80), c[1].take(4), lat, lon, radius).takeIf { it.name.isNotBlank() }
        }.take(RecentSearches.MAX_STORED)

        private fun field(s: String) = s.replace('\t', ' ').replace('\n', ' ').trim()
    }
}
