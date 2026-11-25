package fr.cyberagenda.feature.settings

import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import fr.cyberagenda.core.ui.LocalThemeReveal
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.text.font.FontStyle
import android.Manifest
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.layout.FlowRow
import fr.cyberagenda.core.ui.cardBorder
import fr.cyberagenda.core.ui.cardContainerColor
import fr.cyberagenda.core.ui.themedCard
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import fr.cyberagenda.core.ui.scrolledFromTop
import fr.cyberagenda.core.ui.ScrollDivider
import androidx.compose.foundation.lazy.rememberLazyListState
import fr.cyberagenda.core.ui.overscrollIfScrollable
import fr.cyberagenda.core.ui.FilterObscuredTouches
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.cyberagenda.core.model.ReminderMode
import fr.cyberagenda.core.model.SourceState
import fr.cyberagenda.core.model.ThemeMode
import fr.cyberagenda.core.ui.AgendaTheme
import fr.cyberagenda.core.ui.Formatters
import fr.cyberagenda.core.ui.ExternalApps
import android.widget.Toast
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import fr.cyberagenda.core.ui.LegendSheet
import fr.cyberagenda.core.ui.Mono
import fr.cyberagenda.core.ui.ScreenHeader
import fr.cyberagenda.core.ui.SectionHeader
import fr.cyberagenda.core.ui.Tag
import fr.cyberagenda.core.ui.kindColor

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SettingsRoute(
    onPropose: () -> Unit,
    onGlossary: () -> Unit,
    onAbout: () -> Unit,
    onReplayTour: () -> Unit,
    versionName: String,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var permissionDenied by remember { mutableStateOf(false) }
    var pendingReminder by remember { mutableStateOf<ReminderMode?>(null) }

    // Permission de notification demandée seulement quand on active les rappels (Android 13+).
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val mode = pendingReminder
        pendingReminder = null
        if (granted && mode != null) viewModel.setReminder(mode) else permissionDenied = !granted
    }
    fun chooseReminder(mode: ReminderMode) {
        permissionDenied = false
        val needsPermission = mode != ReminderMode.OFF && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            pendingReminder = mode
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.setReminder(mode)
        }
    }

    val active = state.sources.count { it.enabled }
    val available = state.sources.count { it.meta.available }
    // En-tête fixe (comme l'agenda) : seule la liste défile.
    Column(Modifier.fillMaxSize()) {
        // Easter egg : 7 appuis sur la version (comme le numéro de build d'Android) débloquent le
        // thème « Terminal ». Compte à rebours à partir du 4e appui ; les appuis espacés de plus
        // de 2 s repartent de zéro.
        var taps by remember { mutableIntStateOf(0) }
        var lastTap by remember { mutableLongStateOf(0L) }
        var toast by remember { mutableStateOf<Toast?>(null) }
        fun say(text: String) { toast?.cancel(); toast = Toast.makeText(context, text, Toast.LENGTH_SHORT).also { it.show() } }
        val reveal = LocalThemeReveal.current
        val haptics = LocalHapticFeedback.current
        var headerBounds by remember { mutableStateOf(Rect.Zero) }
        ScreenHeader(
            "paramètres", "version $versionName",
            modifier = Modifier.onGloballyPositioned { headerBounds = it.boundsInWindow() }.clickable(interactionSource = null, indication = null, onClickLabel = "Version de l'application") {
                val now = System.currentTimeMillis()
                taps = if (now - lastTap > 2_000) 1 else taps + 1
                lastTap = now
                when {
                    state.terminalUnlocked -> if (taps >= 3) say("Thème secret déjà débloqué ✦")
                    taps >= SECRET_TAPS -> {
                        taps = 0
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        // Le nouveau thème s'ouvre en cercle depuis la ligne « version ».
                        reveal.start(Offset(headerBounds.left + headerBounds.width * 0.25f, headerBounds.bottom - headerBounds.height * 0.3f))
                        viewModel.unlockTerminal()
                        say("✦ Thème secret débloqué !")
                    }
                    taps >= 4 -> say("Encore ${SECRET_TAPS - taps} appui${if (SECRET_TAPS - taps > 1) "s" else ""}…")
                }
            },
        )
        val list = rememberLazyListState()
        ScrollDivider(scrolledFromTop(list))
        LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(bottom = 32.dp), overscrollEffect = overscrollIfScrollable(list)) {

            item { SectionHeader("apparence") }
            item {
                Choice(
                    options = listOf(ThemeMode.SYSTEM to "Auto", ThemeMode.LIGHT to "Clair", ThemeMode.DARK to "Sombre"),
                    selected = state.theme, onSelect = viewModel::setTheme,
                )
                Hint("« Auto » suit le réglage du téléphone.")
                // Thèmes secrets (débloqués par 7 appuis sur la version).
                if (state.terminalUnlocked) {
                    Text("✦ thèmes secrets", style = Mono.tag, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp))
                    FlowRow(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        SECRET_THEMES.forEach { (mode, label) ->
                            FilterChip(
                                selected = state.theme == mode, onClick = { viewModel.setTheme(mode) },
                                label = { Text(label, style = Mono.label) },
                                shape = MaterialTheme.shapes.small,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                ),
                            )
                        }
                    }
                    // Indice pour un flag de CTF (noms des thèmes, dans l'ordre, reliés par un tiret).
                    Text(
                        "Six pièces, un seul ordre. Liées d'un trait, elles ouvrent une autre porte.",
                        style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                        color = AgendaTheme.colors.muted,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 6.dp),
                    )
                }
            }

            item { SectionHeader("rappels") }
            item {
                Choice(
                    options = listOf(ReminderMode.OFF to "Aucun", ReminderMode.DAY_BEFORE to "La veille", ReminderMode.HOUR_BEFORE to "1 h avant"),
                    selected = state.reminder, onSelect = ::chooseReminder,
                )
                Hint(
                    if (permissionDenied) "Notifications refusées : autorisez-les dans les réglages Android pour recevoir les rappels."
                    else "Une notification pour chaque événement mis en favori.",
                    error = permissionDenied,
                )
            }

            item {
                val n = state.historyCount
                var confirm by remember { mutableStateOf(false) }
                LinkRow(
                    "Effacer l'historique de recherche",
                    if (n == 0) "Vide : aucune recherche mémorisée" else Formatters.count(n, "recherche mémorisée", "recherches mémorisées"),
                ) {
                    if (n == 0) Toast.makeText(context, "L'historique de recherche est déjà vide", Toast.LENGTH_SHORT).show()
                    else confirm = true
                }
                if (confirm) {
                    AlertDialog(
                        onDismissRequest = { confirm = false },
                        title = { Text("Effacer l'historique ?") },
                        text = {
                            FilterObscuredTouches()
                            Text("${Formatters.count(n, "recherche mémorisée", "recherches mémorisées")} (agenda et carte) ${if (n > 1) "seront supprimées" else "sera supprimée"}.")
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                confirm = false
                                viewModel.clearSearchHistory()
                                Toast.makeText(context, "Historique de recherche effacé", Toast.LENGTH_SHORT).show()
                            }) { Text("Effacer") }
                        },
                        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Annuler") } },
                    )
                }
            }

            item {
                val scope = rememberCoroutineScope()
                LinkRow("Exporter mes favoris (.ics)", "Les importer dans Google Agenda, Outlook, Proton…") {
                    scope.launch {
                        val favorites = viewModel.upcomingFavorites()
                        if (favorites.isEmpty()) {
                            Toast.makeText(context, "Aucun favori à venir pour l'instant", Toast.LENGTH_SHORT).show()
                        } else {
                            ExternalApps.exportIcs(context, favorites, "Mes favoris CyberAgenda")
                        }
                    }
                }
            }

            item {
                SectionHeader("sources", count = state.sources.size)
            }
            item {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "$active / $available actives. Seules des sources vérifiées et intégrées à l'application " +
                            "sont proposées : aucune adresse ne peut être ajoutée à la main.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    if (state.refreshing) {
                        CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = viewModel::refreshAll) {
                            Icon(Icons.Default.Refresh, contentDescription = "Actualiser toutes les sources")
                        }
                    }
                }
            }
            items(state.sources, key = { it.meta.id }) { source ->
                SourceCard(source, Modifier.padding(horizontal = 16.dp, vertical = 5.dp)) { viewModel.setSourceEnabled(source.meta.id, it) }
            }

            item { SectionHeader("contribuer") }
            item {
                Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Un événement manque près de chez vous ? Proposez-le : il sera vérifié avant d'être ajouté à la sélection.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = onPropose, shape = MaterialTheme.shapes.small,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("  Proposer un événement")
                    }
                }
            }

            item { SectionHeader("à propos") }
            item {
                var legend by remember { mutableStateOf(false) }
                LinkRow("Comment lire l'agenda", "Couleurs, pictogrammes et symboles") { legend = true }
                if (legend) LegendSheet { legend = false }
            }
            item { LinkRow("Revoir la visite guidée", "Où trouver la recherche, les filtres, la carte…", onReplayTour) }
            item { LinkRow("Lexique pour débuter", "CTF, flag, writeup, OSINT…", onGlossary) }
            item { LinkRow("Confidentialité et licences", "Ce que fait (et ne fait pas) l'application", onAbout) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> Choice(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        options.forEachIndexed { i, (value, label) ->
            SegmentedButton(
                selected = value == selected,
                onClick = { onSelect(value) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size, MaterialTheme.shapes.small),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = MaterialTheme.colorScheme.primary,
                    activeContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                icon = {},
                label = { Text(label, style = Mono.label) },
            )
        }
    }
}

@Composable
private fun Hint(text: String, error: Boolean = false) {
    Text(
        text, style = MaterialTheme.typography.bodySmall,
        color = if (error) MaterialTheme.colorScheme.error else AgendaTheme.colors.muted,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
    )
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun SourceCard(source: SourceState, modifier: Modifier, onToggle: (Boolean) -> Unit) {
    val colors = AgendaTheme.colors
    val shape = MaterialTheme.shapes.medium
    val secret = colors.secret != null
    Surface(
        // Ruban posé en dernier (modificateur le plus extérieur) : par-dessus les décors du thème.
        modifier = modifier.fillMaxWidth()
            .then(if (source.meta.available) Modifier else Modifier.workTape(shape))
            .then(if (secret) Modifier.themedCard(shape, source.meta.id, MaterialTheme.colorScheme.primary) else Modifier),
        shape = shape,
        color = if (secret) cardContainerColor(null) else MaterialTheme.colorScheme.surfaceContainer,
        border = if (secret) cardBorder(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.outline)
            else BorderStroke(1.dp, if (source.enabled) MaterialTheme.colorScheme.outline else colors.hairline),
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(source.meta.name, style = MaterialTheme.typography.titleMedium)
                    Text(source.meta.homepage.removePrefix("https://"), style = Mono.meta, color = colors.muted)
                }
                // Source en travaux : interrupteur grisé, impossible à activer.
                Switch(
                    checked = source.enabled,
                    onCheckedChange = onToggle,
                    enabled = source.meta.available,
                    colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.semantics { contentDescription = "Activer la source ${source.meta.name}" },
                )
            }
            Text(source.meta.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            // Étiquettes sur plusieurs lignes si besoin (sinon la dernière est coupée).
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                source.meta.kinds.sortedBy { it.ordinal }.forEach { Tag(Formatters.kind(it), kindColor(it)) }
                if (source.meta.verified) Tag("signée", colors.ok)
            }
            if (source.enabled) {
                val error = source.lastError
                val (text, color) = when {
                    error != null -> "✕ ${Formatters.error(error)}" to MaterialTheme.colorScheme.error
                    source.lastSuccessEpochMs != null ->
                        "✓ ${Formatters.count(source.eventCount, "événement")} · ${Formatters.dateTime(source.lastSuccessEpochMs!!)}" to colors.ok
                    else -> "… jamais synchronisée" to colors.muted
                }
                Text(text, style = Mono.meta, color = color)
            }
        }
    }
}

/**
 * Ruban de chantier « EN TRAVAUX » : deux bandes qui se croisent en X sur le contenu, aux
 * couleurs du thème (couleur primaire, hachures et texte dans sa couleur de contraste).
 * La bande descendante part sous la ligne de titre ([clearTop]) : le nom reste visible.
 * Réutilisable sur n'importe quel bloc mis en attente.
 */
@Composable
fun Modifier.workTape(shape: Shape, clearTop: Dp = 60.dp): Modifier {
    val tape = MaterialTheme.colorScheme.primary
    val ink = MaterialTheme.colorScheme.onPrimary
    val measurer = rememberTextMeasurer()
    val label = measurer.measure(
        "EN TRAVAUX  ·  EN TRAVAUX  ·  EN TRAVAUX",
        TextStyle(color = ink, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp),
    )
    return drawWithContent {
        drawContent()
        // Tout est rogné à la forme du bloc (les décors du thème restent dessous).
        val outline = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithContent)) }
        clipPath(outline) { drawTapes(tape, ink, label, clearTop) }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTapes(
    tape: Color, ink: Color, label: androidx.compose.ui.text.TextLayoutResult, clearTop: Dp,
) {
    drawRect(Color.Black.copy(alpha = 0.12f)) // léger voile : le bloc est « mis de côté »
    val band = 22.dp.toPx()
    val top = minOf(clearTop.toPx(), size.height * 0.45f)
    // Bande 1 : sous le titre à gauche → coin bas droit. Bande 2 : bas gauche → coin haut droit.
    val bands = listOf(Offset(0f, top) to Offset(size.width, size.height), Offset(0f, size.height) to Offset(size.width, 0f))
    for ((i, ends) in bands.withIndex()) {
        val (a, b) = ends
        val mid = Offset((a.x + b.x) / 2, (a.y + b.y) / 2)
        val angle = Math.toDegrees(kotlin.math.atan2((b.y - a.y).toDouble(), (b.x - a.x).toDouble())).toFloat()
        val length = (b - a).getDistance() + 2 * band
        rotate(angle, mid) {
            val y = mid.y - band / 2
            val left = mid.x - length / 2
            // Ombre portée, fond, hachures en biais.
            drawRect(Color.Black.copy(alpha = 0.25f), Offset(left, y + 2.dp.toPx()), Size(length, band))
            drawRect(tape, Offset(left, y), Size(length, band))
            clipRect(left, y, left + length, y + band) {
                val step = 18.dp.toPx()
                var x = left - band
                while (x < left + length) {
                    drawPath(Path().apply {
                        moveTo(x, y + band); lineTo(x + step / 2, y + band); lineTo(x + step / 2 + band, y); lineTo(x + band, y); close()
                    }, ink.copy(alpha = 0.85f))
                    x += step
                }
                // Texte sur une plage pleine au milieu, décalé d'une bande à l'autre
                // pour que les mots ne se chevauchent pas au croisement.
                val tw = label.size.width.toFloat(); val th = label.size.height.toFloat()
                val pad = 6.dp.toPx()
                val shift = if (i == 0) tw * 0.2f else -tw * 0.2f
                drawRect(tape, Offset(mid.x - tw / 2 - pad + shift, y + 3.dp.toPx()), Size(tw + 2 * pad, band - 6.dp.toPx()))
                drawText(label, topLeft = Offset(mid.x - tw / 2 + shift, y + (band - th) / 2))
            }
        }
    }
}

@Composable
private fun LinkRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = AgendaTheme.colors.muted)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = AgendaTheme.colors.muted)
    }
}

/** Nombre d'appuis sur la version pour débloquer le thème secret. */
private const val SECRET_TAPS = 7

/** Thèmes secrets proposés une fois débloqués. */
private val SECRET_THEMES = listOf(
    ThemeMode.BRUTAL to "Brut", ThemeMode.NOTEBOOK to "Carnet", ThemeMode.ARCADE to "Arcade",
    ThemeMode.BLUEPRINT to "Plan", ThemeMode.GAZETTE to "Gazette", ThemeMode.ITASHA to "Itasha",
)
