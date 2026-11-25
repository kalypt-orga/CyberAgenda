package fr.cyberagenda.feature.map

import androidx.compose.animation.fadeOut
import fr.cyberagenda.core.ui.LegendButton
import androidx.compose.ui.input.nestedscroll.nestedScroll
import fr.cyberagenda.core.ui.rememberSheetListGuard
import androidx.compose.foundation.lazy.rememberLazyListState
import fr.cyberagenda.core.ui.overscrollIfScrollable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.zIndex
import fr.cyberagenda.core.ui.releaseSearchOnOutsideTap
import fr.cyberagenda.core.ui.searchZone
import fr.cyberagenda.core.ui.rememberSearchZone
import androidx.compose.ui.focus.onFocusChanged
import fr.cyberagenda.core.ui.RecentSearchList
import fr.cyberagenda.core.model.RecentSearches
import fr.cyberagenda.core.model.RecentPlace
import androidx.compose.foundation.BorderStroke
import fr.cyberagenda.core.ui.FilterObscuredTouches
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.launch
import androidx.core.view.doOnLayout
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.Home
import fr.cyberagenda.core.ui.FilterBar
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.cyberagenda.core.geo.City
import fr.cyberagenda.core.ui.AgendaTheme
import fr.cyberagenda.core.ui.EmptyMessage
import fr.cyberagenda.core.ui.EventCard
import fr.cyberagenda.core.ui.Formatters
import fr.cyberagenda.core.ui.Mono
import fr.cyberagenda.core.ui.SectionHeader
import android.graphics.RectF
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonOptions
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

private const val SOURCE_ID = "events"
private const val LAYER_ID = "events-circles"
private const val AREA_SOURCE = "area"
private const val PAST_SOURCE = "past-events"
private const val CLUSTER_LAYER = "events-clusters"
private const val PAST_CLUSTER_LAYER = "past-clusters"
/** Rayon de regroupement (px) et zoom à partir duquel tout est séparé (niveau rue). */
private const val CLUSTER_RADIUS = 38
private const val CLUSTER_MAX_ZOOM = 14
/** Tolérance d'appui autour d'un point (dp) : ~48 dp de côté, la taille d'une cible tactile. */
private const val TOUCH_SLOP_DP = 24f
private const val PAST_LAYER = "past-circles"
// Fond de carte OpenFreeMap : libre, sans clé d'API ni traceur.
private const val STYLE_DARK = "https://tiles.openfreemap.org/styles/dark"
private const val STYLE_LIGHT = "https://tiles.openfreemap.org/styles/positron"
private val FRANCE = LatLng(46.5, 2.4)
private val RADII = listOf(10, 25, 50, 100)
/** Hauteur du panneau « Autour de… » replié (poignée, titre, rayon) et hauteur max déplié. */
private const val PANEL_PEEK_DP = 112.0
private const val PANEL_MAX_FRACTION = 0.62f
/** Largeur réservée à la colonne d'outils (44 dp + marges). */
private const val TOOLS_WIDTH_DP = 60.0

@OptIn(ExperimentalMaterial3Api::class)
/** Commandes de caméra déclenchées par les outils (l'identifiant permet de répéter la même). */
internal enum class CameraAction { ZOOM_IN, ZOOM_OUT, FRANCE }
internal data class CameraCommand(val action: CameraAction, val id: Long)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapRoute(onEventClick: (String) -> Unit, viewModel: MapViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val density = LocalDensity.current
    // Hauteur couverte en haut de la carte (recherche + filtres), mesurée : la zone visible commence là.
    var mapTop by remember { mutableStateOf(0f) }
    var searchBottom by remember { mutableStateOf(0f) }
    var filterHeight by remember { mutableStateOf(0f) }
    val topOverlayPx = (searchBottom - mapTop).coerceAtLeast(0f) + filterHeight + with(density) { 8.dp.toPx() }
    var command by remember { mutableStateOf<CameraCommand?>(null) }
    var commandId by remember { mutableStateOf(0L) }
    fun run(action: CameraAction) { command = CameraCommand(action, ++commandId) }

    // Toucher la carte ou le panneau (hors recherche), ou fermer le clavier : le champ se désactive.
    val searchZone = rememberSearchZone()
    // Pendant la saisie d'une ville, les boutons de la carte sont masqués : ils ne servent pas, et
    // la liste des suggestions (ou le clavier, qui réduit l'écran) les ferait se chevaucher.
    var searchActive by remember { mutableStateOf(false) }
    BoxWithConstraints(
        Modifier.fillMaxSize().onGloballyPositioned { mapTop = it.boundsInRoot().top }.releaseSearchOnOutsideTap(searchZone)
    ) {
        val panelMax = maxHeight * PANEL_MAX_FRACTION
        EventsMap(
            places = state.places, pastPlaces = state.pastPlaces, area = state.area, topOverlayPx = topOverlayPx,
            command = command, onPlaceClick = viewModel::select,
            savedCamera = viewModel.camera, onCameraIdle = { viewModel.camera = it },
            handledAreaRequest = { viewModel.handledAreaRequest }, onAreaHandled = { viewModel.handledAreaRequest = it },
        )

        // Au-dessus des boutons de la carte : la liste des suggestions ne doit pas passer dessous.
        Column(Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(12.dp).zIndex(1f)) {
            CitySearch(
                state.query, state.suggestions, state.area != null, viewModel::onQueryChange, viewModel::chooseCity, viewModel::clearSearch,
                noCityFound = state.noCityFound,
                recent = state.recentPlaces, onChooseRecent = viewModel::chooseRecent, onRemoveRecent = viewModel::removeRecent,
                onFieldBottom = { searchBottom = it },
                onFocusChange = { searchActive = it },
                modifier = Modifier.searchZone(searchZone),
            )
            Surface(
                modifier = Modifier.padding(top = 8.dp).fillMaxWidth().onGloballyPositioned { filterHeight = it.size.height.toFloat() },
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.95f),
                border = BorderStroke(1.dp, AgendaTheme.colors.hairline),
            ) {
                Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                    FilterBar(state.filter, viewModel::setFilter)
                    // Légende des points, avec les mêmes couleurs que sur la carte.
                    // Combien sur place (sur la carte) et combien en ligne (absents de la carte, dans l'agenda) :
                    // explique pourquoi la carte montre parfois moins que prévu.
                    Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        LegendDot(MaterialTheme.colorScheme.primary)
                        Text(
                            " ${state.onsiteCount} sur place" + if (state.onlineCount > 0) "  ·  +${state.onlineCount} en ligne (dans l'agenda)" else "",
                            style = Mono.meta, color = AgendaTheme.colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    // Événements passés : ligne à part, seulement si l'option est active dans les filtres.
                    if (state.showPast) {
                        Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            LegendDot(AgendaTheme.colors.muted)
                            Text(" ${state.pastPlaces.sumOf { it.events.size }} terminés ces 3 derniers mois", style = Mono.meta, color = AgendaTheme.colors.muted)
                        }
                    }
                }
            }
        }

        // Outils de navigation, à droite sous la barre du haut (masqués pendant la saisie d'une ville).
        AnimatedVisibility(
            visible = !searchActive,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopEnd)
                .padding(top = with(density) { topOverlayPx.toDp() } + 20.dp, end = 12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ToolButton("Zoomer", { run(CameraAction.ZOOM_IN) }) { Text("+", style = Mono.brand) }
                ToolButton("Dézoomer", { run(CameraAction.ZOOM_OUT) }) { Text("−", style = Mono.brand) }
                ToolButton("Voir toute la France", { viewModel.clearSearch(); run(CameraAction.FRANCE) }) {
                    Icon(Icons.Default.Home, contentDescription = null)
                }
                if (state.area != null) {
                    ToolButton("Recentrer sur ${state.area?.city?.name}", viewModel::recenter) {
                        Icon(painterResource(R.drawable.ic_map_recenter), contentDescription = null)
                    }
                }
            }
        }

        state.area?.let { area ->
            NearbyPanel(
                area = area, items = state.nearby,
                onRadius = viewModel::setRadius,
                onEventClick = onEventClick,
                onToggleFavorite = viewModel::toggleFavorite,
                maxHeight = panelMax,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }

    val place = state.selected
    if (place != null) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.select(null) },
            // Ouverte directement à sa taille : pas de passage « à moitié » qui saute au défilement.
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.background,
        ) {
            FilterObscuredTouches()
            // Titre fixe, hors de la liste.
            SectionHeader(
                (place.label.ifBlank { "Ce lieu" }) + if (place.past) " · terminés" else "",
                count = place.events.size,
            )
            // Étirement natif en bout de liste ; l'élan des lancers ne part pas dans la fenêtre
            // (un geste vers le bas depuis le haut de la liste la ferme d'un trait).
            val guard = rememberSheetListGuard()
            val list = rememberLazyListState()
            LazyColumn(
                state = list,
                contentPadding = PaddingValues(bottom = 24.dp),
                overscrollEffect = overscrollIfScrollable(list, guard.overscroll),
                modifier = Modifier.nestedScroll(guard),
            ) {
                items(place.events, key = { it.event.id }) { item ->
                    EventCard(
                        event = item.event, sourceName = item.sourceName, favorite = item.favorite,
                        onClick = { viewModel.select(null); onEventClick(item.event.id) },
                        onToggleFavorite = { viewModel.toggleFavorite(item) },
                        otherDates = item.otherDates,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun LegendDot(color: androidx.compose.ui.graphics.Color) {
    Box(Modifier.size(9.dp).clip(CircleShape).background(color))
}


@Composable
private fun ToolButton(description: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.95f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, AgendaTheme.colors.hairline),
        shadowElevation = 2.dp,
        modifier = Modifier.size(44.dp).semantics { contentDescription = description },
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

@Composable
private fun CitySearch(
    query: String,
    suggestions: List<City>,
    active: Boolean,
    onQuery: (String) -> Unit,
    onChoose: (City) -> Unit,
    onClear: () -> Unit,
    noCityFound: Boolean,
    recent: List<RecentPlace>,
    onChooseRecent: (RecentPlace) -> Unit,
    onRemoveRecent: (RecentPlace) -> Unit,
    onFieldBottom: (Float) -> Unit,
    onFocusChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focus = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, AgendaTheme.colors.hairline),
        shadowElevation = 2.dp,
    ) {
        Column {
            OutlinedTextField(
                value = query,
                onValueChange = onQuery,
                placeholder = { Text("Chercher une ville…") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    // Champ vide : la légende (ⓘ, comme sur l'agenda et le calendrier) ; sinon la croix.
                    if (query.isNotEmpty() || active) {
                        IconButton(onClick = { onClear(); focus.clearFocus() }) {
                            Icon(Icons.Default.Clear, contentDescription = "Effacer la recherche")
                        }
                    } else {
                        LegendButton()
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    suggestions.firstOrNull()?.let { onChoose(it); focus.clearFocus() }
                }),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                    focusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                ),
                // On mesure le champ seul (pas la liste de suggestions, qui apparaît et disparaît).
                modifier = Modifier.fillMaxWidth()
                    .onGloballyPositioned { onFieldBottom(it.boundsInRoot().bottom) }
                    .onFocusChanged { focused = it.isFocused; onFocusChange(it.isFocused) },
            )
            // Champ vide : les 3 dernières zones (« (35) Rennes · 25 km »), un toucher les relance.
            val recentShown = if (focused && query.isBlank() && !active) recent.take(RecentSearches.SHOWN) else emptyList()
            if (recentShown.isNotEmpty()) HorizontalDivider(color = AgendaTheme.colors.hairline)
            RecentSearchList(
                entries = recentShown.map { it to "(${it.department}) ${it.name} · ${it.radiusKm} km" },
                onPick = { onChooseRecent(it); focus.clearFocus() },
                onRemove = onRemoveRecent,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
            // Rien trouvé (une fois la recherche terminée) : on le dit, plutôt qu'une liste vide.
            if (noCityFound && focused) {
                HorizontalDivider(color = AgendaTheme.colors.hairline)
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text("Aucune commune trouvée", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Vérifiez l'orthographe : toutes les communes de France (outre-mer compris) et Monaco sont proposées.",
                        style = MaterialTheme.typography.bodySmall, color = AgendaTheme.colors.muted,
                    )
                }
            }
            // Suggestions seulement pendant la saisie : champ désactivé = liste refermée.
            if (suggestions.isNotEmpty() && !active && focused) {
                HorizontalDivider(color = AgendaTheme.colors.hairline)
                suggestions.forEach { city ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onChoose(city); focus.clearFocus() }.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(city.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Text(city.department, style = Mono.meta, color = AgendaTheme.colors.muted)
                    }
                }
            }
        }
    }
}

/**
 * Panneau « Autour de… » : replié par défaut (titre et rayon) pour laisser la carte visible.
 * On le déplie en touchant l'en-tête ou en le faisant glisser ; il suit le doigt puis se cale.
 */
@Composable
private fun NearbyPanel(
    area: Area,
    items: List<PlaceEvent>,
    onRadius: (Int) -> Unit,
    onEventClick: (String) -> Unit,
    onToggleFavorite: (PlaceEvent) -> Unit,
    maxHeight: Dp,
    modifier: Modifier,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val peekPx = with(density) { PANEL_PEEK_DP.dp.toPx() }
    val maxPx = with(density) { maxHeight.toPx() }.coerceAtLeast(peekPx)
    // Nouvelle ville = panneau replié ; changer de rayon conserve l'état.
    val height = remember(area.city) { Animatable(peekPx) }
    val expanded = height.value > (peekPx + maxPx) / 2
    fun settle(expand: Boolean) { scope.launch { height.animateTo(if (expand) maxPx else peekPx) } }
    val drag = rememberDraggableState { delta -> scope.launch { height.snapTo((height.value - delta).coerceIn(peekPx, maxPx)) } }

    Surface(
        modifier = modifier.fillMaxWidth().height(with(density) { height.value.toDp() }),
        shape = MaterialTheme.shapes.large.copy(bottomStart = androidx.compose.foundation.shape.CornerSize(0), bottomEnd = androidx.compose.foundation.shape.CornerSize(0)),
        color = MaterialTheme.colorScheme.background,
        border = BorderStroke(1.dp, AgendaTheme.colors.hairline),
        shadowElevation = 6.dp,
    ) {
        Column {
            // En-tête : poignée + titre. Toucher ou glisser pour déplier / replier.
            Column(
                Modifier.fillMaxWidth()
                    .draggable(
                        state = drag,
                        orientation = Orientation.Vertical,
                        onDragStopped = { velocity ->
                            settle(
                                when {
                                    velocity < -600f -> true
                                    velocity > 600f -> false
                                    else -> height.value > (peekPx + maxPx) / 2
                                }
                            )
                        },
                    )
                    .clickable(onClickLabel = if (expanded) "Replier la liste" else "Déplier la liste") { settle(!expanded) }
                    .padding(start = 20.dp, end = 12.dp, top = 8.dp),
            ) {
                Box(
                    Modifier.align(Alignment.CenterHorizontally).size(width = 36.dp, height = 4.dp)
                        .clip(MaterialTheme.shapes.extraSmall).background(MaterialTheme.colorScheme.outline)
                )
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Autour de ${area.city.name}", style = MaterialTheme.typography.titleMedium)
                        Text(
                            Formatters.count(items.size, "événement") + " à moins de ${area.radiusKm} km",
                            style = Mono.meta, color = AgendaTheme.colors.muted,
                        )
                    }
                    Icon(
                        if (expanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                        contentDescription = null, tint = AgendaTheme.colors.muted,
                    )
                }
            }
            Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RADII.forEach { km ->
                    FilterChip(
                        selected = area.radiusKm == km,
                        onClick = { onRadius(km) },
                        label = { Text("$km km", style = Mono.label) },
                        shape = MaterialTheme.shapes.small,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    )
                }
            }
        // Effet élastique en haut comme en bas (liste hors panneau).
        val list = rememberLazyListState()
        LazyColumn(Modifier.weight(1f), state = list, contentPadding = PaddingValues(bottom = 16.dp), overscrollEffect = overscrollIfScrollable(list)) {
            if (items.isEmpty()) {
                item {
                    EmptyMessage(
                        "Rien dans ce rayon pour l'instant",
                        "Élargissez le rayon, ou regardez les événements en ligne dans l'agenda : on peut y participer depuis n'importe où.",
                    )
                }
            }
            items(items, key = { it.event.id }) { item ->
                EventCard(
                    event = item.event, sourceName = item.sourceName, favorite = item.favorite,
                    onClick = { onEventClick(item.event.id) },
                    onToggleFavorite = { onToggleFavorite(item) },
                    extra = item.distanceKm?.let { if (it < 1) "< 1 km" else "${it.toInt()} km" },
                    otherDates = item.otherDates,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp),
                )
            }
        }
        }
    }
}

@Composable
private fun EventsMap(
    places: List<Place>,
    pastPlaces: List<Place>,
    area: Area?,
    topOverlayPx: Float,
    command: CameraCommand?,
    onPlaceClick: (String) -> Unit,
    savedCamera: SavedCamera?,
    onCameraIdle: (SavedCamera) -> Unit,
    handledAreaRequest: () -> Long,
    onAreaHandled: (Long) -> Unit,
) {
    val context = LocalContext.current
    val dark = AgendaTheme.colors.isDark
    val accent = MaterialTheme.colorScheme.primary.toArgb()
    val stroke = MaterialTheme.colorScheme.background.toArgb()
    val pastColor = AgendaTheme.colors.muted.toArgb()
    val currentOnClick by rememberUpdatedState(onPlaceClick)
    val currentTopOverlay by rememberUpdatedState(topOverlayPx)
    val mapView = remember {
        MapLibre.getInstance(context.applicationContext)
        // TextureView plutôt que SurfaceView : la carte participe aux animations de Compose
        // (fondu entre onglets). Une SurfaceView reste opaque et se mélange à l'écran suivant.
        MapView(context, MapLibreMapOptions.createFromAttributes(context).textureMode(true)).apply { onCreate(null) }
    }
    val metrics = context.resources.displayMetrics.density.toDouble()

    /**
     * Zone utile de la carte en dp : sous la barre du haut, au-dessus du panneau éventuel, et
     * hors de la colonne d'outils à droite (retirée des deux côtés pour rester centré).
     */
    fun viewport(bottomPanel: Boolean): Triple<Double, Double, Double> {
        val h = mapView.height / metrics
        val w = mapView.width / metrics
        val top = currentTopOverlay / metrics
        // La caméra se cale sur le panneau replié, son état par défaut.
        val bottom = if (bottomPanel) PANEL_PEEK_DP else 0.0
        return Triple(w - 2 * TOOLS_WIDTH_DP, h - top - bottom, bottom)
    }

    /** Position de caméra plaçant (lat, lon) au centre de la zone utile. Sans état résiduel. */
    fun cameraFor(lat: Double, lon: Double, zoom: Double, bottomDp: Double): CameraPosition {
        val top = currentTopOverlay / metrics
        val target = LatLng(shiftSouth(lat, zoom, (bottomDp - top) / 2), lon)
        return CameraPosition.Builder().target(target).zoom(zoom).padding(0.0, 0.0, 0.0, 0.0).build()
    }

    fun moveTo(m: MapLibreMap, lat: Double, lon: Double, zoom: Double, bottomDp: Double) =
        m.animateCamera(CameraUpdateFactory.newCameraPosition(cameraFor(lat, lon, zoom, bottomDp)), 600)

    fun franceCamera(): CameraPosition? {
        val (w, h, _) = viewport(bottomPanel = false)
        // Toute la largeur : la colonne d'outils tombe sur la mer, à droite de la France.
        return frame(FRANCE_BOUNDS, w + 2 * TOOLS_WIDTH_DP - 24, h - 16)?.let { cameraFor(it.latitude, it.longitude, it.zoom, 0.0) }
    }

    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    // Numéro du dernier chargement de style demandé : un chargement dépassé est ignoré.
    var styleRequest by remember { mutableStateOf(0) }

    // Cycle de vie de la MapView calqué sur celui de l'écran.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onDestroy()
        }
    }

    LaunchedEffect(mapView) {
        mapView.getMapAsync { m ->
            // Retour sur l'onglet : on reprend la vue laissée. Premier affichage : la France entière.
            val saved = savedCamera
            if (saved != null) {
                m.cameraPosition = CameraPosition.Builder().target(LatLng(saved.latitude, saved.longitude))
                    .zoom(saved.zoom).padding(0.0, 0.0, 0.0, 0.0).build()
            } else {
                m.cameraPosition = CameraPosition.Builder().target(FRANCE).zoom(5.0).build()
                // La taille de la vue n'est connue qu'une fois la mise en page faite (largeur > 0).
                mapView.doOnLayout { franceCamera()?.let { m.cameraPosition = it } }
            }
            m.addOnCameraIdleListener {
                val c = m.cameraPosition
                c.target?.let { t -> onCameraIdle(SavedCamera(t.latitude, t.longitude, c.zoom)) }
            }
            m.uiSettings.isRotateGesturesEnabled = false
            m.uiSettings.isTiltGesturesEnabled = false
            m.addOnMapClickListener { latLng ->
                val point = m.projection.toScreenLocation(latLng)
                // Les événements à venir sont prioritaires sur les passés au même endroit.
                // Bulle de regroupement : on zoome jusqu'à ce que ses points se séparent.
                // Zone d'appui élargie autour du doigt : on retient le point le plus proche.
                val slop = TOUCH_SLOP_DP * mapView.resources.displayMetrics.density
                val area = RectF(point.x - slop, point.y - slop, point.x + slop, point.y + slop)
                fun nearest(layer: String) = m.queryRenderedFeatures(area, layer).minByOrNull { f ->
                    val g = f.geometry() as? Point ?: return@minByOrNull Float.MAX_VALUE
                    val p = m.projection.toScreenLocation(LatLng(g.latitude(), g.longitude()))
                    (p.x - point.x) * (p.x - point.x) + (p.y - point.y) * (p.y - point.y)
                }
                val cluster = nearest(CLUSTER_LAYER)?.let { it to SOURCE_ID }
                    ?: nearest(PAST_CLUSTER_LAYER)?.let { it to PAST_SOURCE }
                if (cluster != null) {
                    val (feature, sourceId) = cluster
                    val zoom = m.style?.getSourceAs<GeoJsonSource>(sourceId)
                        ?.let { src -> runCatching { src.getClusterExpansionZoom(feature) }.getOrNull() }
                        ?: (m.cameraPosition.zoom + 2).toInt()
                    val at = (feature.geometry() as? Point) ?: return@addOnMapClickListener true
                    m.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(at.latitude(), at.longitude()), zoom.toDouble() + 0.5), 450)
                    return@addOnMapClickListener true
                }
                val hit = nearest(LAYER_ID) ?: nearest(PAST_LAYER)
                hit?.getStringProperty("key")?.let { currentOnClick(it) }
                hit != null
            }
            map = m
        }
    }

    LaunchedEffect(map, dark) {
        val m = map ?: return@LaunchedEffect
        style = null
        val request = ++styleRequest
        m.setStyle(Style.Builder().fromUri(if (dark) STYLE_DARK else STYLE_LIGHT)) { loaded ->
            if (request != styleRequest) return@setStyle
            useFrenchLabels(loaded)
            loaded.addSource(GeoJsonSource(AREA_SOURCE, FeatureCollection.fromFeatures(emptyList())))
            loaded.addLayer(FillLayer("area-fill", AREA_SOURCE).withProperties(
                PropertyFactory.fillColor(accent), PropertyFactory.fillOpacity(0.08f)))
            loaded.addLayer(LineLayer("area-line", AREA_SOURCE).withProperties(
                PropertyFactory.lineColor(accent), PropertyFactory.lineWidth(1.5f),
                PropertyFactory.lineDasharray(arrayOf(2f, 2f))))
            loaded.addSource(clusteredSource(PAST_SOURCE))
            addClusterLayers(loaded, PAST_SOURCE, PAST_CLUSTER_LAYER, pastColor, stroke, 0.75f)
            loaded.addLayer(
                CircleLayer(PAST_LAYER, PAST_SOURCE).withFilter(Expression.not(Expression.has("point_count"))).withProperties(
                    PropertyFactory.circleColor(pastColor),
                    PropertyFactory.circleOpacity(0.75f),
                    PropertyFactory.circleStrokeColor(stroke),
                    PropertyFactory.circleStrokeWidth(1.5f),
                    PropertyFactory.circleRadius(6f),
                )
            )
            loaded.addSource(clusteredSource(SOURCE_ID))
            addClusterLayers(loaded, SOURCE_ID, CLUSTER_LAYER, accent, stroke, 0.9f)
            loaded.addLayer(
                CircleLayer(LAYER_ID, SOURCE_ID).withFilter(Expression.not(Expression.has("point_count"))).withProperties(
                    PropertyFactory.circleColor(accent),
                    PropertyFactory.circleOpacity(0.9f),
                    PropertyFactory.circleStrokeColor(stroke),
                    PropertyFactory.circleStrokeWidth(2f),
                    PropertyFactory.circleRadius(
                        Expression.interpolate(
                            Expression.linear(), Expression.get("count"),
                            Expression.stop(1, 7f), Expression.stop(5, 11f), Expression.stop(20, 16f),
                        )
                    ),
                )
            )
            // Nombre d'événements sur un lieu qui en a plusieurs.
            loaded.addLayer(countLabels("$LAYER_ID-count", SOURCE_ID, Expression.all(
                Expression.not(Expression.has("point_count")), Expression.gt(Expression.get("count"), Expression.literal(1)),
            ), Expression.get("count"), stroke, 10f))
            style = loaded
        }
    }

    LaunchedEffect(style, places) {
        val s = style ?: return@LaunchedEffect
        val features = places.map { p ->
            Feature.fromGeometry(Point.fromLngLat(p.longitude, p.latitude)).apply {
                addStringProperty("key", p.key)
                addNumberProperty("count", p.events.size)
            }
        }
        s.getSourceAs<GeoJsonSource>(SOURCE_ID)?.setGeoJson(FeatureCollection.fromFeatures(features))
    }

    LaunchedEffect(style, pastPlaces) {
        val s = style ?: return@LaunchedEffect
        val features = pastPlaces.map { p ->
            Feature.fromGeometry(Point.fromLngLat(p.longitude, p.latitude)).apply {
                addStringProperty("key", p.key)
                addNumberProperty("count", p.events.size)
            }
        }
        s.getSourceAs<GeoJsonSource>(PAST_SOURCE)?.setGeoJson(FeatureCollection.fromFeatures(features))
    }

    // Zone recherchée : cercle pointillé + recadrage de la caméra.
    LaunchedEffect(style, area) {
        val s = style ?: return@LaunchedEffect
        val m = map ?: return@LaunchedEffect
        val src = s.getSourceAs<GeoJsonSource>(AREA_SOURCE)
        if (area == null) {
            src?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
            return@LaunchedEffect
        }
        val center = area.city.point
        src?.setGeoJson(Feature.fromGeometry(circlePolygon(center.latitude, center.longitude, area.radiusKm.toDouble())))
        // Zone visible : entre la barre du haut et le panneau. Zoom choisi pour que le cercle y tienne.
        // Cadrage déjà appliqué (carte recréée au retour sur l'onglet) : on garde la vue de l'utilisateur.
        if (area.request == handledAreaRequest()) return@LaunchedEffect
        onAreaHandled(area.request)
        val (w, h, bottom) = viewport(bottomPanel = true)
        val zoom = zoomToFit(area.radiusKm.toDouble(), center.latitude, minOf(w, h) / 2 - 16)
        moveTo(m, center.latitude, center.longitude, zoom, bottom)
    }

    // Outils : zoom, France entière, cadrage sur les événements affichés.
    LaunchedEffect(command) {
        val cmd = command ?: return@LaunchedEffect
        val m = map ?: return@LaunchedEffect
        when (cmd.action) {
            CameraAction.ZOOM_IN -> m.animateCamera(CameraUpdateFactory.zoomIn(), 250)
            CameraAction.ZOOM_OUT -> m.animateCamera(CameraUpdateFactory.zoomOut(), 250)
            // La recherche est fermée en même temps : pas de panneau en bas.
            CameraAction.FRANCE -> franceCamera()?.let { m.animateCamera(CameraUpdateFactory.newCameraPosition(it), 600) }
        }
    }

    AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
}

/** Remplace les libellés du fond de carte par leur nom français quand il existe. */
/**
 * Source regroupée : les points proches à l'écran fusionnent en une bulle, qui se sépare en zoomant.
 * « total » additionne les événements de chaque lieu (un lieu peut en compter plusieurs).
 */
private fun clusteredSource(id: String) = GeoJsonSource(
    id, FeatureCollection.fromFeatures(emptyList()),
    GeoJsonOptions().withCluster(true).withClusterRadius(CLUSTER_RADIUS).withClusterMaxZoom(CLUSTER_MAX_ZOOM)
        .withClusterProperty("total", Expression.sum(Expression.accumulated(), Expression.get("total")), Expression.get("count")),
)

/** Bulle de regroupement : halo translucide, disque qui grossit avec le nombre, nombre au centre. */
private fun addClusterLayers(style: Style, source: String, layerId: String, color: Int, stroke: Int, opacity: Float) {
    val size = Expression.step(Expression.get("total"), Expression.literal(13f),
        Expression.stop(5, 16f), Expression.stop(15, 20f), Expression.stop(40, 25f))
    val halo = Expression.step(Expression.get("total"), Expression.literal(19f),
        Expression.stop(5, 23f), Expression.stop(15, 28f), Expression.stop(40, 34f))
    style.addLayer(CircleLayer("$layerId-halo", source).withFilter(Expression.has("point_count")).withProperties(
        PropertyFactory.circleColor(color), PropertyFactory.circleOpacity(opacity * 0.25f), PropertyFactory.circleRadius(halo),
    ))
    style.addLayer(CircleLayer(layerId, source).withFilter(Expression.has("point_count")).withProperties(
        PropertyFactory.circleColor(color), PropertyFactory.circleOpacity(opacity),
        PropertyFactory.circleStrokeColor(stroke), PropertyFactory.circleStrokeWidth(2f), PropertyFactory.circleRadius(size),
    ))
    style.addLayer(countLabels("$layerId-count", source, Expression.has("point_count"), Expression.get("total"), stroke, 12f))
}

private fun countLabels(id: String, source: String, filter: Expression, value: Expression, color: Int, size: Float) =
    SymbolLayer(id, source).withFilter(filter).withProperties(
        PropertyFactory.textField(Expression.toString(value)),
        PropertyFactory.textFont(arrayOf("Noto Sans Bold")),
        PropertyFactory.textSize(size),
        PropertyFactory.textColor(color),
        PropertyFactory.textAllowOverlap(true),
        PropertyFactory.textIgnorePlacement(true),
    )

private fun useFrenchLabels(style: Style) {
    val french = Expression.coalesce(Expression.get("name:fr"), Expression.get("name:latin"), Expression.get("name"))
    style.layers.filterIsInstance<SymbolLayer>().forEach { layer ->
        val current = layer.textField.expression?.toString() ?: return@forEach
        if ("name" in current) layer.setProperties(PropertyFactory.textField(french))
    }
}

/** Polygone approximant un cercle de [radiusKm] autour d'un point (64 segments). */
private fun circlePolygon(lat: Double, lon: Double, radiusKm: Double): Polygon {
    val r = radiusKm / 6371.0
    val la = Math.toRadians(lat)
    val lo = Math.toRadians(lon)
    val points = (0..64).map { i ->
        val bearing = 2 * Math.PI * i / 64
        val pLat = asin(sin(la) * cos(r) + cos(la) * sin(r) * cos(bearing))
        val pLon = lo + atan2(sin(bearing) * sin(r) * cos(la), cos(r) - sin(la) * sin(pLat))
        Point.fromLngLat(Math.toDegrees(pLon), Math.toDegrees(pLat))
    }
    return Polygon.fromLngLats(listOf(points))
}
