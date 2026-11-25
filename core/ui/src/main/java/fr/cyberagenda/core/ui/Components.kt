package fr.cyberagenda.core.ui

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.cyberagenda.core.model.Event
import fr.cyberagenda.core.model.EventFormat

/** En-tête d'écran : nom en chasse fixe + curseur ambre, sous-titre discret. */
@Composable
fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    startPadding: androidx.compose.ui.unit.Dp = 20.dp,
    trailing: @Composable () -> Unit = {},
) {
    Row(modifier.fillMaxWidth().padding(start = startPadding, end = 8.dp, top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            val style = AgendaTheme.style
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    asciiIf(style.asciiOnly, if (style.titleUppercase) title.uppercase(Formatters.locale) else title),
                    style = Mono.brand.copy(
                        fontFamily = style.titleFont, fontWeight = style.titleWeight,
                        fontSize = style.titleSize, letterSpacing = style.titleLetterSpacing,
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                )
                TitleCursor()
            }
            if (style.brandTagline != null && title == "cyberagenda") {
                Text(style.brandTagline, style = Mono.meta, color = MaterialTheme.colorScheme.primary)
            }
            if (subtitle != null) {
                Text(subtitle, style = Mono.meta, color = AgendaTheme.colors.muted)
            }
        }
        trailing()
    }
}

/** Séparateur de section : « NOVEMBRE 2026 ───────── ». */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, count: Int? = null) {
    Row(modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(text.uppercase(Formatters.locale), style = Mono.tag, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (count != null) Text("  $count", style = Mono.tag, color = MaterialTheme.colorScheme.primary)
        HorizontalDivider(Modifier.padding(start = 12.dp).weight(1f), color = AgendaTheme.colors.hairline)
    }
}

/** Petite étiquette en chasse fixe, encadrée d'un filet. */
@Composable
fun Tag(text: String, color: Color = AgendaTheme.colors.muted, filled: Boolean = false) {
    val tags = AgendaTheme.style.tags
    val shape = MaterialTheme.shapes.extraSmall
    val frame = when {
        filled -> Modifier.clip(shape).background(color)
        tags == TagStyle.THICK -> Modifier.border(2.dp, MaterialTheme.colorScheme.outline, shape).background(color.copy(alpha = 0.18f), shape)
        // Coup de tampon : double contour, légèrement de travers.
        tags == TagStyle.STAMP -> Modifier.graphicsLayer { rotationZ = -3f }
            .border(1.5.dp, color.copy(alpha = 0.8f), shape).padding(1.5.dp).border(0.8.dp, color.copy(alpha = 0.6f), shape)
        tags == TagStyle.DASHED -> Modifier.drawBehind {
            drawRect(color, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx(),
                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6f, 4f))))
        }
        tags == TagStyle.SMALL_CAPS -> Modifier
        // Autocollant : fond blanc, gros contour coloré, de travers.
        tags == TagStyle.STICKER -> Modifier.graphicsLayer { rotationZ = if (text.length % 2 == 0) -2.5f else 2f }
            .border(2.dp, color, shape).background(Color.White, shape)
        else -> Modifier.clip(shape).border(1.dp, color.copy(alpha = 0.45f), shape)
    }
    Text(
        asciiIf(tags == TagStyle.PIXEL, text.uppercase(Formatters.locale)),
        style = tagTextStyle(Mono.tag, tags),
        color = if (filled) MaterialTheme.colorScheme.background else color,
        maxLines = 1,
        modifier = frame.padding(horizontal = if (tags == TagStyle.SMALL_CAPS) 0.dp else 6.dp, vertical = 2.dp),
    )
}

@Composable
fun KindDot(color: Color, size: Int = 6) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(color))
}

/**
 * Carte d'événement façon « billet » : talon daté à gauche, filet coloré selon le type.
 */
@Composable
fun EventCard(
    event: Event,
    sourceName: String,
    favorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
    /** Information contextuelle ajoutée à la ligne de métadonnées (ex. « 12 km »). */
    extra: String? = null,
    /** Nombre d'autres dates pour un événement récurrent (0 = aucune). */
    otherDates: Int = 0,
) {
    val accent = kindColor(event.kind)
    val colors = AgendaTheme.colors
    val ended = event.endsAtEpochMs < System.currentTimeMillis()
    val shape = MaterialTheme.shapes.medium
    var burst by remember { mutableIntStateOf(0) }
    Surface(
        onClick = onClick,
        // Événement terminé : estompé, pour le distinguer d'un coup d'œil.
        modifier = modifier.fillMaxWidth().themedCard(shape, event.id, accent).alpha(if (ended) 0.6f else 1f),
        shape = shape,
        color = cardContainerColor(event.kind),
        border = cardBorder(accent),
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(3.dp).fillMaxHeight().background(accent))
            DateStub(event.startsAtEpochMs, Formatters.displayEnd(event.startsAtEpochMs, event.endsAtEpochMs), accent)
            // Pointillés du talon
            Column(Modifier.fillMaxHeight().padding(vertical = 8.dp), verticalArrangement = Arrangement.SpaceEvenly) {
                repeat(6) { Box(Modifier.size(width = 1.dp, height = 4.dp).background(colors.hairline)) }
            }
            Column(Modifier.weight(1f).padding(start = 12.dp, top = 12.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    KindIcon(event.kind, 15.dp)
                    Text(Formatters.kind(event.kind).uppercase(Formatters.locale), style = Mono.tag, color = accent)
                    Text("·", style = Mono.tag, color = colors.muted)
                    Text(Formatters.format(event.format).uppercase(Formatters.locale), style = Mono.tag, color = colors.muted)
                }
                Text(event.title, style = CardTitle, maxLines = 2, overflow = TextOverflow.Ellipsis)
                // Une journée : plage horaire. Plusieurs jours : les dates sont déjà dans le talon,
                // on ne les répète pas ; il ne reste que le lieu (ligne masquée si rien à dire).
                val place = event.placeLabel ?: event.location?.let(::shortPlace)
                val multiDay = Formatters.isMultiDay(event.startsAtEpochMs, event.endsAtEpochMs)
                val meta = listOfNotNull(
                    if (multiDay) null else Formatters.shortSpan(event.startsAtEpochMs, event.endsAtEpochMs),
                    extra,
                    place,
                ).joinToString("  ·  ")
                if (meta.isNotEmpty()) {
                    Text(meta, style = Mono.meta, color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (ended) Tag("Terminé", colors.muted)
                    if (event.beginnerFriendly) Tag("Débutants bienvenus", colors.ok)
                    if (event.price == "Gratuit") Tag("Gratuit", colors.ok)
                    if (otherDates > 0) Tag("+$otherDates dates", MaterialTheme.colorScheme.primary)
                    Text("via $sourceName", style = Mono.tag.copy(fontSize = 10.sp), color = colors.muted.copy(alpha = 0.8f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                }
            }
            Box {
                IconButton(onClick = { if (!favorite) burst++; onToggleFavorite() }, modifier = Modifier.semantics {
                    contentDescription = if (favorite) "Retirer des favoris" else "Ajouter aux favoris"
                }) {
                    Icon(
                        if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = null,
                        tint = if (favorite) MaterialTheme.colorScheme.primary else colors.muted,
                        modifier = Modifier.size(20.dp),
                    )
                }
                // « 1UP » / « ✧ kawaii! » selon le thème secret.
                FavoriteBurstEffect(burst, Modifier.align(Alignment.Center))
            }
        }
    }
}

/**
 * Talon daté. Un événement sur plusieurs jours affiche sa plage (« 26–27 NOV »,
 * « 30 NOV→DÉC ») au lieu d'apparaître une fois par jour.
 */
@Composable
private fun DateStub(startMs: Long, endMs: Long, accent: Color) {
    val colors = AgendaTheme.colors
    // Talon en aplat plein (thème Brut) : textes à l'encre noire, lisibles sur la couleur.
    val solid = AgendaTheme.style.solidStub
    val faint = if (solid) Color(0xFF111111) else colors.muted
    val strong = if (solid) Color(0xFF111111) else MaterialTheme.colorScheme.onSurface
    val multi = Formatters.isMultiDay(startMs, endMs)
    val sameMonth = Formatters.monthShort(startMs) == Formatters.monthShort(endMs)
    Column(
        // Talon légèrement teinté de la couleur du type : les cartes se distinguent d'un coup d'œil.
        Modifier.width(62.dp).fillMaxHeight().background(
            if (AgendaTheme.style.solidStub) accent else accent.copy(alpha = if (AgendaTheme.colors.isDark) 0.12f else 0.09f)
        )
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            if (multi) "${Formatters.weekdayShort(startMs)}→" else Formatters.weekdayShort(startMs),
            style = Mono.tag, color = faint,
        )
        Text(
            if (multi && sameMonth) "${Formatters.dayNumber(startMs)}–${Formatters.dayNumber(endMs)}" else Formatters.dayNumber(startMs),
            style = if (multi && sameMonth) MaterialTheme.typography.titleLarge.copy(fontSize = 17.sp) else MaterialTheme.typography.headlineSmall,
            color = strong, maxLines = 1,
        )
        Text(
            if (multi && !sameMonth) "${Formatters.monthShort(startMs)}→${Formatters.monthShort(endMs)}" else Formatters.monthShort(startMs),
            style = Mono.tag, color = faint, maxLines = 1,
        )
    }
}

/** Titre des miniatures d'événement : demi-gras, pour ressortir d'un coup d'œil. */
val CardTitle: TextStyle
    @Composable get() {
        val style = AgendaTheme.style
        val base = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
        return if (style.eventTitleFont == null) base
        else base.copy(fontFamily = style.eventTitleFont, fontWeight = FontWeight.Bold, fontSize = style.eventTitleSize ?: base.fontSize, lineHeight = (style.eventTitleSize ?: base.fontSize) * 1.15f)
    }

/** « Couvent des Jacobins, Rennes (35) » → « Rennes (35) » : on garde la partie la plus utile. */
fun shortPlace(location: String): String = location.split(',').last().trim().ifEmpty { location }

/** Message d'état vide, sobre. */
@Composable
fun EmptyMessage(title: String, body: String, modifier: Modifier = Modifier, action: @Composable () -> Unit = {}) {
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(AgendaTheme.style.emptyFace, style = Mono.label, color = AgendaTheme.colors.muted)
        Spacer(Modifier.height(4.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        action()
    }
}

/** Curseur placé après le titre d'écran, selon le thème. */
@Composable
private fun TitleCursor() {
    val style = AgendaTheme.style
    val primary = MaterialTheme.colorScheme.primary
    val bar = Modifier.padding(start = 3.dp, bottom = 4.dp).size(width = 9.dp, height = 3.dp)
    when (style.cursor) {
        TitleCursorStyle.BAR -> Box(bar.background(primary))
        TitleCursorStyle.BLOCK -> Box(Modifier.padding(start = 6.dp, bottom = 5.dp).size(16.dp).background(MaterialTheme.colorScheme.onBackground))
        TitleCursorStyle.BLINK_BLOCK -> {
            val t = rememberInfiniteTransition(label = "curseur")
            val on by t.animateFloat(1f, 0f, infiniteRepeatable(tween(560, easing = { if (it < 0.5f) 0f else 1f })), label = "c")
            Box(Modifier.padding(start = 6.dp, bottom = 2.dp).size(width = 11.dp, height = 15.dp).alpha(on).background(primary))
        }
        // Trait de crayon ondulé, comme un soulignement à la main.
        TitleCursorStyle.SQUIGGLE -> androidx.compose.foundation.Canvas(Modifier.padding(start = 4.dp, bottom = 8.dp).size(width = 30.dp, height = 8.dp)) {
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(0f, size.height / 2)
                var x = 0f; val step = size.width / 6
                while (x < size.width) { quadraticTo(x + step / 2, if ((x / step).toInt() % 2 == 0) 0f else size.height, x + step, size.height / 2); x += step }
            }
            drawPath(path, primary, style = androidx.compose.ui.graphics.drawscope.Stroke(2.2.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
        }
        TitleCursorStyle.CROSSHAIR -> Text(" ⌖", style = Mono.brand, color = MaterialTheme.colorScheme.secondary)
        TitleCursorStyle.DOT -> Text(".", style = Mono.brand.copy(fontFamily = style.titleFont, fontWeight = style.titleWeight, fontSize = style.titleSize), color = primary)
        TitleCursorStyle.HEART -> {
            val t = rememberInfiniteTransition(label = "cœur")
            val beat by t.animateFloat(0.85f, 1.15f, infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "b")
            Text(" ♥", style = Mono.brand.copy(fontSize = 20.sp), color = primary, modifier = Modifier.graphicsLayer { scaleX = beat; scaleY = beat })
        }
    }
}

/** Retire les accents si [ascii] (« PARAMÈTRES » → « PARAMETRES ») : la police pixel n'en a pas. */
internal fun asciiIf(ascii: Boolean, text: String): String =
    if (!ascii) text else java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
