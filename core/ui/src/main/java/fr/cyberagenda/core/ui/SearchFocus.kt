package fr.cyberagenda.core.ui

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalFocusManager

/**
 * Zone d'une recherche (champ + suggestions) : un toucher en dehors, ou la fermeture du clavier,
 * désactive le champ. Sans cela, le champ reste actif (curseur, suggestions ouvertes) après
 * qu'on a touché la liste ou la carte.
 *
 * Usage : `val zone = rememberSearchZone()`, `Modifier.searchZone(zone)` sur le bloc de
 * recherche, et `Modifier.releaseSearchOnOutsideTap(zone)` sur le conteneur de l'écran.
 */
class SearchZone internal constructor() {
    internal var bounds: Rect? = null
    internal var origin: Offset = Offset.Zero
}

@Composable
fun rememberSearchZone(): SearchZone = remember { SearchZone() }

/** Le bloc de recherche (champ et suggestions) : y toucher ne désactive pas le champ. */
fun Modifier.searchZone(zone: SearchZone): Modifier = onGloballyPositioned { zone.bounds = it.boundsInWindow() }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Modifier.releaseSearchOnOutsideTap(zone: SearchZone): Modifier {
    val focus = LocalFocusManager.current
    // Clavier fermé (touche Retour) : le champ n'a plus de raison de rester actif.
    val imeVisible = WindowInsets.isImeVisible
    val wasVisible = remember { booleanArrayOf(false) }
    LaunchedEffect(imeVisible) {
        if (wasVisible[0] && !imeVisible) focus.clearFocus()
        wasVisible[0] = imeVisible
    }
    return this
        .onGloballyPositioned { zone.origin = it.positionInWindow() }
        .pointerInput(zone) {
            awaitEachGesture {
                // Passe « Initial » : on voit le toucher avant la liste ou la carte, sans le consommer.
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val inside = zone.bounds?.contains(down.position + zone.origin) == true
                if (!inside) focus.clearFocus()
            }
        }
}
