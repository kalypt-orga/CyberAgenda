package fr.cyberagenda

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Révélation du thème débloqué, en deux temps :
 * 1. « charge » (~0,3 s) : un anneau aux couleurs du nouveau thème pulse sous le doigt ;
 * 2. « ouverture » (~1,1 s) : l'ancien écran figé ([frozen]) s'ouvre en un cercle qui grandit,
 *    bordé d'un liseré coloré et de quelques étincelles qui s'éteignent en s'éloignant.
 * Les touches sont ignorées pendant l'animation ; [onDone] retire la couche.
 * À dessiner DANS le nouveau thème (couleur primaire du thème révélé).
 */
@Composable
internal fun ThemeRevealOverlay(frozen: ImageBitmap, origin: Offset, ready: Boolean, onDone: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    val charge = remember { Animatable(0f) }
    val open = remember { Animatable(0f) }
    LaunchedEffect(ready) {
        if (!ready) return@LaunchedEffect
        charge.animateTo(1f, tween(CHARGE_MS, easing = CubicBezierEasing(0.3f, 0f, 0.2f, 1f)))
        open.animateTo(1f, tween(OPEN_MS, easing = FastOutSlowInEasing))
        onDone()
    }
    Canvas(Modifier.fillMaxSize().pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } } }) {
        val corners = listOf(Offset.Zero, Offset(size.width, 0f), Offset(0f, size.height), Offset(size.width, size.height))
        val max = corners.maxOf { (it - origin).getDistance() }
        val p = open.value
        val radius = p * max
        if (radius > 0f) {
            clipPath(Path().apply { addOval(Rect(origin, radius)) }, ClipOp.Difference) { drawImage(frozen) }
        } else {
            drawImage(frozen)
        }
        // 1. Anneau qui se charge sous le doigt (s'efface dès que le cercle s'ouvre).
        val c = charge.value
        if (c > 0f && p < 0.35f) {
            val fade = 1f - p / 0.35f
            drawCircle(accent.copy(alpha = 0.25f * fade), 26.dp.toPx() * c, origin)
            drawCircle(accent.copy(alpha = 0.9f * fade), 26.dp.toPx() * c, origin, style = Stroke(2.5.dp.toPx()))
        }
        // 2. Liseré et étincelles sur le bord du cercle.
        if (p > 0f && p < 1f) {
            val fade = 1f - p
            drawCircle(accent.copy(alpha = 0.85f * fade), radius, origin, style = Stroke(4.dp.toPx() * fade + 1.dp.toPx()))
            for (k in 0 until SPARKS) {
                val a = Math.toRadians(k * 360.0 / SPARKS + p * 50.0)
                val d = radius * (1f + 0.06f * ((k % 3) - 1)) + 10.dp.toPx() * p
                val at = Offset(origin.x + (cos(a) * d).toFloat(), origin.y + (sin(a) * d).toFloat())
                drawSpark(at, (7.dp.toPx() * fade + 2.dp.toPx()) * (if (k % 2 == 0) 1f else 0.7f), accent.copy(alpha = fade))
            }
        }
    }
}

/** Étoile à quatre branches (✦). */
private fun DrawScope.drawSpark(c: Offset, r: Float, color: androidx.compose.ui.graphics.Color) {
    val i = r * 0.28f
    drawPath(Path().apply {
        moveTo(c.x, c.y - r); lineTo(c.x + i, c.y - i); lineTo(c.x + r, c.y); lineTo(c.x + i, c.y + i)
        lineTo(c.x, c.y + r); lineTo(c.x - i, c.y + i); lineTo(c.x - r, c.y); lineTo(c.x - i, c.y - i); close()
    }, color)
}

private const val CHARGE_MS = 300
private const val OPEN_MS = 1_100
private const val SPARKS = 10
