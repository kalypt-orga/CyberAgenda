package fr.cyberagenda

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.view.drawToBitmap
import fr.cyberagenda.core.ui.LocalThemeReveal
import fr.cyberagenda.core.ui.ThemeReveal
import kotlinx.coroutines.delay
import android.content.Intent
import fr.cyberagenda.core.ui.SecretStyle
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import dagger.hilt.android.AndroidEntryPoint
import fr.cyberagenda.core.data.AppSettings
import fr.cyberagenda.core.data.EventRepository
import kotlinx.coroutines.launch
import fr.cyberagenda.core.model.ThemeMode
import fr.cyberagenda.core.ui.CyberAgendaTheme
import fr.cyberagenda.feature.reminders.ReminderWorker
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var settings: AppSettings
    @Inject lateinit var repository: EventRepository

    /** Événement à ouvrir (depuis une notification de rappel). */
    private var pendingEventId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        // Le thème enregistré est lu avant le premier affichage : l'écran de démarrage reste visible
        // le temps de cette lecture (quelques ms), ce qui évite un flash du mauvais thème et un
        // double chargement de la carte.
        val theme = settings.theme.stateIn(lifecycleScope, SharingStarted.Eagerly, null)
        val tourDone = settings.tourDone.stateIn(lifecycleScope, SharingStarted.Eagerly, null)
        splash.setKeepOnScreenCondition { theme.value == null || tourDone.value == null }
        // Sortie de l'écran de démarrage (fond uni, sans logo) : simple fondu vers l'interface.
        splash.setOnExitAnimationListener { provider ->
            provider.view.animate().alpha(0f).setDuration(SPLASH_FADE_MS)
                .withEndAction { provider.remove() }.start()
        }
        enableEdgeToEdge()
        // Protection contre le tapjacking : on ignore les touches quand une autre
        // fenêtre (overlay) recouvre l'application.
        window.decorView.filterTouchesWhenObscured = true
        if (savedInstanceState == null) pendingEventId = eventIdFrom(intent)

        setContent {
            val mode by theme.collectAsStateWithLifecycle()
            val tourSeen by tourDone.collectAsStateWithLifecycle()
            val dark = when (mode ?: ThemeMode.SYSTEM) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                // Thèmes secrets : clairs ou sombres selon leur concept (barre d'état, fond de carte).
                ThemeMode.ARCADE, ThemeMode.BLUEPRINT -> true
                ThemeMode.BRUTAL, ThemeMode.NOTEBOOK, ThemeMode.GAZETTE, ThemeMode.ITASHA -> false
            }
            // Icônes de la barre d'état lisibles quel que soit le thème choisi.
            LaunchedEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            val secret = when (mode) {
                ThemeMode.BRUTAL -> SecretStyle.BRUTAL
                ThemeMode.NOTEBOOK -> SecretStyle.NOTEBOOK
                ThemeMode.ARCADE -> SecretStyle.ARCADE
                ThemeMode.BLUEPRINT -> SecretStyle.BLUEPRINT
                ThemeMode.GAZETTE -> SecretStyle.GAZETTE
                ThemeMode.ITASHA -> SecretStyle.ITASHA
                else -> null
            }
            // Révélation du thème débloqué (voir ThemeRevealOverlay) : l'écran actuel est figé en
            // image juste avant le changement de thème. Une seule fois, au déblocage.
            var frozen by remember { mutableStateOf<ImageBitmap?>(null) }
            var origin by remember { mutableStateOf(Offset.Zero) }
            var ready by remember { mutableStateOf(false) }
            val reveal = remember {
                ThemeReveal { at ->
                    frozen = runCatching { window.decorView.drawToBitmap().asImageBitmap() }.getOrNull()
                    origin = at
                    ready = false
                }
            }
            // On attend que le nouveau thème soit appliqué (ou 1 s au plus) avant d'ouvrir.
            LaunchedEffect(frozen, secret) {
                if (frozen == null) return@LaunchedEffect
                if (secret == null) delay(1_000)
                delay(REVEAL_SETTLE_MS)
                ready = true
            }
            CyberAgendaTheme(darkTheme = dark, secret = secret) {
                CompositionLocalProvider(LocalThemeReveal provides reveal) {
                    AppNavHost(
                        versionName = BuildConfig.VERSION_NAME,
                        openEventId = pendingEventId,
                        onEventOpened = { pendingEventId = null },
                        showTour = tourSeen == false,
                        onTourDone = { lifecycleScope.launch { settings.setTourDone() } },
                    )
                }
                frozen?.let { ThemeRevealOverlay(it, origin, ready, onDone = { frozen = null; ready = false }) }
            }
        }
    }

    /** Retour au premier plan : listes recalculées et synchronisation si la dernière date de plus de 6 h. */
    override fun onStart() {
        super.onStart()
        repository.tickClock()
        lifecycleScope.launch { runCatching { repository.refresh(force = false) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        eventIdFrom(intent)?.let { pendingEventId = it }
    }

    /**
     * L'activité est exportée (lanceur) : n'importe quelle app peut lui envoyer cet extra.
     * On n'accepte qu'un identifiant au format attendu ; au pire, cela ouvre une fiche existante.
     */
    private fun eventIdFrom(intent: Intent?): String? =
        // runCatching : un extra non désérialisable (classe inconnue, Android < 13) ne doit pas
        // faire planter l'app au lancement.
        runCatching { intent?.getStringExtra(ReminderWorker.EXTRA_EVENT_ID) }.getOrNull()
            ?.takeIf { EVENT_ID.matches(it) }

    private companion object {
        val EVENT_ID = Regex("[a-z0-9-]{2,32}:[A-Za-z0-9._-]{1,120}")
        const val SPLASH_FADE_MS = 220L
        const val REVEAL_SETTLE_MS = 60L
    }
}
