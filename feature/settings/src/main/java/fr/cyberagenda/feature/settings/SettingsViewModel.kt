package fr.cyberagenda.feature.settings

import androidx.lifecycle.ViewModel
import fr.cyberagenda.core.data.SearchHistory
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.cyberagenda.core.data.AppSettings
import fr.cyberagenda.core.data.EventRepository
import fr.cyberagenda.core.model.Event
import fr.cyberagenda.core.model.ReminderMode
import kotlinx.coroutines.flow.first
import fr.cyberagenda.core.model.SourceState
import fr.cyberagenda.core.model.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val reminder: ReminderMode = ReminderMode.OFF,
    val sources: List<SourceState> = emptyList(),
    val refreshing: Boolean = false,
    /** Entrées des historiques de recherche (agenda + carte). */
    val historyCount: Int = 0,
    /** Thème secret « Terminal » débloqué. */
    val terminalUnlocked: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: EventRepository,
    private val settings: AppSettings,
    private val searchHistory: SearchHistory,
) : ViewModel() {
    private val refreshing = MutableStateFlow(false)

    val state: StateFlow<SettingsUiState> = combine(
        settings.theme, settings.reminder, repository.observeSources(), refreshing, ::SettingsUiState,
    ).combine(combine(searchHistory.queries, searchHistory.places) { q, p -> q.size + p.size }) { s, n -> s.copy(historyCount = n) }
        .combine(settings.terminalUnlocked) { s, unlocked -> s.copy(terminalUnlocked = unlocked) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun clearSearchHistory() { viewModelScope.launch { searchHistory.clear() } }

    /** Favoris à venir, pour l'export .ics. */
    suspend fun upcomingFavorites(): List<Event> = repository.observeUpcomingFavorites().first()

    fun setTheme(mode: ThemeMode) { viewModelScope.launch { settings.setTheme(mode) } }
    fun unlockTerminal() { viewModelScope.launch { settings.unlockTerminal() } }
    fun setReminder(mode: ReminderMode) { viewModelScope.launch { settings.setReminder(mode) } }

    fun setSourceEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch {
            repository.setSourceEnabled(id, enabled)
            if (enabled) repository.refresh(force = true)
        }
    }

    fun refreshAll() {
        if (refreshing.value) return
        viewModelScope.launch {
            refreshing.value = true
            try { repository.refresh(force = true) } finally { refreshing.value = false }
        }
    }
}
