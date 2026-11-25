package fr.cyberagenda

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import fr.cyberagenda.core.geo.FrenchGeocoder
import fr.cyberagenda.feature.reminders.ReminderSync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class CyberAgendaApp : Application() {
    @Inject lateinit var reminderSync: ReminderSync
    @Inject lateinit var geocoder: FrenchGeocoder

    /** Portée liée au processus pour les tâches de fond légères (réconciliation des rappels). */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        reminderSync.start(appScope)
        // Table des communes (≈ 35 000) chargée en arrière-plan dès le démarrage.
        appScope.launch { geocoder.warmUp() }
    }
}
