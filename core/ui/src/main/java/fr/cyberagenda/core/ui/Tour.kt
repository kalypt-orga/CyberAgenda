package fr.cyberagenda.core.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Visite guidée : un calque assombrit l'écran et éclaire l'élément présenté, avec une courte
 * explication. Les éléments s'enregistrent avec [Modifier.tourTarget] ; une étape dont
 * l'élément n'est pas affiché est présentée au centre, sans projecteur.
 */
class TourTargets {
    internal val bounds = mutableStateMapOf<String, Rect>()
}

val LocalTourTargets = staticCompositionLocalOf<TourTargets?> { null }

/** Marque cet élément comme cible possible de la visite guidée. */
fun Modifier.tourTarget(key: String): Modifier = composed {
    val targets = LocalTourTargets.current ?: return@composed this
    DisposableEffect(key) { onDispose { targets.bounds.remove(key) } }
    onGloballyPositioned { targets.bounds[key] = it.boundsInRoot() }
}

data class TourStep(val target: String?, val title: String, val text: String)

@Composable
fun TourOverlay(
    targets: TourTargets,
    steps: List<TourStep>,
    step: Int?,
    onStep: (Int) -> Unit,
    onFinish: () -> Unit,
) {
    AnimatedVisibility(visible = step != null, enter = fadeIn(tween(250)), exit = fadeOut(tween(200))) {
        val index = (step ?: 0).coerceIn(0, steps.lastIndex)
        val current = steps[index]
        BackHandler(onBack = onFinish) // « Retour » = passer la visite

        val density = LocalDensity.current
        val pad = with(density) { 6.dp.toPx() }
        val hole = current.target?.let { targets.bounds[it] }?.inflate(pad)
        // Le projecteur glisse d'un élément à l'autre.
        val left by animateFloatAsState(hole?.left ?: 0f, tween(300), label = "l")
        val top by animateFloatAsState(hole?.top ?: 0f, tween(300), label = "t")
        val right by animateFloatAsState(hole?.right ?: 0f, tween(300), label = "r")
        val bottom by animateFloatAsState(hole?.bottom ?: 0f, tween(300), label = "b")
        val holeAlpha by animateFloatAsState(if (hole != null) 1f else 0f, tween(200), label = "a")

        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                // Le calque bloque les touches : on ne déclenche rien par erreur sous la visite.
                .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } } },
        ) {
            Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
                drawRect(Color.Black.copy(alpha = 0.72f))
                if (holeAlpha > 0f) {
                    drawRoundRect(
                        color = Color.Black.copy(alpha = holeAlpha),
                        topLeft = Offset(left, top),
                        size = Size(right - left, bottom - top),
                        cornerRadius = CornerRadius(12.dp.toPx()),
                        blendMode = BlendMode.Clear,
                    )
                }
            }

            val screenH = with(density) { maxHeight.toPx() }
            val below = hole != null && hole.center.y < screenH / 2
            val align = when {
                hole == null -> Alignment.Center
                below -> Alignment.TopCenter
                else -> Alignment.BottomCenter
            }
            val gap = with(density) {
                when {
                    hole == null -> 0.dp
                    below -> (hole.bottom + 12.dp.toPx()).toDp()
                    else -> (screenH - hole.top + 12.dp.toPx()).toDp()
                }
            }
            Box(
                Modifier.fillMaxSize().padding(
                    start = 16.dp, end = 16.dp,
                    top = if (below) gap else 0.dp,
                    bottom = if (hole != null && !below) gap else 0.dp,
                ),
                contentAlignment = align,
            ) {
                TourCard(current, index, steps.size, onNext = {
                    if (index == steps.lastIndex) onFinish() else onStep(index + 1)
                }, onSkip = onFinish)
            }
        }
    }
}

@Composable
private fun TourCard(step: TourStep, index: Int, count: Int, onNext: () -> Unit, onSkip: () -> Unit) {
    val last = index == count - 1
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 20.dp, end = 12.dp, top = 18.dp, bottom = 8.dp)) {
            Text("${index + 1} / $count", style = Mono.tag, color = MaterialTheme.colorScheme.primary)
            Text(
                step.title, style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 4.dp, end = 8.dp).semantics { liveRegion = LiveRegionMode.Polite },
            )
            Text(
                step.text, style = MaterialTheme.typography.bodyMedium, color = AgendaTheme.colors.muted,
                modifier = Modifier.padding(top = 6.dp, end = 8.dp),
            )
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                if (!last) TextButton(onClick = onSkip) { Text("Passer") }
                Spacer(Modifier.weight(1f, fill = false))
                Button(onClick = onNext) { Text(if (last) "C'est parti" else "Suivant") }
            }
        }
    }
}

/** Contenu de la visite. Les clés correspondent aux [Modifier.tourTarget] posés dans les écrans. */
object Tour {
    const val SEARCH = "search"
    const val FILTERS = "filters"
    const val LEGEND = "legend"
    const val PROPOSE = "propose"
    const val TAB_CALENDAR = "tab_calendar"
    const val TAB_MAP = "tab_map"
    const val TAB_SETTINGS = "tab_settings"

    val steps = listOf(
        TourStep(null, "Bienvenue sur CyberAgenda", "Les CTF, conférences et ateliers cyber en France, au même endroit. Petit tour en 30 secondes ?"),
        TourStep(SEARCH, "Rechercher", "Par titre, ville ou organisateur. Tirez la liste vers le bas pour l'actualiser."),
        TourStep(FILTERS, "Filtrer", "Format, type, niveau débutant, favoris… Le filtre s'applique à l'agenda, au calendrier et à la carte."),
        TourStep(LEGEND, "Comprendre les couleurs", "Chaque type d'événement a sa couleur et son pictogramme : la légende les rappelle."),
        TourStep(PROPOSE, "Proposer un événement", "Un événement manque ? Proposez-le en quelques champs."),
        TourStep(TAB_CALENDAR, "Calendrier", "Le mois en un coup d'œil. Balayez pour changer de mois."),
        TourStep(TAB_MAP, "Carte", "Les événements près de chez vous : cherchez une ville et choisissez un rayon."),
        TourStep(TAB_SETTINGS, "Paramètres", "Thème clair ou sombre, rappels pour vos favoris, choix des sources. Vous pourrez y revoir cette visite."),
    )
}
