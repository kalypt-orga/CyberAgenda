package fr.cyberagenda.feature.map

import androidx.lifecycle.ViewModel
import fr.cyberagenda.core.model.RecentPlace
import fr.cyberagenda.core.data.SearchHistory
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.cyberagenda.core.data.EventRepository
import fr.cyberagenda.core.geo.City
import fr.cyberagenda.core.geo.FrenchGeocoder
import fr.cyberagenda.core.geo.GeoPoint
import fr.cyberagenda.core.model.Event
import fr.cyberagenda.core.model.EventFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject

data class PlaceEvent(val event: Event, val sourceName: String, val favorite: Boolean, val distanceKm: Double? = null, val otherDates: Int = 0)

/** Un point sur la carte : tous les événements situés au même endroit. */
data class Place(val key: String, val latitude: Double, val longitude: Double, val label: String, val events: List<PlaceEvent>, val past: Boolean = false)

/** Position de caméra mémorisée (le ViewModel survit aux changements d'onglet, pas la carte). */
data class SavedCamera(val latitude: Double, val longitude: Double, val zoom: Double)

/**
 * Zone de recherche : une ville et un rayon. [request] change à chaque sélection explicite,
 * pour recentrer la carte même si l'on re-choisit la même ville.
 */
data class Area(val city: City, val radiusKm: Int, val request: Long = 0)

data class MapUiState(
    val places: List<Place> = emptyList(),
    val onlineCount: Int = 0,
    val selectedKey: String? = null,
    val query: String = "",
    val suggestions: List<City> = emptyList(),
    /** Recherche terminée sans aucune commune trouvée (affiche « Aucune commune trouvée »). */
    val noCityFound: Boolean = false,
    val area: Area? = null,
    val nearby: List<PlaceEvent> = emptyList(),
    val filter: EventFilter = EventFilter(),
    /** Événements terminés depuis moins de 3 mois, affichés à la demande. */
    val showPast: Boolean = false,
    val pastPlaces: List<Place> = emptyList(),
    /** Zones récemment choisies (ville + rayon), les plus récentes d'abord. */
    val recentPlaces: List<RecentPlace> = emptyList(),
) {
    val selected: Place? get() = (places + pastPlaces).firstOrNull { it.key == selectedKey }
    val onsiteCount: Int get() = places.sumOf { it.events.size }
}

private data class SearchState(
    val query: String = "",
    val suggestions: List<City> = emptyList(),
    val area: Area? = null,
    /** Dernier texte dont la recherche est terminée (null tant qu'aucune n'a abouti). */
    val searched: String? = null,
)

@HiltViewModel
class MapViewModel @Inject constructor(
    private val repository: EventRepository,
    private val geocoder: FrenchGeocoder,
    private val searchHistory: SearchHistory,
) : ViewModel() {
    private val selectedKey = MutableStateFlow<String?>(null)
    // « Passés 3 mois » fait partie du filtre commun (agenda, calendrier, carte).
    private val past = combine(repository.observeRecentPast(), repository.filter) { list, f -> f.includePast to list }
    private val search = MutableStateFlow(SearchState())
    private var requests = 0L
    private var lastRadius = 50

    /** Dernière vue de la carte, restaurée quand on revient sur l'onglet. */
    var camera: SavedCamera? = null
    /** Dernière demande de cadrage déjà appliquée : une carte recréée ne la rejoue pas. */
    var handledAreaRequest: Long = -1

    val state: StateFlow<MapUiState> = combine(
        combine(repository.observeFiltered(), repository.observeFavoriteIds(), ::Pair),
        selectedKey, search, repository.filter, past,
    ) { (events, favorites), key, s, filter, (pastOn, pastEvents) ->
        val located = events.filter { it.latitude != null && it.longitude != null }
        fun item(e: Event, d: Double? = null) = PlaceEvent(e, repository.sourceLabel(e), e.allIds.any { it in favorites }, d)
        fun places(list: List<Event>, isPast: Boolean) = list
            .filter { it.latitude != null && it.longitude != null }
            .groupBy { String.format(Locale.ROOT, "%.3f,%.3f", it.latitude, it.longitude) }
            .map { (k, group) ->
                Place(
                    key = (if (isPast) "past-" else "") + k,
                    latitude = group.first().latitude!!, longitude = group.first().longitude!!,
                    label = group.first().location?.split(',')?.last()?.trim().orEmpty(),
                    events = collapseSeries(group.map { item(it) }, newestFirst = isPast),
                    past = isPast,
                )
            }
        val places = places(events, isPast = false)
        val nearby = s.area?.let { area ->
            collapseSeries(located.mapNotNull { e ->
                val d = area.city.point.distanceKm(GeoPoint(e.latitude!!, e.longitude!!))
                if (d <= area.radiusKm) item(e, d) else null
            })
        }.orEmpty()
        MapUiState(
            places, events.size - located.size, key, s.query, s.suggestions,
            noCityFound = s.area == null && noCityFor(s.query, s.searched, s.suggestions),
            area = s.area, nearby = nearby, filter = filter,
            showPast = pastOn, pastPlaces = if (pastOn) places(pastEvents, isPast = true) else emptyList(),
        )
    }.combine(searchHistory.places) { s, recent -> s.copy(recentPlaces = recent) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MapUiState())

    fun select(key: String?) { selectedKey.value = key }


    fun onQueryChange(query: String) {
        val q = query.take(60)
        // Modifier le texte = nouvelle recherche : on quitte la zone en cours et on repropose des villes.
        search.value = search.value.copy(query = q, area = null)
        viewModelScope.launch {
            // Le premier appel charge la table des communes depuis les assets : hors du thread UI.
            val found = withContext(Dispatchers.IO) { geocoder.search(q) }
            if (search.value.query == q) search.value = search.value.copy(suggestions = found, searched = q)
        }
    }

    fun chooseCity(city: City) {
        val radius = search.value.area?.radiusKm ?: lastRadius
        search.value = SearchState(query = city.name, area = Area(city, radius, request = ++requests))
        rememberPlace(city, radius)
    }

    /** Zone reprise depuis l'historique : même ville, même rayon. */
    fun chooseRecent(place: RecentPlace) {
        lastRadius = place.radiusKm
        search.value = SearchState() // le rayon mémorisé s'applique, pas celui de la zone en cours
        chooseCity(City(place.name, place.department, GeoPoint(place.latitude, place.longitude)))
    }

    fun removeRecent(place: RecentPlace) { viewModelScope.launch { searchHistory.removePlace(place) } }

    private fun rememberPlace(city: City, radiusKm: Int) {
        viewModelScope.launch {
            searchHistory.addPlace(RecentPlace(city.name, city.department, city.point.latitude, city.point.longitude, radiusKm))
        }
    }

    fun setRadius(km: Int) {
        lastRadius = km
        // Nouveau rayon = nouveau cadrage (pour que le cercle tienne à l'écran).
        search.value.area?.let {
            search.value = search.value.copy(area = it.copy(radiusKm = km, request = ++requests))
            rememberPlace(it.city, km) // l'historique garde le dernier rayon choisi pour cette ville
        }
    }

    fun clearSearch() { search.value = SearchState() }

    /** Recentre la caméra sur la zone en cours (après un déplacement manuel). */
    fun recenter() {
        search.value.area?.let { search.value = search.value.copy(area = it.copy(request = ++requests)) }
    }

    fun setFilter(f: EventFilter) { viewModelScope.launch { repository.setFilter(f) } }

    fun toggleFavorite(item: PlaceEvent) {
        viewModelScope.launch { repository.setFavorite(item.event, !item.favorite) }
    }
}

/**
 * « Aucune commune trouvée » : affiché dès qu'une VRAIE recherche (2 lettres ou plus) s'est
 * terminée sans résultat, et tant que le texte fait au moins 2 lettres. Pendant la frappe (ajout
 * ou retour arrière), il reste en place jusqu'au résultat suivant : remplacé directement par les
 * villes trouvées, ou inchangé s'il n'y en a toujours pas, donc jamais de clignotement. Une seule
 * lettre ne lance pas de recherche : elle ne compte pas (pas de message parasite à la 2e lettre).
 */
internal fun noCityFor(query: String, searched: String?, suggestions: List<City>): Boolean {
    if (searched == null || suggestions.isNotEmpty()) return false
    return searched.trim().length >= 2 && query.trim().length >= 2
}

/**
 * Un événement récurrent au même endroit (même source, titre et lieu) n'apparaît qu'une fois :
 * sa prochaine date (ou la plus récente pour le passé), avec le nombre d'autres dates.
 */
internal fun collapseSeries(items: List<PlaceEvent>, newestFirst: Boolean = false): List<PlaceEvent> =
    items.groupBy { Triple(it.event.sourceId, it.event.title, it.event.location) }.values
        .map { series ->
            val pick = if (newestFirst) series.maxBy { it.event.startsAtEpochMs } else series.minBy { it.event.startsAtEpochMs }
            pick.copy(otherDates = series.size - 1, favorite = series.any { it.favorite })
        }
        .let { list -> if (newestFirst) list.sortedByDescending { it.event.startsAtEpochMs } else list.sortedBy { it.event.startsAtEpochMs } }
