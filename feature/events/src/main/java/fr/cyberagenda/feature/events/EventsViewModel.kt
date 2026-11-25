package fr.cyberagenda.feature.events

import androidx.lifecycle.ViewModel
import fr.cyberagenda.core.data.SearchHistory
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.cyberagenda.core.data.EventRepository
import fr.cyberagenda.core.model.BeginnerFilter
import fr.cyberagenda.core.model.Event
import fr.cyberagenda.core.model.EventFilter
import fr.cyberagenda.core.model.EventKind
import java.time.LocalDate
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EventItem(val event: Event, val sourceName: String, val favorite: Boolean, val otherDates: Int = 0)

data class EventsUiState(
    /** Lignes affichées (dans l'horizon), CTF en ligne regroupés par semaine. */
    val rows: List<AgendaRow> = emptyList(),
    /** Nombre d'événements distincts (toutes dates confondues). */
    val total: Int = 0,
    /** Événements au-delà de l'horizon, masqués pour alléger la liste. */
    val beyondHorizon: Int = 0,
    val horizonMonths: Int = DEFAULT_HORIZON_MONTHS,
    /** Recherche texte : propre à l'agenda, non conservée. */
    val query: String = "",
    /** Filtre commun à toutes les vues, conservé. */
    val filter: EventFilter = EventFilter(),
    val refreshing: Boolean = false,
    val loaded: Boolean = false,
    val showBeginnerTip: Boolean = false,
    /** Événements terminés depuis moins de 3 mois (si le filtre « passés » est actif), récents d'abord. */
    val past: List<EventItem> = emptyList(),
    val pastExpanded: Boolean = false,
    /** Recherches validées récemment (les plus récentes d'abord). */
    val history: List<String> = emptyList(),
) {
    /** Recherche ou filtre qui réduit la liste (inclure les passés n'en fait pas partie). */
    val narrowed: Boolean get() = query.isNotBlank() || !filter.copy(includePast = false).isDefault
}

private data class Controls(val query: String, val refreshing: Boolean, val tipDismissed: Boolean, val filter: EventFilter)
private data class View(val horizonMonths: Int, val expandedWeeks: Set<LocalDate>, val pastExpanded: Boolean)

const val DEFAULT_HORIZON_MONTHS = 3

@HiltViewModel
class EventsViewModel @Inject constructor(
    private val repository: EventRepository,
    private val searchHistory: SearchHistory,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val refreshing = MutableStateFlow(false)
    private val horizon = MutableStateFlow(DEFAULT_HORIZON_MONTHS)
    private val expandedWeeks = MutableStateFlow<Set<LocalDate>>(emptySet())
    private val pastExpanded = MutableStateFlow(false)
    private val view = combine(horizon, expandedWeeks, pastExpanded, ::View)

    private val controls = combine(query, refreshing, repository.beginnerTipDismissed, repository.filter, ::Controls)

    val state: StateFlow<EventsUiState> = combine(
        repository.observeFiltered(),
        repository.observeFavoriteIds(),
        controls,
        view,
        repository.observeRecentPast(),
    ) { events, favorites, c, v, recentPast ->
        // Un événement récurrent (même source, titre et lieu) n'apparaît qu'une fois :
        // sa prochaine occurrence, avec le nombre d'autres dates.
        val items = events
            .filter { matchesQuery(it, c.query) }
            .groupBy { Triple(it.sourceId, it.title, it.location) }
            .values
            .map { series ->
                val next = series.minBy { it.startsAtEpochMs }
                EventItem(next, repository.sourceLabel(next), series.any { e -> e.allIds.any { it in favorites } }, series.size - 1)
            }
            .sortedBy { it.event.startsAtEpochMs }
        // Horizon : on n'affiche que les prochains mois, le reste est à un toucher.
        val limit = ZonedDateTime.now().plusMonths(v.horizonMonths.toLong()).toInstant().toEpochMilli()
        val (soon, later) = items.partition { it.event.startsAtEpochMs < limit }
        // Quand on cherche explicitement (texte ou filtre « CTF »), on montre tout sans regrouper.
        val group = c.query.isBlank() && c.filter.kinds != setOf(EventKind.CTF)
        EventsUiState(
            rows = buildAgendaRows(soon, group, v.expandedWeeks),
            total = items.size, beyondHorizon = later.size, horizonMonths = v.horizonMonths,
            query = c.query, filter = c.filter, refreshing = c.refreshing, loaded = true, showBeginnerTip = !c.tipDismissed,
            past = if (!c.filter.includePast) emptyList() else recentPast
                .filter { matchesQuery(it, c.query) }
                .groupBy { Triple(it.sourceId, it.title, it.location) }.values
                .map { s -> val last = s.maxBy { it.startsAtEpochMs }
                    EventItem(last, repository.sourceLabel(last), s.any { e -> e.allIds.any { it in favorites } }, s.size - 1) }
                .sortedByDescending { it.event.startsAtEpochMs },
            pastExpanded = v.pastExpanded,
        )
    }.combine(searchHistory.queries) { s, history -> s.copy(history = history) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EventsUiState())

    init {
        refresh(force = false)
    }

    fun refresh(force: Boolean = true) {
        if (refreshing.value) return
        viewModelScope.launch {
            refreshing.value = true
            try {
                repository.refresh(force)
            } finally {
                refreshing.value = false
            }
        }
    }

    fun togglePast() { pastExpanded.value = !pastExpanded.value }

    fun showFurther() { horizon.value += DEFAULT_HORIZON_MONTHS }

    fun toggleWeek(week: LocalDate) {
        expandedWeeks.value = expandedWeeks.value.let { if (week in it) it - week else it + week }
    }

    fun setQuery(q: String) { query.value = q.take(100) }

    /** Recherche validée (touche Rechercher, ou fiche ouverte depuis les résultats) : mémorisée. */
    fun commitQuery() {
        val q = query.value
        if (q.isNotBlank()) viewModelScope.launch { searchHistory.addQuery(q) }
    }

    /** Recherche reprise depuis l'historique : appliquée et remontée en tête. */
    fun useHistory(q: String) { setQuery(q); commitQuery() }

    fun removeHistory(q: String) { viewModelScope.launch { searchHistory.removeQuery(q) } }

    fun setFilter(f: EventFilter) { viewModelScope.launch { repository.setFilter(f) } }

    fun dismissBeginnerTip(enableFilter: Boolean) {
        viewModelScope.launch {
            if (enableFilter) repository.setFilter(repository.filter.first().copy(beginner = BeginnerFilter.ONLY))
            repository.dismissBeginnerTip()
        }
    }

    fun toggleFavorite(item: EventItem) {
        viewModelScope.launch { repository.setFavorite(item.event, !item.favorite) }
    }

    private fun matchesQuery(e: Event, q: String): Boolean {
        val t = q.trim()
        if (t.isEmpty()) return true
        return listOfNotNull(e.title, e.organizer, e.location, e.description).any { it.contains(t, ignoreCase = true) }
    }
}
