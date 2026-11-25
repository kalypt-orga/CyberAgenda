package fr.cyberagenda.feature.events

import fr.cyberagenda.core.ui.CardTitle
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import fr.cyberagenda.core.ui.style
import fr.cyberagenda.core.ui.cardBorder
import fr.cyberagenda.core.ui.cardContainerColor
import fr.cyberagenda.core.ui.themedCard
import fr.cyberagenda.core.ui.scrolledFromTop
import fr.cyberagenda.core.ui.ScrollDivider
import androidx.compose.foundation.lazy.rememberLazyListState
import fr.cyberagenda.core.ui.overscrollIfScrollable
import fr.cyberagenda.core.ui.releaseSearchOnOutsideTap
import fr.cyberagenda.core.ui.searchZone
import fr.cyberagenda.core.ui.rememberSearchZone
import fr.cyberagenda.core.ui.SearchZone
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import fr.cyberagenda.core.ui.RecentSearchList
import fr.cyberagenda.core.model.RecentSearches
import androidx.compose.foundation.BorderStroke
import fr.cyberagenda.core.ui.tourTarget
import fr.cyberagenda.core.ui.Tour
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import fr.cyberagenda.core.model.EventKind
import fr.cyberagenda.core.ui.kindColor
import fr.cyberagenda.core.ui.KindIcon
import fr.cyberagenda.core.ui.LegendButton
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.cyberagenda.core.ui.AgendaTheme
import fr.cyberagenda.core.ui.EmptyMessage
import fr.cyberagenda.core.ui.EventCard
import fr.cyberagenda.core.ui.FilterBar
import fr.cyberagenda.core.model.EventFilter
import fr.cyberagenda.core.ui.Formatters
import fr.cyberagenda.core.ui.Mono
import fr.cyberagenda.core.ui.ScreenHeader
import fr.cyberagenda.core.ui.SectionHeader
import java.time.YearMonth

@Composable
fun EventsRoute(
    onEventClick: (String) -> Unit,
    onOpenSources: () -> Unit,
    onPropose: () -> Unit,
    viewModel: EventsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    EventsScreen(
        state = state,
        onRefresh = { viewModel.refresh() },
        onQueryChange = viewModel::setQuery,
        onQueryCommit = viewModel::commitQuery,
        onHistoryPick = viewModel::useHistory,
        onHistoryRemove = viewModel::removeHistory,
        onFilterChange = viewModel::setFilter,
        onToggleFavorite = viewModel::toggleFavorite,
        // Ouvrir une fiche depuis des résultats de recherche = recherche utile : on la mémorise.
        onEventClick = { id -> viewModel.commitQuery(); onEventClick(id) },
        onOpenSources = onOpenSources,
        onDismissTip = viewModel::dismissBeginnerTip,
        onPropose = onPropose,
        onToggleWeek = viewModel::toggleWeek,
        onShowFurther = viewModel::showFurther,
        onTogglePast = viewModel::togglePast,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun EventsScreen(
    state: EventsUiState,
    onRefresh: () -> Unit,
    onQueryChange: (String) -> Unit,
    onQueryCommit: () -> Unit,
    onHistoryPick: (String) -> Unit,
    onHistoryRemove: (String) -> Unit,
    onFilterChange: (EventFilter) -> Unit,
    onToggleFavorite: (EventItem) -> Unit,
    onEventClick: (String) -> Unit,
    onOpenSources: () -> Unit,
    onDismissTip: (Boolean) -> Unit,
    onPropose: () -> Unit,
    onToggleWeek: (LocalDate) -> Unit,
    onShowFurther: () -> Unit,
    onTogglePast: () -> Unit,
) {
    // Regroupement par mois pour les en-têtes de section.
    val byMonth = remember(state.rows) {
        state.rows.groupBy { YearMonth.from(Formatters.zoned(it.startsAtEpochMs)) }
    }
    // Toucher en dehors de la recherche (liste, filtres…) ou fermer le clavier : le champ se désactive.
    val searchZone = rememberSearchZone()
    val list = rememberLazyListState()
    Column(Modifier.fillMaxSize().releaseSearchOnOutsideTap(searchZone)) {
        ScreenHeader(
            title = "cyberagenda",
            subtitle = when {
                !state.loaded -> "chargement…"
                state.refreshing && state.total == 0 -> "synchronisation des sources…"
                state.narrowed -> Formatters.count(state.total, "résultat")
                else -> Formatters.count(state.total, "événement") + " à venir"
            },
        ) {
            LegendButton(Modifier.tourTarget(Tour.LEGEND))
            IconButton(onClick = onPropose, modifier = Modifier.tourTarget(Tour.PROPOSE)) {
                Icon(Icons.Default.Add, contentDescription = "Proposer un événement")
            }
        }
        ScrollDivider(scrolledFromTop(list))
        PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                Modifier.fillMaxSize(),
                state = list,
                contentPadding = PaddingValues(bottom = 24.dp),
                overscrollEffect = overscrollIfScrollable(list),
            ) {
                item {
                    SearchAndFilters(
                        state.query, state.filter, state.history, onQueryChange, onQueryCommit,
                        onHistoryPick, onHistoryRemove, onFilterChange, searchZone,
                    )
                }
                if (state.showBeginnerTip) {
                    item { BeginnerTip(onDismissTip) }
                }
                if (state.loaded && state.total == 0 && !state.refreshing) {
                    item {
                        val filtered = state.narrowed
                        EmptyMessage(
                            title = if (filtered) "Rien ne correspond" else "Aucun événement pour l'instant",
                            body = if (filtered) "Essayez d'enlever un filtre ou de modifier la recherche. Les filtres s'appliquent aussi au calendrier et à la carte."
                            else "Tirez vers le bas pour actualiser, ou activez d'autres sources dans les paramètres.",
                        ) { TextButton(onClick = onOpenSources) { Text("Ouvrir les paramètres") } }
                    }
                }
                // Terminés récemment : section repliable en tête de liste (filtre « passés »).
                if (state.past.isNotEmpty()) {
                    item(key = "past-header") { PastHeader(state.past.size, state.pastExpanded, onTogglePast) }
                    if (state.pastExpanded) {
                        items(state.past, key = { "past-" + it.event.id }) { item ->
                            AgendaEventCard(item, onEventClick, onToggleFavorite, Modifier.animateItem())
                        }
                    }
                }
                byMonth.forEach { (month, rows) ->
                    val count = rows.sumOf { if (it is AgendaRow.OnlineCtfWeek) it.items.size else 1 }
                    stickyHeader(key = "h-$month") { SectionHeader(Formatters.monthYear(month), count = count) }
                    rows.forEach { row ->
                        when (row) {
                            is AgendaRow.Single -> item(key = row.key) {
                                AgendaEventCard(row.item, onEventClick, onToggleFavorite, Modifier.animateItem())
                            }
                            is AgendaRow.OnlineCtfWeek -> {
                                item(key = row.key) {
                                    CtfWeekCard(row, onClick = { onToggleWeek(row.weekStart) }, modifier = Modifier.animateItem())
                                }
                                if (row.expanded) {
                                    items(row.items, key = { it.event.id }) { item ->
                                        AgendaEventCard(item, onEventClick, onToggleFavorite, Modifier.padding(start = 20.dp).animateItem())
                                    }
                                }
                            }
                        }
                    }
                }
                if (state.beyondHorizon > 0) {
                    item(key = "further") { FurtherButton(state, onShowFurther) }
                }
            }
        }
    }
}

@Composable
private fun AgendaEventCard(item: EventItem, onEventClick: (String) -> Unit, onToggleFavorite: (EventItem) -> Unit, modifier: Modifier) {
    EventCard(
        event = item.event,
        sourceName = item.sourceName,
        favorite = item.favorite,
        onClick = { onEventClick(item.event.id) },
        onToggleFavorite = { onToggleFavorite(item) },
        otherDates = item.otherDates,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 5.dp),
    )
}

/**
 * Carte repliable regroupant les CTF en ligne d'une même semaine.
 * Repliée : teinte vive et effet de « pile » (cartes empilées) pour signaler le groupe à ouvrir.
 * Dépliée : teinte atténuée, l'attention passe aux CTF affichés en dessous.
 */
@Composable
private fun CtfWeekCard(group: AgendaRow.OnlineCtfWeek, onClick: () -> Unit, modifier: Modifier) {
    val accent = kindColor(EventKind.CTF)
    val colors = AgendaTheme.colors
    val dark = colors.isDark
    val surface = MaterialTheme.colorScheme.surfaceContainer
    val spec = tween<Color>(220)
    val cardColor by animateColorAsState(
        if (group.expanded) surface else accent.copy(alpha = if (dark) 0.22f else 0.14f).compositeOver(surface), spec, label = "fond",
    )
    val stubColor by animateColorAsState(
        accent.copy(alpha = if (group.expanded) (if (dark) 0.12f else 0.09f) else (if (dark) 0.34f else 0.24f)), spec, label = "talon",
    )
    val borderColor by animateColorAsState(if (group.expanded) colors.hairline else accent.copy(alpha = 0.55f), spec, label = "bord")
    // Thèmes à ombre pleine (Brut, Arcade) : la pile REMPLACE l'ombre (sinon elle la recouvre).
    // Même forme que les autres thèmes (vers le bas, rétrécie vers l'intérieur), un peu plus
    // décalée pour que chaque marche reste lisible malgré les contours épais.
    val hardShadow = AgendaTheme.style.hardShadow
    val solidStack = hardShadow > 0.dp
    val pileStep = if (solidStack) hardShadow + 2.dp else 4.dp
    val pile by animateDpAsState(if (group.expanded) 0.dp else pileStep, tween(220), label = "pile")

    Box(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp).padding(bottom = pile * 2)) {
        // Effet de pile : deux bords décalés sous la carte, visibles seulement repliée.
        // Cartes translucides (Gazette, Plan) : on ne dessine que les bords qui dépassent sous la
        // carte (filets ou pointillés), sinon la pile se verrait à travers la carte.
        val style = AgendaTheme.style
        if (pile > 0.dp && style.transparentCards) {
            val ink = if (style.dashedBorder) (style.cardBorderColor ?: colors.muted) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
            val dashed = style.dashedBorder
            Box(Modifier.matchParentSize().drawBehind {
                // La zone de dessin s'arrête au bord bas de la carte (la marge réservée à la pile
                // est en dehors) : les bords de la pile se dessinent en dessous, hors de la zone.
                val step = pile.toPx()
                val bottom = size.height
                val effect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(8f, 6f)) else null
                // Chaque fausse carte ne montre que ce qui dépasse sous la précédente : ses bords
                // partent du bas de la carte du dessus (pas de traits qui se croisent).
                for (level in 1..2) {
                    val top = bottom + step * (level - 1)
                    val y = bottom + step * level
                    val inset = (6 * level).dp.toPx()
                    drawLine(ink, Offset(inset, y), Offset(size.width - inset, y), 1.dp.toPx(), pathEffect = effect)
                    drawLine(ink, Offset(inset, top), Offset(inset, y), 1.dp.toPx(), pathEffect = effect)
                    drawLine(ink, Offset(size.width - inset, top), Offset(size.width - inset, y), 1.dp.toPx(), pathEffect = effect)
                }
            })
        } else if (pile > 0.dp && solidStack) {
            listOf(2, 1).forEach { level ->
                Surface(
                    modifier = Modifier.matchParentSize().padding(horizontal = (6 * level).dp).offset(y = pile * level),
                    shape = MaterialTheme.shapes.medium,
                    color = cardContainerColor(EventKind.CTF),
                    border = cardBorder(accent) ?: BorderStroke(1.dp, accent),
                ) {}
            }
        } else if (pile > 0.dp) {
            // Thème secret : les fausses cartes ont l'allure des vraies (fond plein, contour du thème).
            val secretPile = AgendaTheme.colors.secret != null
            listOf(2, 1).forEach { level ->
                Surface(
                    modifier = Modifier.matchParentSize().padding(horizontal = (6 * level).dp).offset(y = pile * level),
                    shape = MaterialTheme.shapes.medium,
                    color = if (secretPile) cardContainerColor(EventKind.CTF) else accent.copy(alpha = if (dark) 0.14f else 0.08f).compositeOver(surface),
                    border = (if (secretPile) cardBorder(accent) else null) ?: BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
                ) {}
            }
        }
        // Thème secret : même allure que les cartes d'événement (fond, contour, ombre, décor).
        val secret = AgendaTheme.colors.secret != null
        val shape = MaterialTheme.shapes.medium
        Surface(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth()
                // Pile visible : elle tient lieu d'ombre (pas d'ombre en plus).
                .then(if (secret) Modifier.themedCard(shape, "ctf-" + group.weekStart, accent, shadow = !(solidStack && pile > 0.dp)) else Modifier)
                .semantics { stateDescription = if (group.expanded) "déplié" else "replié" },
            shape = shape,
            color = if (secret) cardContainerColor(EventKind.CTF) else cardColor,
            border = if (secret) cardBorder(accent) else BorderStroke(1.dp, borderColor),
        ) {
            Row(Modifier.height(IntrinsicSize.Min)) {
                Box(Modifier.width(if (group.expanded) 3.dp else 5.dp).fillMaxHeight().background(accent))
                Column(
                    Modifier.width(62.dp).fillMaxHeight().background(if (AgendaTheme.style.solidStub) accent else stubColor).padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    // Talon en aplat (Brut) : icône à l'encre, sinon invisible sur la couleur pleine.
                    KindIcon(EventKind.CTF, 18.dp, tint = if (AgendaTheme.style.solidStub) androidx.compose.ui.graphics.Color(0xFF111111) else kindColor(EventKind.CTF))
                    Text("×${group.items.size}", style = MaterialTheme.typography.titleLarge)
                }
                Column(Modifier.weight(1f).padding(start = 12.dp, top = 12.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("CTF · EN LIGNE · GROUPÉS", style = Mono.tag, color = accent)
                    Text("${group.items.size} CTF en ligne ${weekLabel(group.weekStart)}", style = CardTitle)
                    Text(
                        if (group.expanded) group.items.joinToString(", ") { it.event.title } else "Touchez pour voir : " + group.items.joinToString(", ") { it.event.title },
                        style = Mono.meta, color = colors.muted, maxLines = if (group.expanded) 3 else 1, overflow = TextOverflow.Ellipsis,
                    )
                    if (group.favorites > 0) Text("♥ ${Formatters.count(group.favorites, "favori")}", style = Mono.meta, color = MaterialTheme.colorScheme.primary)
                }
                Icon(
                    if (group.expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (group.expanded) "Replier" else "Voir les ${group.items.size} CTF",
                    tint = if (group.expanded) colors.muted else accent,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }
    }
}

private val weekFormatter = DateTimeFormatter.ofPattern("d MMM", Formatters.locale)

private fun weekLabel(weekStart: LocalDate): String {
    val thisWeek = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    return when (weekStart) {
        thisWeek -> "cette semaine"
        thisWeek.plusWeeks(1) -> "la semaine prochaine"
        else -> "la semaine du ${weekStart.format(weekFormatter)}"
    }
}

/** En-tête repliable des événements terminés ces 3 derniers mois. */
@Composable
private fun PastHeader(count: Int, expanded: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp)
            .semantics { stateDescription = if (expanded) "déplié" else "replié" },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, AgendaTheme.colors.hairline),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(9.dp).clip(androidx.compose.foundation.shape.CircleShape).background(AgendaTheme.colors.muted))
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text("Terminés ces 3 derniers mois", style = MaterialTheme.typography.titleSmall)
                Text(Formatters.count(count, "événement"), style = Mono.meta, color = AgendaTheme.colors.muted)
            }
            Icon(
                if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) "Masquer" else "Afficher les événements terminés",
                tint = AgendaTheme.colors.muted,
            )
        }
    }
}

/** Bas de liste : les événements au-delà de l'horizon restent accessibles d'un toucher. */
@Composable
private fun FurtherButton(state: EventsUiState, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "Affichage des ${state.horizonMonths} prochains mois",
            style = Mono.meta, color = AgendaTheme.colors.muted,
        )
        OutlinedButton(
            onClick = onClick,
            shape = MaterialTheme.shapes.small,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.padding(top = 6.dp),
        ) { Text("Voir plus loin · ${Formatters.count(state.beyondHorizon, "autre événement", "autres événements")}") }
    }
}

@Composable
private fun SearchAndFilters(
    query: String,
    filter: EventFilter,
    history: List<String>,
    onQuery: (String) -> Unit,
    onCommit: () -> Unit,
    onHistoryPick: (String) -> Unit,
    onHistoryRemove: (String) -> Unit,
    onFilter: (EventFilter) -> Unit,
    zone: SearchZone,
) {
    val focus = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    Column(Modifier.padding(horizontal = 16.dp)) {
        // Champ + recherches récentes : y toucher ne désactive pas le champ.
        Column(Modifier.searchZone(zone)) {
            OutlinedTextField(
                value = query,
                onValueChange = onQuery,
                placeholder = { Text("Titre, ville, organisateur…") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQuery("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Effacer la recherche")
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onCommit(); focus.clearFocus() }),
                shape = MaterialTheme.shapes.medium,
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = AgendaTheme.colors.hairline,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                modifier = Modifier.fillMaxWidth().tourTarget(Tour.SEARCH).onFocusChanged { focused = it.isFocused },
            )
            // Recherches récentes : visibles pendant la saisie (les 3 dernières, ou celles qui commencent pareil).
            val recent = if (focused) RecentSearches.suggestions(history, query) { it } else emptyList()
            RecentSearchList(
                entries = recent.map { it to it },
                onPick = { onHistoryPick(it); focus.clearFocus() },
                onRemove = onHistoryRemove,
            )
        }
        FilterBar(filter, onFilter, Modifier.padding(vertical = 10.dp).tourTarget(Tour.FILTERS))
    }
}

@Composable
private fun BeginnerTip(onDismiss: (Boolean) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.medium,
        color = AgendaTheme.colors.accentSoft,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 4.dp)) {
            Text("Vous débutez ?", style = MaterialTheme.typography.titleSmall)
            Text(
                "Dans « Filtres », le niveau « Débutants » ne montre que les ateliers et rencontres ouverts à toutes et tous, " +
                    "sans prérequis. Les CTF « en ligne » se font depuis chez soi, souvent en équipe.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { onDismiss(false) }) { Text("Compris") }
                TextButton(onClick = { onDismiss(true) }) { Text("Voir ces événements") }
            }
        }
    }
}
