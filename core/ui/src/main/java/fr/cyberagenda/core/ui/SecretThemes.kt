package fr.cyberagenda.core.ui

import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.cyberagenda.core.model.EventKind

/**
 * Thèmes secrets (7 appuis sur la version dans les paramètres). Chacun est une vraie rupture avec
 * les thèmes normaux : couleurs, mais aussi police des titres, formes, allure des cartes, texture
 * de fond, curseur des titres et petite animation. Le corps des textes reste lisible partout.
 */
enum class SecretStyle(val label: String) {
    BRUTAL("Brut"),
    NOTEBOOK("Carnet"),
    ARCADE("Arcade"),
    BLUEPRINT("Plan"),
    GAZETTE("Gazette"),
    ITASHA("Itasha"),
}

/** Texture dessinée derrière tous les écrans. */
enum class BackgroundPattern { NONE, GRID_PAPER, SCANLINES, BLUEPRINT, SAKURA }

/** Curseur placé après les titres d'écran. */
enum class TitleCursorStyle { BAR, BLOCK, BLINK_BLOCK, SQUIGGLE, CROSSHAIR, DOT, HEART }

/** Allure des étiquettes (« Débutants bienvenus », « Gratuit »…). */
enum class TagStyle { OUTLINE, THICK, STAMP, PIXEL, DASHED, SMALL_CAPS, STICKER }

/** Ce qui se passe quand on ajoute un favori. */
enum class FavoriteBurst { NONE, ONE_UP, KAWAII }

/** Description de l'allure d'un thème : lue par les composants (en-têtes, cartes, étiquettes…). */
@Immutable
data class AgendaStyle(
    val titleFont: FontFamily = JetBrainsMono,
    val titleWeight: FontWeight = FontWeight.Bold,
    val titleSize: TextUnit = 20.sp,
    val titleUppercase: Boolean = false,
    val titleLetterSpacing: TextUnit = (-0.2).sp,
    /** Sans accents (police pixel sans lettres accentuées, comme les vieilles machines). */
    val asciiOnly: Boolean = false,
    /** Sous-titre décoratif ajouté sous le titre de l'agenda (ex. katakana), null = aucun. */
    val brandTagline: String? = null,
    /** Police des titres d'événement (cartes, fiche), null = police normale. */
    val eventTitleFont: FontFamily? = null,
    val eventTitleSize: TextUnit? = null,
    // Cartes
    val cardBorder: Dp = 1.dp,
    /** Couleur du contour, null = filet du thème ; [kindBorder] = couleur de la catégorie. */
    val cardBorderColor: Color? = null,
    val kindBorder: Boolean = false,
    val dashedBorder: Boolean = false,
    val cropMarks: Boolean = false,
    /** Ombre pleine décalée (néo-brutaliste, arcade), 0 = aucune. */
    val hardShadow: Dp = 0.dp,
    val shadowColor: Color = Color.Black,
    /** Cartes légèrement inclinées (post-it). */
    val tilt: Boolean = false,
    /** Bout de scotch en haut de la carte. */
    val tape: Boolean = false,
    /** Fond de carte = couleur pastel de la catégorie (post-it). */
    val postIt: Boolean = false,
    /** Cartes sans fond plein (on voit la texture), séparées par des filets ou des pointillés. */
    val transparentCards: Boolean = false,
    /** Filets de journal (épais en haut, fin en bas) au lieu d'un contour. */
    val rules: Boolean = false,
    /** Bandes de course diagonales dans le coin des cartes. */
    val racingStripes: Boolean = false,
    /** Talon de date en aplat plein de la couleur de la catégorie. */
    val solidStub: Boolean = false,
    // Décors
    val pattern: BackgroundPattern = BackgroundPattern.NONE,
    val cursor: TitleCursorStyle = TitleCursorStyle.BAR,
    val tags: TagStyle = TagStyle.OUTLINE,
    val favoriteBurst: FavoriteBurst = FavoriteBurst.NONE,
    /** Message des listes vides. */
    val emptyFace: String = "¯\\_(ツ)_/¯",
    /** Couleurs de fond « post-it » par catégorie (si [postIt]). */
    val postItColors: Map<EventKind, Color> = emptyMap(),
)

internal val LocalAgendaStyle = staticCompositionLocalOf { AgendaStyle() }

/** Tout ce qu'il faut pour peindre un thème secret. */
internal class SecretTheme(
    val scheme: ColorScheme,
    val colors: AgendaColors,
    val shapes: Shapes,
    val style: AgendaStyle,
)

private val Caveat = FontFamily(Font(R.font.caveat_bold, FontWeight.Bold))
private val Playfair = FontFamily(Font(R.font.playfair_display_extrabold, FontWeight.ExtraBold))
private val PressStart = FontFamily(Font(R.font.press_start_2p, FontWeight.Normal))

private fun sharp(r: Dp) = Shapes(RoundedCornerShape(r), RoundedCornerShape(r), RoundedCornerShape(r), RoundedCornerShape(r))

internal fun secretTheme(style: SecretStyle): SecretTheme = when (style) {

    // Affiche graphique : contours noirs épais, ombres pleines décalées, aplats francs.
    SecretStyle.BRUTAL -> SecretTheme(
        scheme = lightColorScheme(
            primary = Color(0xFFFF5FA2), onPrimary = Color(0xFF111111),
            primaryContainer = Color(0xFFFFD23F), onPrimaryContainer = Color(0xFF111111),
            secondary = Color(0xFF3D5AFE), onSecondary = Color.White,
            background = Color(0xFFFFF1CC), onBackground = Color(0xFF111111),
            surface = Color(0xFFFFF1CC), onSurface = Color(0xFF111111),
            surfaceVariant = Color(0xFFFFE7A6), onSurfaceVariant = Color(0xFF2B2B2B),
            surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFFFFBEF),
            surfaceContainer = Color.White, surfaceContainerHigh = Color(0xFFFFF8E4), surfaceContainerHighest = Color(0xFFFFE9B0),
            outline = Color(0xFF111111), outlineVariant = Color(0xFF111111), error = Color(0xFFD7263D),
        ),
        colors = AgendaColors(
            ctf = Color(0xFFFF5A4E), conference = Color(0xFF3D5AFE), meetup = Color(0xFF00B884),
            workshop = Color(0xFFA259FF), other = Color(0xFFFF9F1C), ok = Color(0xFF008F68),
            muted = Color(0xFF444444), hairline = Color(0xFF111111), accentSoft = Color(0xFFFFD23F),
            isDark = false, secret = style,
        ),
        shapes = sharp(4.dp),
        style = AgendaStyle(
            titleFont = PlexSans, titleWeight = FontWeight.Bold, titleSize = 27.sp, titleUppercase = true,
            cardBorder = 2.5.dp, cardBorderColor = Color(0xFF111111), hardShadow = 4.dp,
            solidStub = true, cursor = TitleCursorStyle.BLOCK, tags = TagStyle.THICK, emptyFace = "■ RIEN ICI ■",
        ),
    )

    // Carnet de bord : papier quadrillé, post-it inclinés avec scotch, titres manuscrits, tampons.
    SecretStyle.NOTEBOOK -> SecretTheme(
        scheme = lightColorScheme(
            primary = Color(0xFF2B4C9A), onPrimary = Color.White,
            primaryContainer = Color(0xFFDCE6F7), onPrimaryContainer = Color(0xFF13285A),
            secondary = Color(0xFFC2452D), onSecondary = Color.White,
            background = Color(0xFFFBF7EE), onBackground = Color(0xFF1F2A44),
            surface = Color(0xFFFBF7EE), onSurface = Color(0xFF1F2A44),
            surfaceVariant = Color(0xFFF1EBDD), onSurfaceVariant = Color(0xFF4A5878),
            surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFFDFBF6),
            surfaceContainer = Color(0xFFFFFDF8), surfaceContainerHigh = Color(0xFFF5F0E4), surfaceContainerHighest = Color(0xFFECE5D4),
            outline = Color(0xFF9FB3D1), outlineVariant = Color(0xFFC9D6EA), error = Color(0xFFC2452D),
        ),
        colors = AgendaColors(
            ctf = Color(0xFFC2452D), conference = Color(0xFF2B4C9A), meetup = Color(0xFF2E7D4F),
            workshop = Color(0xFF7A4BB0), other = Color(0xFF8A6A2E), ok = Color(0xFF2E7D4F),
            muted = Color(0xFF5E6B85), hairline = Color(0xFFC9D6EA), accentSoft = Color(0x332B4C9A),
            isDark = false, secret = style,
        ),
        shapes = Shapes(RoundedCornerShape(3.dp), RoundedCornerShape(3.dp), RoundedCornerShape(2.dp), RoundedCornerShape(6.dp)),
        style = AgendaStyle(
            titleFont = Caveat, titleWeight = FontWeight.Bold, titleSize = 38.sp, titleLetterSpacing = 0.sp,
            eventTitleFont = Caveat, eventTitleSize = 23.sp,
            cardBorder = 0.dp, postIt = true, tilt = true, tape = true,
            pattern = BackgroundPattern.GRID_PAPER, cursor = TitleCursorStyle.SQUIGGLE, tags = TagStyle.STAMP,
            emptyFace = "✎ page blanche",
            postItColors = mapOf(
                EventKind.CTF to Color(0xFFFFD9D3), EventKind.CONFERENCE to Color(0xFFD8E7FF), EventKind.MEETUP to Color(0xFFD9F2DE),
                EventKind.WORKSHOP to Color(0xFFEADFFB), EventKind.OTHER to Color(0xFFFFF1B8),
            ),
        ),
    )

    // Borne d'arcade : nuit, lignes de balayage, coins en escalier, police pixel, « 1UP ».
    SecretStyle.ARCADE -> SecretTheme(
        scheme = darkColorScheme(
            primary = Color(0xFFFFEC27), onPrimary = Color(0xFF1D0D00),
            primaryContainer = Color(0xFF3A2E00), onPrimaryContainer = Color(0xFFFFF6A8),
            secondary = Color(0xFF29ADFF), onSecondary = Color(0xFF001A2B),
            background = Color(0xFF0B0420), onBackground = Color(0xFFFFF1E8),
            surface = Color(0xFF0B0420), onSurface = Color(0xFFFFF1E8),
            surfaceVariant = Color(0xFF1A0C42), onSurfaceVariant = Color(0xFFC2C3D7),
            surfaceContainerLowest = Color(0xFF070216), surfaceContainerLow = Color(0xFF12072E),
            surfaceContainer = Color(0xFF170A3A), surfaceContainerHigh = Color(0xFF1E0E4A), surfaceContainerHighest = Color(0xFF26135A),
            outline = Color(0xFF5B4A9E), outlineVariant = Color(0xFF2A1C5C), error = Color(0xFFFF004D),
        ),
        colors = AgendaColors(
            ctf = Color(0xFFFF004D), conference = Color(0xFF29ADFF), meetup = Color(0xFF00E436),
            workshop = Color(0xFFFF77A8), other = Color(0xFFFFA300), ok = Color(0xFF00E436),
            muted = Color(0xFFA79CC9), hairline = Color(0xFF2A1C5C), accentSoft = Color(0x33FFEC27),
            isDark = true, secret = style,
        ),
        shapes = Shapes(CutCornerShape(2.dp), CutCornerShape(4.dp), CutCornerShape(5.dp), CutCornerShape(6.dp)),
        style = AgendaStyle(
            titleFont = PressStart, titleWeight = FontWeight.Normal, titleSize = 15.sp, titleUppercase = true, titleLetterSpacing = 0.sp, asciiOnly = true,
            cardBorder = 2.dp, kindBorder = true, hardShadow = 4.dp, shadowColor = Color.Black,
            pattern = BackgroundPattern.SCANLINES, cursor = TitleCursorStyle.BLINK_BLOCK, tags = TagStyle.PIXEL,
            favoriteBurst = FavoriteBurst.ONE_UP, emptyFace = "GAME OVER ?  INSERT COIN",
        ),
    )

    // Plan d'architecte : bleu à grille millimétrée, pointillés, repères de coupe, capitales techniques.
    SecretStyle.BLUEPRINT -> SecretTheme(
        scheme = darkColorScheme(
            primary = Color.White, onPrimary = Color(0xFF0E3B6F),
            primaryContainer = Color(0xFF2A5C96), onPrimaryContainer = Color.White,
            secondary = Color(0xFF7FE0FF), onSecondary = Color(0xFF00263A),
            background = Color(0xFF0E3B6F), onBackground = Color(0xFFEAF3FF),
            surface = Color(0xFF0E3B6F), onSurface = Color(0xFFEAF3FF),
            surfaceVariant = Color(0xFF14497F), onSurfaceVariant = Color(0xFFB4D0F0),
            surfaceContainerLowest = Color(0xFF0B3260), surfaceContainerLow = Color(0xFF104272),
            surfaceContainer = Color(0xFF12467F), surfaceContainerHigh = Color(0xFF165091), surfaceContainerHighest = Color(0xFF1B5BA3),
            outline = Color(0xFF8FB8E6), outlineVariant = Color(0xFF3C6EA6), error = Color(0xFFFF9B9B),
        ),
        colors = AgendaColors(
            ctf = Color(0xFFFF9B9B), conference = Color(0xFF8FD8FF), meetup = Color(0xFFA6F5C0),
            workshop = Color(0xFFD8BDFF), other = Color(0xFFFFE59A), ok = Color(0xFFA6F5C0),
            muted = Color(0xFFB4D0F0), hairline = Color(0xFF3C6EA6), accentSoft = Color(0x33FFFFFF),
            isDark = true, secret = style,
        ),
        shapes = sharp(0.dp),
        style = AgendaStyle(
            titleFont = JetBrainsMono, titleWeight = FontWeight.Bold, titleSize = 19.sp, titleUppercase = true, titleLetterSpacing = 4.sp,
            cardBorder = 1.dp, cardBorderColor = Color(0xCCD6E8FF), dashedBorder = true, cropMarks = true, transparentCards = true,
            pattern = BackgroundPattern.BLUEPRINT, cursor = TitleCursorStyle.CROSSHAIR, tags = TagStyle.DASHED,
            emptyFace = "[ zone non construite ]",
        ),
    )

    // Journal : papier journal, grands titres à empattements, filets, encre rouge.
    SecretStyle.GAZETTE -> SecretTheme(
        scheme = lightColorScheme(
            primary = Color(0xFFB3261E), onPrimary = Color.White,
            primaryContainer = Color(0xFFF1D9D6), onPrimaryContainer = Color(0xFF410E0B),
            secondary = Color(0xFF161616), onSecondary = Color.White,
            background = Color(0xFFF3EEE3), onBackground = Color(0xFF161616),
            surface = Color(0xFFF3EEE3), onSurface = Color(0xFF161616),
            surfaceVariant = Color(0xFFE9E2D3), onSurfaceVariant = Color(0xFF4B4740),
            surfaceContainerLowest = Color(0xFFF8F4EB), surfaceContainerLow = Color(0xFFF0EADD),
            surfaceContainer = Color(0xFFF3EEE3), surfaceContainerHigh = Color(0xFFE8E1D1), surfaceContainerHighest = Color(0xFFDED6C3),
            outline = Color(0xFF161616), outlineVariant = Color(0xFFBDB5A3), error = Color(0xFFB3261E),
        ),
        colors = AgendaColors(
            ctf = Color(0xFFB3261E), conference = Color(0xFF1F4E8C), meetup = Color(0xFF2F6B3B),
            workshop = Color(0xFF6A3D8F), other = Color(0xFF6B5B3E), ok = Color(0xFF2F6B3B),
            muted = Color(0xFF5C574D), hairline = Color(0xFFBDB5A3), accentSoft = Color(0x22B3261E),
            isDark = false, secret = style,
        ),
        shapes = sharp(0.dp),
        style = AgendaStyle(
            titleFont = Playfair, titleWeight = FontWeight.ExtraBold, titleSize = 31.sp, titleLetterSpacing = 0.sp,
            eventTitleFont = Playfair, eventTitleSize = 19.sp,
            cardBorder = 0.dp, rules = true, transparentCards = true,
            cursor = TitleCursorStyle.DOT, tags = TagStyle.SMALL_CAPS, emptyFace = "— Pas d'article aujourd'hui —",
        ),
    )

    // Itasha (tuning japonais façon anime) : rose et cyan, sakura, bandes de course, autocollants.
    SecretStyle.ITASHA -> SecretTheme(
        scheme = lightColorScheme(
            primary = Color(0xFFFF3D8B), onPrimary = Color.White,
            primaryContainer = Color(0xFFFFD6E8), onPrimaryContainer = Color(0xFF5C0029),
            secondary = Color(0xFF00C2E0), onSecondary = Color.White,
            background = Color(0xFFFFF0F6), onBackground = Color(0xFF2A1A3A),
            surface = Color(0xFFFFF0F6), onSurface = Color(0xFF2A1A3A),
            surfaceVariant = Color(0xFFFFE1EE), onSurfaceVariant = Color(0xFF6A4A7A),
            surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFFFF7FB),
            surfaceContainer = Color.White, surfaceContainerHigh = Color(0xFFFFE8F2), surfaceContainerHighest = Color(0xFFFFD9EA),
            outline = Color(0xFFFF9CC5), outlineVariant = Color(0xFFFFCFE3), error = Color(0xFFE5004F),
        ),
        colors = AgendaColors(
            ctf = Color(0xFFFF3D8B), conference = Color(0xFF00B4D8), meetup = Color(0xFF2BC48A),
            workshop = Color(0xFF9B5CFF), other = Color(0xFFFF9F43), ok = Color(0xFF14A877),
            muted = Color(0xFF8A6A9A), hairline = Color(0xFFFFCFE3), accentSoft = Color(0x33FF3D8B),
            isDark = false, secret = style,
        ),
        shapes = Shapes(RoundedCornerShape(10.dp), RoundedCornerShape(14.dp), RoundedCornerShape(20.dp), RoundedCornerShape(26.dp)),
        style = AgendaStyle(
            titleFont = PlexSans, titleWeight = FontWeight.Bold, titleSize = 26.sp, titleLetterSpacing = (-0.5).sp,
            brandTagline = "サイバー・アジェンダ ✧",
            cardBorder = 2.dp, cardBorderColor = Color(0xFFFF9CC5), racingStripes = true,
            pattern = BackgroundPattern.SAKURA, cursor = TitleCursorStyle.HEART, tags = TagStyle.STICKER,
            favoriteBurst = FavoriteBurst.KAWAII, emptyFace = "(◕︿◕✿)  rien pour l'instant",
        ),
    )
}
