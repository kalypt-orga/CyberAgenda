package fr.cyberagenda.core.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.cyberagenda.core.model.EventKind
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Allure du thème courant (thèmes normaux : valeurs par défaut, rien ne change). */
val AgendaTheme.style: AgendaStyle
    @Composable @ReadOnlyComposable get() = LocalAgendaStyle.current

/** Texture de fond du thème, dessinée derrière tous les écrans. */
@Composable
fun Modifier.themeBackground(): Modifier {
    val style = AgendaTheme.style
    val colors = AgendaTheme.colors
    return when (style.pattern) {
        BackgroundPattern.NONE -> this
        // Papier quadrillé (carreaux bleus) avec marge rouge, comme un cahier.
        BackgroundPattern.GRID_PAPER -> drawBehind {
            val step = 22.dp.toPx()
            val line = Color(0x2E5B8BD9)
            var x = 0f; while (x < size.width) { drawLine(line, Offset(x, 0f), Offset(x, size.height), 1f); x += step }
            var y = 0f; while (y < size.height) { drawLine(line, Offset(0f, y), Offset(size.width, y), 1f); y += step }
            drawLine(Color(0x66E25B4E), Offset(30.dp.toPx(), 0f), Offset(30.dp.toPx(), size.height), 2f)
        }
        // Lignes de balayage d'un écran cathodique.
        BackgroundPattern.SCANLINES -> drawBehind {
            val step = 4.dp.toPx()
            var y = 0f; while (y < size.height) { drawRect(Color(0x22000000), Offset(0f, y), Size(size.width, step / 2)); y += step }
            drawRect(androidx.compose.ui.graphics.Brush.radialGradient(
                listOf(Color.Transparent, Color(0x55000000)), center = center, radius = size.maxDimension * 0.75f,
            ))
        }
        // Plan d'architecte : grille millimétrée fine et grille majeure.
        BackgroundPattern.BLUEPRINT -> drawBehind {
            val minor = 12.dp.toPx()
            var i = 0
            var x = 0f; while (x < size.width) { drawLine(Color.White.copy(alpha = if (i % 5 == 0) 0.16f else 0.06f), Offset(x, 0f), Offset(x, size.height), 1f); x += minor; i++ }
            i = 0
            var y = 0f; while (y < size.height) { drawLine(Color.White.copy(alpha = if (i % 5 == 0) 0.16f else 0.06f), Offset(0f, y), Offset(size.width, y), 1f); y += minor; i++ }
        }
        // Itasha : pétales de sakura dispersés et lignes de vitesse.
        BackgroundPattern.SAKURA -> drawBehind {
            val petal = colors.ctf.copy(alpha = 0.13f)
            val r = 7.dp.toPx()
            for (k in 0 until 22) {
                val px = ((k * 137.5f) % 100f) / 100f * size.width
                val py = ((k * 61.8f + 13f) % 100f) / 100f * size.height
                rotate(k * 37f, Offset(px, py)) { drawSakura(Offset(px, py), r * (0.7f + (k % 3) * 0.25f), petal) }
            }
            val speed = colors.conference.copy(alpha = 0.10f)
            for (k in 0 until 6) {
                val y = size.height * (0.15f + k * 0.14f)
                drawLine(speed, Offset(size.width * 0.55f, y), Offset(size.width, y - 40f), 3f)
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSakura(c: Offset, r: Float, color: Color) {
    for (p in 0 until 5) {
        val a = Math.toRadians(p * 72.0)
        drawCircle(color, r * 0.55f, Offset(c.x + (cos(a) * r * 0.6f).toFloat(), c.y + (sin(a) * r * 0.6f).toFloat()))
    }
    drawCircle(color.copy(alpha = (color.alpha * 1.5f).coerceAtMost(1f)), r * 0.22f, c)
}

/** Fond d'une carte d'événement selon le thème (post-it, transparent ou surface normale). */
@Composable
@ReadOnlyComposable
fun cardContainerColor(kind: EventKind?): Color {
    val style = AgendaTheme.style
    return when {
        style.postIt && kind != null -> style.postItColors[kind] ?: MaterialTheme.colorScheme.surfaceContainer
        style.transparentCards -> MaterialTheme.colorScheme.background.copy(alpha = 0.55f)
        else -> MaterialTheme.colorScheme.surfaceContainer
    }
}

/** Contour d'une carte selon le thème ; null = aucun contour (dessiné autrement ou absent). */
@Composable
@ReadOnlyComposable
fun cardBorder(kindColor: Color, default: Color = AgendaTheme.colors.hairline): androidx.compose.foundation.BorderStroke? {
    val style = AgendaTheme.style
    if (style.cardBorder == 0.dp || style.dashedBorder || style.rules) return null
    val color = when {
        style.kindBorder -> kindColor
        style.cardBorderColor != null -> style.cardBorderColor
        else -> default
    }
    return androidx.compose.foundation.BorderStroke(style.cardBorder, color)
}

/**
 * Décor d'une carte (à poser sur la carte elle-même) : inclinaison, ombre pleine décalée,
 * pointillés et repères de coupe, filets de journal, scotch, bandes de course.
 * [seed] varie l'inclinaison d'une carte à l'autre (post-it).
 */
@Composable
fun Modifier.themedCard(shape: Shape, seed: String, accent: Color, shadow: Boolean = true): Modifier {
    val style = AgendaTheme.style
    val ink = MaterialTheme.colorScheme.onSurface
    var m = this
    if (style.tilt) {
        val angle = ((abs(seed.hashCode()) % 7) - 3) * 0.35f // entre -1° et +1°
        m = m.graphicsLayer { rotationZ = angle }
    }
    if (style.hardShadow > 0.dp && shadow) {
        m = m.drawBehind {
            val o = style.hardShadow.toPx()
            translate(o, o) { drawOutline(shape.createOutline(size, layoutDirection, this), style.shadowColor) }
        }
    }
    if (style.dashedBorder || style.cropMarks || style.rules || style.tape || style.racingStripes) {
        m = m.drawWithContent {
            drawContent()
            val line = style.cardBorderColor ?: ink
            if (style.dashedBorder) {
                drawRect(line, style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 7f))))
            }
            if (style.cropMarks) {
                val l = 10.dp.toPx(); val o = 4.dp.toPx(); val w = 1.5.dp.toPx()
                listOf(Offset(-o, -o) to Pair(1, 1), Offset(size.width + o, -o) to Pair(-1, 1),
                    Offset(-o, size.height + o) to Pair(1, -1), Offset(size.width + o, size.height + o) to Pair(-1, -1)).forEach { (p, d) ->
                    drawLine(line, p, Offset(p.x + d.first * l, p.y), w)
                    drawLine(line, p, Offset(p.x, p.y + d.second * l), w)
                }
            }
            if (style.rules) {
                drawRect(ink, Offset.Zero, Size(size.width, 2.5.dp.toPx()))
                drawRect(ink.copy(alpha = 0.5f), Offset(0f, size.height - 0.6.dp.toPx()), Size(size.width, 0.6.dp.toPx()))
            }
            if (style.racingStripes) {
                // Deux bandes diagonales dans le coin bas-droit, comme sur une voiture de course.
                val s = 34.dp.toPx(); val w = 7.dp.toPx()
                for ((i, c) in listOf(accent, accent.copy(alpha = 0.45f)).withIndex()) {
                    val off = i * (w + 4.dp.toPx())
                    val path = Path().apply {
                        moveTo(size.width - s - off, size.height); lineTo(size.width - s - off + w, size.height)
                        lineTo(size.width, size.height - s - off + w); lineTo(size.width, size.height - s - off); close()
                    }
                    drawPath(path, c)
                }
            }
            if (style.tape) {
                // Bout de scotch translucide à cheval sur le haut de la carte.
                val tw = 58.dp.toPx(); val th = 16.dp.toPx()
                rotate(-4f + (abs(seed.hashCode()) % 9), Offset(size.width / 2, 0f)) {
                    drawRoundRect(Color(0x99F3E7C4), Offset(size.width / 2 - tw / 2, -th / 2), Size(tw, th), CornerRadius(2f))
                }
            }
        }
    }
    return m
}

/** Style d'une étiquette selon le thème. */
internal fun tagTextStyle(base: TextStyle, tags: TagStyle): TextStyle = when (tags) {
    TagStyle.PIXEL -> base.copy(fontFamily = androidx.compose.ui.text.font.FontFamily(androidx.compose.ui.text.font.Font(R.font.press_start_2p)), fontSize = 7.sp, letterSpacing = 0.sp)
    TagStyle.THICK -> base.copy(fontWeight = FontWeight.Bold)
    TagStyle.STAMP -> base.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
    TagStyle.SMALL_CAPS -> base.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    TagStyle.STICKER -> base.copy(fontWeight = FontWeight.Bold)
    else -> base
}

/**
 * Petite animation quand on ajoute un favori (« 1UP » en arcade, « ✧ kawaii! » en itasha) :
 * le texte s'envole et s'efface. [trigger] change à chaque ajout.
 */
@Composable
fun FavoriteBurstEffect(trigger: Int, modifier: Modifier = Modifier) {
    val burst = AgendaTheme.style.favoriteBurst
    if (burst == FavoriteBurst.NONE || trigger == 0) return
    val rise = remember { Animatable(0f) }
    LaunchedEffect(trigger) {
        rise.snapTo(0f)
        rise.animateTo(1f, tween(800))
    }
    if (rise.value >= 1f) return
    val (text, color) = when (burst) {
        FavoriteBurst.ONE_UP -> "1UP" to Color(0xFF00E436)
        else -> "✧ kawaii!" to MaterialTheme.colorScheme.primary
    }
    Box(modifier) {
        Text(
            text,
            style = if (burst == FavoriteBurst.ONE_UP) tagTextStyle(Mono.tag, TagStyle.PIXEL).copy(fontSize = 10.sp)
            else Mono.tag.copy(fontWeight = FontWeight.Bold, fontSize = 12.sp),
            color = color,
            modifier = Modifier.offset(y = (-36 * rise.value).dp).alpha(1f - rise.value),
        )
    }
}
