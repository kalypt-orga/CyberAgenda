package fr.cyberagenda.core.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.cyberagenda.core.model.EventKind

/*
 * Pictogrammes des types d'événements, au trait (même style que l'icône de l'app).
 * Associés à une couleur, ils rendent le code couleur compréhensible sans légende.
 */

private fun stroked(name: String, vararg paths: String): ImageVector =
    ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
        paths.forEach { d ->
            addPath(
                pathData = addPathNodes(d),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()

object KindIcons {
    /** CTF : un drapeau à capturer. */
    val Ctf = stroked("ctf", "M5,21V4", "M5,4H17L14.5,8L17,12H5")
    /** Conférence : un micro. */
    val Conference = stroked("conference", "M9,3H15V11A3,3 0,0 1,9 11Z", "M5.5,10.5A6.5,6.5 0,0 0,18.5 10.5", "M12,17V21", "M8.5,21H15.5")
    /** Rencontre : deux bulles de discussion. */
    val Meetup = stroked("meetup", "M3,4H14V12H8L4.5,15V12H3Z", "M17,8H21V16H19.5V19L16,16H11V14.5")
    /** Atelier : un ordinateur portable. */
    val Workshop = stroked("workshop", "M5,5H19V15H5Z", "M2.5,19H21.5", "M9,10L11,12L15,8")
    /** Autre : une page de calendrier. */
    val Other = stroked("other", "M4,6H20V20H4Z", "M4,10H20", "M8,3V7", "M16,3V7")

    fun of(kind: EventKind): ImageVector = when (kind) {
        EventKind.CTF -> Ctf
        EventKind.CONFERENCE -> Conference
        EventKind.MEETUP -> Meetup
        EventKind.WORKSHOP -> Workshop
        EventKind.OTHER -> Other
    }
}

/** Pictogramme d'un type, dans sa couleur. */
@Composable
fun KindIcon(kind: EventKind, size: Dp = 16.dp, modifier: Modifier = Modifier, tint: Color = kindColor(kind)) {
    Icon(KindIcons.of(kind), contentDescription = null, tint = tint, modifier = modifier.size(size))
}
