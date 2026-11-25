package fr.cyberagenda.feature.calendar

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.background
import fr.cyberagenda.core.ui.scrolledFromTop
import fr.cyberagenda.core.ui.ScrollDivider
import androidx.compose.foundation.lazy.rememberLazyListState
import fr.cyberagenda.core.ui.overscrollIfScrollable
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.cyberagenda.core.ui.AgendaTheme
import fr.cyberagenda.core.ui.EventCard
import fr.cyberagenda.core.ui.FilterBar
import fr.cyberagenda.core.ui.LegendButton
import fr.cyberagenda.core.ui.Formatters
import fr.cyberagenda.core.ui.KindDot
import fr.cyberagenda.core.ui.Mono
import fr.cyberagenda.core.ui.ScreenHeader
import fr.cyberagenda.core.ui.SectionHeader
import fr.cyberagenda.core.ui.kindColor
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun CalendarRoute(onEventClick: (String) -> Unit, viewModel: CalendarViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val dayEvents = state.byDay[state.selected].orEmpty()
    val monthCount = state.byDay.values.flatten().distinctBy { it.event.id }.size

    // En-tête fixe (comme l'agenda) : seuls filtres, grille et programme du jour défilent.
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("calendrier", Formatters.count(monthCount, "événement") + " ce mois-ci") { LegendButton() }
        val list = rememberLazyListState()
        ScrollDivider(scrolledFromTop(list))
        LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(bottom = 24.dp), overscrollEffect = overscrollIfScrollable(list)) {
            item {
                FilterBar(state.filter, viewModel::setFilter, Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }
            item {
                var picking by remember { mutableStateOf(false) }
                MonthBar(state.month, state.month > state.minMonth, onPrev = { viewModel.showMonth(-1) }, onNext = { viewModel.showMonth(1) },
                    onPick = { picking = true })
                if (picking) {
                    MonthPickerDialog(
                        current = state.month, min = state.minMonth, max = CalendarViewModel.maxMonth,
                        onPick = { viewModel.goTo(it); picking = false },
                        onDismiss = { picking = false },
                    )
                }
            }
            item {
                // Balayer la grille change de mois ; la grille glisse dans le sens du geste.
                val canGoBack = state.month > state.minMonth
                var drag by remember { mutableStateOf(0f) }
                val threshold = with(LocalDensity.current) { 64.dp.toPx() }
                AnimatedContent(
                    targetState = state.month,
                    transitionSpec = {
                        val dir = if (targetState > initialState) 1 else -1
                        (slideInHorizontally(tween(240)) { w -> dir * w } + fadeIn(tween(240))) togetherWith
                            (slideOutHorizontally(tween(240)) { w -> -dir * w } + fadeOut(tween(160))) using SizeTransform(clip = true)
                    },
                    label = "mois",
                    modifier = Modifier.pointerInput(state.month) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                when {
                                    drag < -threshold -> viewModel.showMonth(1)
                                    drag > threshold && canGoBack -> viewModel.showMonth(-1)
                                }
                                drag = 0f
                            },
                            onDragCancel = { drag = 0f },
                        ) { _, delta -> drag += delta }
                    },
                ) { month ->
                    // Pendant l'animation, le mois sortant est dessiné sans ses événements.
                    val current = month == state.month
                    MonthGrid(
                        month, state.selected,
                        if (current) state.byDay else emptyMap(), if (current) state.spans else emptyList(),
                        viewModel::select,
                    )
                }
            }
            item {
                SectionHeader(Formatters.dayLong(state.selected), count = dayEvents.size.takeIf { it > 0 })
            }
            if (dayEvents.isEmpty()) {
                item {
                    Text(
                        "Rien de prévu ce jour-là.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }
            }
            items(dayEvents, key = { it.event.id }) { item ->
                EventCard(
                    event = item.event, sourceName = item.sourceName, favorite = item.favorite,
                    onClick = { onEventClick(item.event.id) },
                    onToggleFavorite = { viewModel.toggleFavorite(item) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp),
                )
            }
        }
    }
}

@Composable
private fun MonthBar(month: YearMonth, canGoBack: Boolean, onPrev: () -> Unit, onNext: () -> Unit, onPick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrev, enabled = canGoBack) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Mois précédent")
        }
        // Appui sur « Mois Année » : sélecteur pour sauter directement à un mois.
        Row(
            Modifier.weight(1f).clip(MaterialTheme.shapes.small)
                .clickable(onClickLabel = "Choisir le mois et l'année", onClick = onPick)
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(Formatters.monthYear(month), style = MaterialTheme.typography.titleLarge)
        }
        IconButton(onClick = onNext) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Mois suivant")
        }
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    selected: LocalDate,
    byDay: Map<LocalDate, List<DayEvent>>,
    spans: List<Span>,
    onSelect: (LocalDate) -> Unit,
) {
    val today = LocalDate.now()
    val firstOffset = month.atDay(1).dayOfWeek.value - DayOfWeek.MONDAY.value // semaine du lundi
    val gridStart = month.atDay(1).minusDays(firstOffset.toLong())
    val rows = (firstOffset + month.lengthOfMonth() + 6) / 7

    Column(Modifier.padding(horizontal = 12.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            listOf("L", "M", "M", "J", "V", "S", "D").forEach {
                Text(it, style = Mono.tag, color = AgendaTheme.colors.muted, modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
        for (r in 0 until rows) {
            val weekStart = gridStart.plusDays(r * 7L)
            val weekEnd = weekStart.plusDays(6)
            val first = maxOf(weekStart, month.atDay(1))
            val last = minOf(weekEnd, month.atEndOfMonth())
            val lanes = assignLanes(spans.filter { it.end >= first && it.start <= last })
            val hidden = lanes.drop(MAX_LANES).flatten()
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val cellW = maxWidth / 7
                val cellH = cellW / CELL_RATIO
                Row(Modifier.fillMaxWidth()) {
                    for (c in 0 until 7) {
                        val date = weekStart.plusDays(c.toLong())
                        Box(Modifier.weight(1f).aspectRatio(CELL_RATIO).padding(2.dp)) {
                            if (YearMonth.from(date) == month) {
                                // Les points ne concernent que les événements d'un seul jour.
                                val singles = byDay[date].orEmpty().filter {
                                    !fr.cyberagenda.core.ui.Formatters.isMultiDay(it.event.startsAtEpochMs, it.event.endsAtEpochMs)
                                }
                                DayCell(
                                    date, date == selected, date == today, date < today,
                                    total = byDay[date].orEmpty().size, events = singles,
                                    hiddenSpans = hidden.count { date in it.start..it.end },
                                ) { onSelect(date) }
                            }
                        }
                    }
                }
                WeekBars(lanes.take(MAX_LANES), weekStart, first, last, cellW, cellH, selected)
            }
        }
    }
}

private const val MAX_LANES = 3
private const val CELL_RATIO = 1.0f

/** Répartit les événements sur des lignes sans chevauchement (le plus tôt et le plus long d'abord). */
private fun assignLanes(spans: List<Span>): List<List<Span>> {
    val lanes = mutableListOf<MutableList<Span>>()
    spans.forEach { span ->
        val lane = lanes.firstOrNull { l -> l.none { it.end >= span.start && it.start <= span.end } }
        if (lane != null) lane += span else lanes += mutableListOf(span)
    }
    return lanes
}

/**
 * Barres des événements sur plusieurs jours pour une semaine : une barre continue du premier
 * au dernier jour. Au-delà de [MAX_LANES] lignes, les jours concernés affichent « +n ».
 */
/** Opacité des éléments passés (points, barres) : présents mais nettement en retrait. */
private const val PAST_ALPHA = 0.28f

@Composable
private fun WeekBars(lanes: List<List<Span>>, weekStart: LocalDate, first: LocalDate, last: LocalDate, cellW: Dp, cellH: Dp, selected: LocalDate) {
    val today = LocalDate.now()
    lanes.forEachIndexed { laneIndex, lane ->
        lane.forEach { span ->
            val s = maxOf(span.start, first)
            val e = minOf(span.end, last)
            val color = kindColor(span.event.kind)
            // Un événement à cheval sur aujourd'hui : partie passée estompée, la suite en couleur.
            val pieces = when {
                e < today -> listOf(Triple(s, e, true))
                s >= today -> listOf(Triple(s, e, false))
                else -> listOf(Triple(s, today.minusDays(1), true), Triple(today, e, false))
            }
            pieces.forEach { (ps, pe, past) ->
                // Bords arrondis seulement aux vraies extrémités de l'événement.
                val leftOpen = span.start < ps
                val rightOpen = span.end > pe
                val startCol = (ps.toEpochDay() - weekStart.toEpochDay()).toInt()
                val endCol = (pe.toEpochDay() - weekStart.toEpochDay()).toInt()
                val alpha = when {
                    past -> PAST_ALPHA
                    selected in ps..pe -> 1f
                    else -> 0.75f
                }
                Box(
                    Modifier
                        .offset(x = cellW * startCol + (if (leftOpen) 0.dp else 5.dp), y = cellH - 8.dp - (laneIndex * 5).dp)
                        .width(cellW * (endCol - startCol + 1) - (if (leftOpen) 0.dp else 5.dp) - (if (rightOpen) 0.dp else 5.dp))
                        .height(3.dp)
                        .clip(RoundedCornerShape(topStart = if (leftOpen) 0.dp else 2.dp, bottomStart = if (leftOpen) 0.dp else 2.dp,
                            topEnd = if (rightOpen) 0.dp else 2.dp, bottomEnd = if (rightOpen) 0.dp else 2.dp))
                        .background(color.copy(alpha = alpha))
                )
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate, selected: Boolean, today: Boolean, past: Boolean,
    total: Int, events: List<DayEvent>, hiddenSpans: Int, onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.small
    Column(
        Modifier.fillMaxSize()
            .clip(shape)
            .then(
                when {
                    selected -> Modifier.background(if (past) AgendaTheme.colors.muted else scheme.primary)
                    today -> Modifier.border(1.dp, scheme.primary, shape)
                    // Jours passés : pas de fond, même s'il y avait des événements.
                    total > 0 && !past -> Modifier.background(scheme.surfaceContainer)
                    else -> Modifier
                }
            )
            .clickable(onClick = onClick)
            .semantics {
                this.selected = selected
                contentDescription = Formatters.dayLong(date) + if (total > 0) ", $total événement(s)" else ""
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        Spacer(Modifier.height(5.dp))
        Text(
            date.dayOfMonth.toString(),
            style = Mono.label,
            fontWeight = if (today || selected) FontWeight.Bold else FontWeight.Normal,
            color = when {
                selected -> scheme.onPrimary
                past -> AgendaTheme.colors.muted.copy(alpha = 0.45f)
                else -> scheme.onSurface
            },
        )
        Spacer(Modifier.height(1.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.height(10.dp),
        ) {
            events.map { it.event.kind }.distinct().take(3).forEach { kind ->
                KindDot(
                    when {
                        selected -> scheme.onPrimary
                        past -> kindColor(kind).copy(alpha = PAST_ALPHA)
                        else -> kindColor(kind)
                    },
                    5,
                )
            }
            // Événements sur plusieurs jours qui ne tiennent pas dans les barres.
            if (hiddenSpans > 0) {
                Text(
                    "+$hiddenSpans",
                    style = Mono.tag.copy(fontSize = 9.sp, lineHeight = 10.sp),
                    color = when {
                        selected -> scheme.onPrimary
                        past -> AgendaTheme.colors.muted.copy(alpha = 0.6f)
                        else -> scheme.primary
                    },
                )
            }
        }
    }
}

/**
 * Sélecteur classique mois/année : l'année se change avec les flèches, puis on touche un mois.
 * Les mois hors de la plage consultable sont grisés.
 */
@Composable
private fun MonthPickerDialog(current: YearMonth, min: YearMonth, max: YearMonth, onPick: (YearMonth) -> Unit, onDismiss: () -> Unit) {
    var year by remember { mutableIntStateOf(current.year) }
    val today = YearMonth.now()
    val locale = java.util.Locale.FRANCE
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = { onPick(today) }) { Text("Aujourd'hui") }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Annuler") }
        },
        title = {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { year-- }, enabled = year > min.year) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Année précédente")
                }
                Text(year.toString(), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                IconButton(onClick = { year++ }, enabled = year < max.year) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Année suivante")
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (row in 0 until 4) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (col in 0 until 3) {
                            val ym = YearMonth.of(year, row * 3 + col + 1)
                            val enabled = ym in min..max
                            val chosen = ym == current
                            val label = ym.month.getDisplayName(java.time.format.TextStyle.SHORT_STANDALONE, locale)
                                .replaceFirstChar { it.titlecase(locale) }
                            val shape = MaterialTheme.shapes.small
                            Box(
                                Modifier.weight(1f).height(44.dp).clip(shape)
                                    .then(if (chosen) Modifier.background(MaterialTheme.colorScheme.primary) else Modifier)
                                    .then(if (ym == today && !chosen) Modifier.border(1.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
                                    .clickable(enabled = enabled) { onPick(ym) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = when {
                                        chosen -> MaterialTheme.colorScheme.onPrimary
                                        enabled -> MaterialTheme.colorScheme.onSurface
                                        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        },
    )
}
