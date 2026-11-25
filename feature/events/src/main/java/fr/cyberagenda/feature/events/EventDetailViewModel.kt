package fr.cyberagenda.feature.events

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.cyberagenda.core.data.EventRepository
import fr.cyberagenda.core.model.Event
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EventDetailUiState(
    val loaded: Boolean = false,
    val event: Event? = null,
    val sourceName: String = "",
    val favorite: Boolean = false,
    /** Autres occurrences à venir d'un événement récurrent. */
    val otherDates: List<Event> = emptyList(),
)

@HiltViewModel
class EventDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: EventRepository,
) : ViewModel() {

    private val eventId: String = checkNotNull(savedStateHandle[ARG_EVENT_ID])

    @OptIn(ExperimentalCoroutinesApi::class)
    private val series = repository.observeEvent(eventId).flatMapLatest { e ->
        if (e == null) flowOf(emptyList()) else repository.observeSeries(e).map { list -> list.filter { it.id != e.id } }
    }

    val state: StateFlow<EventDetailUiState> = combine(
        repository.observeEvent(eventId),
        repository.observeIsFavorite(eventId),
        series,
    ) { event, favorite, others ->
        EventDetailUiState(true, event, event?.let { repository.sourceLabel(it) }.orEmpty(), favorite, others)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EventDetailUiState())

    fun toggleFavorite() {
        val current = state.value
        val event = current.event ?: return
        viewModelScope.launch { repository.setFavorite(event, !current.favorite) }
    }

    companion object {
        const val ARG_EVENT_ID = "eventId"
    }
}
