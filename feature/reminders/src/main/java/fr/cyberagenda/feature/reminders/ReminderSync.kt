package fr.cyberagenda.feature.reminders

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.cyberagenda.core.data.AppSettings
import fr.cyberagenda.core.data.EventRepository
import fr.cyberagenda.core.model.Event
import fr.cyberagenda.core.model.ReminderMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Maintient les rappels planifiés en phase avec les favoris et le réglage choisi.
 * Approche par réconciliation : à chaque changement, on annule tout et on replanifie,
 * ce qui gère aussi les changements d'horaire remontés par les sources.
 * WorkManager (rappel non exact, à quelques minutes près) évite la permission sensible
 * SCHEDULE_EXACT_ALARM et survit aux redémarrages du téléphone.
 */
@Singleton
class ReminderSync @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: EventRepository,
    private val settings: AppSettings,
) {
    @OptIn(kotlinx.coroutines.FlowPreview::class)
    fun start(scope: CoroutineScope) {
        scope.launch {
            combine(repository.observeUpcomingFavorites(), settings.reminder) { favorites, mode ->
                favorites.map { Triple(it.id, it.startsAtEpochMs, it.title) } to (favorites to mode)
            }
                .distinctUntilChanged { a, b -> a.first == b.first && a.second.second == b.second.second }
                .debounce(500)
                .collectLatest { (_, pair) -> reconcile(pair.first, pair.second) }
        }
    }

    private fun reconcile(favorites: List<Event>, mode: ReminderMode) {
        val wm = WorkManager.getInstance(context)
        wm.cancelAllWorkByTag(TAG)
        // Les rappels annulés ou passés (titre, lieu) ne restent pas dans la base de WorkManager.
        wm.pruneWork()
        if (mode == ReminderMode.OFF) return
        val now = System.currentTimeMillis()
        favorites.take(MAX_REMINDERS).forEach { e ->
            val at = e.startsAtEpochMs - mode.beforeMs
            if (at <= now + 60_000) return@forEach
            val request = OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInitialDelay(at - now, TimeUnit.MILLISECONDS)
                .addTag(TAG)
                .setInputData(
                    workDataOf(
                        ReminderWorker.KEY_ID to e.id,
                        ReminderWorker.KEY_TITLE to e.title.take(150),
                        ReminderWorker.KEY_START to e.startsAtEpochMs,
                        ReminderWorker.KEY_PLACE to (e.location ?: "En ligne").take(120),
                    )
                )
                .build()
            wm.enqueueUniqueWork("reminder-${e.id}", ExistingWorkPolicy.REPLACE, request)
        }
    }

    private companion object {
        const val TAG = "event-reminder"
        const val MAX_REMINDERS = 50
    }
}
