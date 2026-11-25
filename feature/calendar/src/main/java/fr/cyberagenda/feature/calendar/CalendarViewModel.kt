package fr.cyberagenda.feature.calendar

import androidx.lifecycle.ViewModel
import fr.cyberagenda.core.ui.Formatters
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.cyberagenda.core.data.EventRepository
import fr.cyberagenda.core.model.Event
import fr.cyberagenda.core.model.EventFilter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

data class DayEvent(val event: Event, val sourceName: String, val favorite: Boolean)

data class CalendarUiState(
    val month: YearMonth = YearMonth.now(),
    val selected: LocalDate = LocalDate.now(),
    /** Événements par jour (un événement sur plusieurs jours apparaît chaque jour). */
    val byDay: Map<LocalDate, List<DayEvent>> = emptyMap(),
    /** Événements sur plusieurs jours du mois, dessinés comme des barres. */
    val spans: List<Span> = emptyList(),
    val filter: EventFilter = EventFilter(),
) {
    /** Premier mois consultable : 3 mois en arrière si le filtre « passés » est actif. */
    val minMonth: YearMonth get() = if (filter.includePast) YearMonth.now().minusMonths(3) else YearMonth.now()
}

data class Span(val event: Event, val start: LocalDate, val end: LocalDate)

@HiltViewModel
class CalendarViewModel @Inject constructor(private val repository: EventRepository) : ViewModel() {
    private val month = MutableStateFlow(YearMonth.now())
    private val selected = MutableStateFlow(LocalDate.now())

    private val withPast = combine(repository.observeFiltered(), repository.observeRecentPast(), repository.filter) { up, past, f ->
        if (f.includePast) past + up else up
    }

    val state: StateFlow<CalendarUiState> = combine(
        withPast, repository.observeFavoriteIds(), month, selected, repository.filter,
    ) { events, favorites, m, sel, f ->
        CalendarUiState(m, sel, spread(events, favorites, m), spans(events, m), f)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarUiState())

    fun showMonth(delta: Long) {
        val next = month.value.plusMonths(delta)
        if (next < state.value.minMonth) return
        month.value = next
        selected.value = if (next == YearMonth.now()) LocalDate.now() else next.atDay(1)
    }

    fun select(day: LocalDate) { selected.value = day }

    /** Saut direct vers un mois (sélecteur mois/année), borné aux mois consultables. */
    fun goTo(target: YearMonth) {
        if (target < state.value.minMonth || target > maxMonth) return
        month.value = target
        selected.value = if (target == YearMonth.now()) LocalDate.now() else target.atDay(1)
    }

    companion object {
        /** Les sources ne publient rien au-delà de 5 ans (borne du validateur d'événements). */
        val maxMonth: YearMonth get() = YearMonth.now().plusYears(5)
    }

    fun setFilter(f: EventFilter) { viewModelScope.launch { repository.setFilter(f) } }

    fun toggleFavorite(item: DayEvent) {
        viewModelScope.launch { repository.setFavorite(item.event, !item.favorite) }
    }

    private fun spans(events: List<Event>, m: YearMonth): List<Span> {
        val zone = ZoneId.systemDefault()
        return events.mapNotNull { e ->
            val s = Instant.ofEpochMilli(e.startsAtEpochMs).atZone(zone).toLocalDate()
            val end = Instant.ofEpochMilli(Formatters.displayEnd(e.startsAtEpochMs, e.endsAtEpochMs)).atZone(zone).toLocalDate()
            if (s == end || end < m.atDay(1) || s > m.atEndOfMonth()) null else Span(e, s, end)
        }.sortedWith(compareBy({ it.start }, { -it.end.toEpochDay() }))
    }

    private fun spread(events: List<Event>, favorites: Set<String>, m: YearMonth): Map<LocalDate, List<DayEvent>> {
        val zone = ZoneId.systemDefault()
        val first = m.atDay(1)
        val last = m.atEndOfMonth()
        val out = HashMap<LocalDate, MutableList<DayEvent>>()
        for (e in events) {
            val start = Instant.ofEpochMilli(e.startsAtEpochMs).atZone(zone).toLocalDate()
            val end = Instant.ofEpochMilli(Formatters.displayEnd(e.startsAtEpochMs, e.endsAtEpochMs)).atZone(zone).toLocalDate()
            if (end < first || start > last) continue
            var d = maxOf(start, first)
            val stop = minOf(end, last)
            val item = DayEvent(e, repository.sourceLabel(e), e.allIds.any { it in favorites })
            while (d <= stop) {
                out.getOrPut(d) { mutableListOf() } += item
                d = d.plusDays(1)
            }
        }
        return out
    }
}
