package fr.cyberagenda.core.ui

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

// Polices embarquées (licence OFL, voir assets/licenses) : aucun téléchargement à l'exécution.
// Un fichier fixe par graisse (instances des polices variables d'origine) : certains
// téléphones (HyperOS notamment) ignorent le réglage d'épaisseur des polices variables,
// ce qui affichait tous les textes en graisse normale.
val PlexSans = FontFamily(
    Font(R.font.ibm_plex_sans_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_sans_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_sans_semibold, FontWeight.SemiBold),
    Font(R.font.ibm_plex_sans_bold, FontWeight.Bold),
)

val JetBrainsMono = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
)

/** Style « métadonnée » : dates, étiquettes, compteurs. */
object Mono {
    val tag = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 0.06.em)
    val meta = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Normal, fontSize = 12.sp)
    val label = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Medium, fontSize = 13.sp)
    val brand = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, fontSize = 20.sp, letterSpacing = (-0.01).em)
}

private val base = Typography()

val AgendaTypography = Typography(
    displaySmall = base.displaySmall.copy(fontFamily = PlexSans, fontWeight = FontWeight.SemiBold),
    headlineLarge = base.headlineLarge.copy(fontFamily = PlexSans, fontWeight = FontWeight.SemiBold),
    headlineMedium = base.headlineMedium.copy(fontFamily = PlexSans, fontWeight = FontWeight.SemiBold),
    headlineSmall = base.headlineSmall.copy(fontFamily = PlexSans, fontWeight = FontWeight.SemiBold, lineHeight = 30.sp),
    titleLarge = base.titleLarge.copy(fontFamily = PlexSans, fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontFamily = PlexSans, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 21.sp),
    titleSmall = base.titleSmall.copy(fontFamily = PlexSans, fontWeight = FontWeight.Medium),
    bodyLarge = base.bodyLarge.copy(fontFamily = PlexSans, lineHeight = 24.sp),
    bodyMedium = base.bodyMedium.copy(fontFamily = PlexSans, lineHeight = 21.sp),
    bodySmall = base.bodySmall.copy(fontFamily = PlexSans),
    labelLarge = base.labelLarge.copy(fontFamily = PlexSans, fontWeight = FontWeight.Medium),
    labelMedium = base.labelMedium.copy(fontFamily = JetBrainsMono),
    labelSmall = base.labelSmall.copy(fontFamily = JetBrainsMono),
)
