package fr.cyberagenda

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import fr.cyberagenda.core.ui.themeBackground
import androidx.compose.runtime.SideEffect
import fr.cyberagenda.core.ui.tourTarget
import fr.cyberagenda.core.ui.TourTargets
import fr.cyberagenda.core.ui.TourOverlay
import fr.cyberagenda.core.ui.Tour
import fr.cyberagenda.core.ui.LocalTourTargets
import kotlinx.coroutines.delay
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import fr.cyberagenda.core.ui.AgendaTheme
import fr.cyberagenda.core.ui.Mono
import fr.cyberagenda.feature.calendar.CalendarRoute
import fr.cyberagenda.feature.events.EventDetailRoute
import fr.cyberagenda.feature.events.EventDetailViewModel
import fr.cyberagenda.feature.events.EventsRoute
import fr.cyberagenda.feature.map.MapRoute
import fr.cyberagenda.feature.settings.AboutRoute
import fr.cyberagenda.feature.settings.GlossaryRoute
import fr.cyberagenda.feature.settings.ProposeEventRoute
import fr.cyberagenda.feature.settings.SettingsRoute

private object Routes {
    const val EVENTS = "events"
    const val CALENDAR = "calendar"
    const val MAP = "map"
    const val SETTINGS = "settings"
    const val PROPOSE = "propose"
    const val GLOSSARY = "glossary"
    const val ABOUT = "about"
    const val DETAIL = "event/{${EventDetailViewModel.ARG_EVENT_ID}}"
    fun detail(id: String) = "event/${Uri.encode(id)}"
}

/** Onglets principaux. Pour un nouveau module de fonctionnalité : ajouter une entrée ici. */
private enum class TopLevel(val route: String, val label: String, val icon: ImageVector, val tourKey: String?) {
    EVENTS(Routes.EVENTS, "Agenda", Icons.AutoMirrored.Filled.List, null),
    CALENDAR(Routes.CALENDAR, "Calendrier", Icons.Default.DateRange, Tour.TAB_CALENDAR),
    MAP(Routes.MAP, "Carte", Icons.Default.Place, Tour.TAB_MAP),
    SETTINGS(Routes.SETTINGS, "Paramètres", Icons.Default.Settings, Tour.TAB_SETTINGS),
}

@Composable
fun AppNavHost(
    versionName: String,
    openEventId: String?,
    onEventOpened: () -> Unit,
    showTour: Boolean,
    onTourDone: () -> Unit,
    nav: NavHostController = rememberNavController(),
) {
    val tourTargets = remember { TourTargets() }
    var tourStep by rememberSaveable { mutableStateOf<Int?>(null) }
    CompositionLocalProvider(LocalTourTargets provides tourTargets) {
        Box(Modifier.fillMaxSize()) {
            AppScaffold(versionName, openEventId, onEventOpened, nav, onReplayTour = {
                nav.navigateTopLevel(Routes.EVENTS)
                tourStep = 0
            })
            TourOverlay(
                targets = tourTargets,
                steps = Tour.steps,
                step = tourStep,
                onStep = { tourStep = it },
                onFinish = { tourStep = null; onTourDone() },
            )
        }
    }
    // Premier lancement : la visite démarre sur l'agenda, une fois l'écran en place
    // (jamais par-dessus une fiche ouverte depuis une notification).
    val route = nav.currentBackStackEntryAsState().value?.destination?.route
    LaunchedEffect(showTour, route) {
        if (showTour && tourStep == null && route == Routes.EVENTS && openEventId == null) {
            delay(TOUR_DELAY_MS)
            tourStep = 0
        }
    }
}

@Composable
private fun AppScaffold(
    versionName: String,
    openEventId: String?,
    onEventOpened: () -> Unit,
    nav: NavHostController,
    onReplayTour: () -> Unit,
) {
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBar = TopLevel.entries.any { it.route == currentRoute }
    val openDetail: (String) -> Unit = { nav.navigate(Routes.detail(it)) }
    val openPropose: () -> Unit = { nav.navigate(Routes.PROPOSE) { launchSingleTop = true } }

    // Ouverture depuis une notification de rappel (ou un intent extérieur) : la fiche remplace
    // toute fiche ouverte de cette façon, au-dessus de l'onglet de départ. Des intents répétés
    // ne peuvent donc pas empiler des dizaines d'écrans : un seul « Retour » ramène à l'app.
    LaunchedEffect(openEventId) {
        openEventId?.let { id ->
            nav.navigate(Routes.detail(id)) {
                popUpTo(nav.graph.findStartDestination().id)
                launchSingleTop = true
            }
            onEventOpened()
        }
    }

    Scaffold(
        // Fond transparent : la texture du thème (papier quadrillé, grille, sakura…) est dessinée dessous.
        containerColor = Color.Transparent,
        // Couleur du texte explicite : avec un fond transparent, elle retomberait sur le noir
        // (textes invisibles en thème sombre).
        contentColor = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.background(MaterialTheme.colorScheme.background).themeBackground(),
        bottomBar = {
            if (showBar) {
                Column {
                    HorizontalDivider(color = AgendaTheme.colors.hairline)
                    NavigationBar(containerColor = MaterialTheme.colorScheme.background, tonalElevation = 0.dp) {
                        TopLevel.entries.forEach { tab ->
                            NavigationBarItem(
                                modifier = tab.tourKey?.let { Modifier.tourTarget(it) } ?: Modifier,
                                selected = currentRoute == tab.route,
                                onClick = { nav.navigateTopLevel(tab.route) },
                                icon = { Icon(tab.icon, contentDescription = null) },
                                label = { Text(tab.label, style = Mono.tag) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = AgendaTheme.colors.accentSoft,
                                    unselectedIconColor = AgendaTheme.colors.muted,
                                    unselectedTextColor = AgendaTheme.colors.muted,
                                ),
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        // Hauteur réservée à la barre du bas, CONSTANTE pour les 4 onglets : quand la barre se
        // masque (fiche, lexique…), l'onglet quitté ne doit pas grandir. Sinon une liste arrêtée
        // tout en bas se recale sur la hauteur agrandie et, au retour, son dernier élément passe
        // sous la barre (ex. Paramètres → Lexique → retour : « Confidentialité » masquée).
        val bottomNow = padding.calculateBottomPadding()
        var barHeight by remember { mutableStateOf(0.dp) }
        if (showBar && barHeight != bottomNow) SideEffect { barHeight = bottomNow }
        val tabBottom = if (showBar) bottomNow else barHeight
        val tab: @Composable (@Composable () -> Unit) -> Unit = { content ->
            Box(Modifier.fillMaxSize().padding(bottom = tabBottom)) { content() }
        }
        // Écrans secondaires (sans barre) : seulement la marge de la barre de navigation système.
        val secondary: @Composable (@Composable () -> Unit) -> Unit = { content ->
            Box(Modifier.fillMaxSize().padding(bottom = bottomNow)) { content() }
        }
        NavHost(
            navController = nav,
            startDestination = Routes.EVENTS,
            // Onglets : léger glissement dans le sens de la barre (gauche/droite) + fondu.
            // Écrans secondaires (fiche, formulaire…) : arrivée par la droite.
            enterTransition = {
                val from = tabIndex(initialState.destination.route)
                val to = tabIndex(targetState.destination.route)
                val dir = if (from >= 0 && to >= 0) (if (to > from) 1 else -1) else 1
                slideInHorizontally(tween(TRANSITION_MS)) { w -> dir * w / 6 } + fadeIn(tween(TRANSITION_MS))
            },
            exitTransition = { fadeOut(tween(TRANSITION_MS / 2)) },
            popEnterTransition = { fadeIn(tween(TRANSITION_MS)) },
            popExitTransition = { slideOutHorizontally(tween(TRANSITION_MS)) { w -> w / 6 } + fadeOut(tween(TRANSITION_MS)) },
            modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()).consumeWindowInsets(padding),
        ) {
            composable(Routes.EVENTS) {
                tab { EventsRoute(onEventClick = openDetail, onOpenSources = { nav.navigateTopLevel(Routes.SETTINGS) }, onPropose = openPropose) }
            }
            composable(Routes.CALENDAR) { tab { CalendarRoute(onEventClick = openDetail) } }
            composable(Routes.MAP) { tab { MapRoute(onEventClick = openDetail) } }
            composable(Routes.SETTINGS) {
                tab {
                    SettingsRoute(
                        onPropose = openPropose,
                        onGlossary = { nav.navigate(Routes.GLOSSARY) },
                        onAbout = { nav.navigate(Routes.ABOUT) },
                        onReplayTour = onReplayTour,
                        versionName = versionName,
                    )
                }
            }
            composable(Routes.GLOSSARY) { secondary { GlossaryRoute(onBack = { nav.popBackStack() }) } }
            composable(Routes.ABOUT) { secondary { AboutRoute(onBack = { nav.popBackStack() }, versionName = versionName) } }
            composable(Routes.PROPOSE) { secondary { ProposeEventRoute(onBack = { nav.popBackStack() }) } }
            composable(
                Routes.DETAIL,
                arguments = listOf(navArgument(EventDetailViewModel.ARG_EVENT_ID) { type = NavType.StringType }),
            ) {
                secondary { EventDetailRoute(onBack = { nav.popBackStack() }) }
            }
        }
    }
}

private const val TRANSITION_MS = 260
private const val TOUR_DELAY_MS = 700L

private fun tabIndex(route: String?) = TopLevel.entries.indexOfFirst { it.route == route }

private fun NavHostController.navigateTopLevel(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}
