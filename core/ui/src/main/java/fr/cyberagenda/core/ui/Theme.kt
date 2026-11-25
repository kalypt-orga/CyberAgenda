package fr.cyberagenda.core.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import fr.cyberagenda.core.model.EventKind

/*
 * Identité visuelle « encre & signal » :
 *  - fond encre (sombre) ou papier (clair), jamais de noir pur ni de blanc pur ;
 *  - un seul accent chaud, l'ambre « signal », pour ce qui est actionnable ;
 *  - une couleur par type d'événement, utilisée en petites touches (filet, libellé) ;
 *  - la police à chasse fixe pour les métadonnées (dates, étiquettes), la sans-serif pour lire.
 * Pas de couleurs dynamiques Material You : l'app garde sa personnalité sur tous les téléphones.
 */

@Immutable
data class AgendaColors(
    val ctf: Color,
    val conference: Color,
    val meetup: Color,
    val workshop: Color,
    val other: Color,
    val ok: Color,
    val muted: Color,
    val hairline: Color,
    val accentSoft: Color,
    val isDark: Boolean,
    /** Thème secret actif (curseur animé des titres), null pour les thèmes normaux. */
    val secret: SecretStyle? = null,
)

private val InkAgenda = AgendaColors(
    ctf = Color(0xFFFF7A6B),
    conference = Color(0xFF7DB8FF),
    meetup = Color(0xFF6FD6A8),
    workshop = Color(0xFFC6A2FF),
    other = Color(0xFF9AA3AF),
    ok = Color(0xFF6FD6A8),
    muted = Color(0xFF8B93A0),
    hairline = Color(0xFF262C35),
    accentSoft = Color(0x33F2A93B),
    isDark = true,
)

private val PaperAgenda = AgendaColors(
    ctf = Color(0xFFC8402F),
    conference = Color(0xFF2763B8),
    meetup = Color(0xFF1C7D55),
    workshop = Color(0xFF7446C2),
    other = Color(0xFF5E6670),
    ok = Color(0xFF1C7D55),
    muted = Color(0xFF5E6670),
    hairline = Color(0xFFE2DDD2),
    accentSoft = Color(0x26B86A00),
    isDark = false,
)

private val Ink = darkColorScheme(
    primary = Color(0xFFF2A93B),
    onPrimary = Color(0xFF1E1404),
    primaryContainer = Color(0xFF3A2A10),
    onPrimaryContainer = Color(0xFFFFD9A0),
    secondary = Color(0xFF6FD6A8),
    onSecondary = Color(0xFF04231A),
    background = Color(0xFF0F1216),
    onBackground = Color(0xFFE8E6E1),
    surface = Color(0xFF0F1216),
    onSurface = Color(0xFFE8E6E1),
    surfaceVariant = Color(0xFF1C2128),
    onSurfaceVariant = Color(0xFF9AA2AE),
    surfaceContainerLowest = Color(0xFF0B0E11),
    surfaceContainerLow = Color(0xFF14181D),
    surfaceContainer = Color(0xFF171B21),
    surfaceContainerHigh = Color(0xFF1E232A),
    surfaceContainerHighest = Color(0xFF252B33),
    outline = Color(0xFF39414C),
    outlineVariant = Color(0xFF262C35),
    error = Color(0xFFFF8A7A),
)

private val Paper = lightColorScheme(
    primary = Color(0xFFB86A00),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFE3BC),
    onPrimaryContainer = Color(0xFF3A2400),
    secondary = Color(0xFF1C7D55),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFF6F4EF),
    onBackground = Color(0xFF1A1D21),
    surface = Color(0xFFF6F4EF),
    onSurface = Color(0xFF1A1D21),
    surfaceVariant = Color(0xFFECE8DF),
    onSurfaceVariant = Color(0xFF5E6670),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFBFAF7),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFF1EEE7),
    surfaceContainerHighest = Color(0xFFE9E5DC),
    outline = Color(0xFFC9C3B6),
    outlineVariant = Color(0xFFE2DDD2),
    error = Color(0xFFB3261E),
)

private val LocalAgendaColors = staticCompositionLocalOf { InkAgenda }

object AgendaTheme {
    val colors: AgendaColors
        @Composable @ReadOnlyComposable get() = LocalAgendaColors.current
}

@Composable
@ReadOnlyComposable
fun kindColor(kind: EventKind): Color = with(AgendaTheme.colors) {
    when (kind) {
        EventKind.CTF -> ctf
        EventKind.CONFERENCE -> conference
        EventKind.MEETUP -> meetup
        EventKind.WORKSHOP -> workshop
        EventKind.OTHER -> other
    }
}

private val AgendaShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(14.dp),
)

@Composable
fun CyberAgendaTheme(darkTheme: Boolean = isSystemInDarkTheme(), secret: SecretStyle? = null, content: @Composable () -> Unit) {
    val theme = secret?.let(::secretTheme)
    CompositionLocalProvider(
        LocalAgendaColors provides (theme?.colors ?: if (darkTheme) InkAgenda else PaperAgenda),
        LocalAgendaStyle provides (theme?.style ?: AgendaStyle()),
    ) {
        MaterialTheme(
            colorScheme = theme?.scheme ?: if (darkTheme) Ink else Paper,
            typography = AgendaTypography,
            shapes = theme?.shapes ?: AgendaShapes,
            content = content,
        )
    }
}
