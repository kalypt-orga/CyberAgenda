package fr.cyberagenda.core.ui

import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity

/**
 * Effet en bout de liste : l'étirement natif d'Android partout (doigt qui tire, lancer qui arrive
 * au bord), sauf quand la page ne défile pas (tout tient à l'écran) : aucun effet alors.
 *
 * Usage : `LazyColumn(state = s, overscrollEffect = overscrollIfScrollable(s))`, ou
 * `Modifier.verticalScroll(s, overscrollEffect = overscrollIfScrollable(s))`.
 */
@Composable
fun overscrollIfScrollable(state: ScrollableState, effect: OverscrollEffect? = rememberOverscrollEffect()): OverscrollEffect? =
    if (state.canScrollForward || state.canScrollBackward) effect else null

/**
 * Liste dans un panneau du bas (légende, fenêtre d'un lieu). Sans protection, l'élan restant
 * d'un lancer arrivé au bout de la liste part dans le panneau : il « saute », ou se ferme quand
 * un lancer remonte la liste jusqu'en haut. Ici, cet élan est gardé et rendu à l'étirement natif
 * de la liste ([overscroll]) : même rebond qu'ailleurs, panneau immobile. Seul un geste vers le
 * bas alors que la liste était déjà en haut reste au panneau (fermeture).
 *
 * Seul l'élan FINAL est retenu : les restes de chaque image d'un lancer doivent passer, sinon le
 * lancer s'éteint sans élan et la fermeture rapide du panneau se fige à mi-course.
 *
 * Usage : `val guard = rememberSheetListGuard()`, `Modifier.nestedScroll(guard)` sur la liste et
 * `overscrollEffect = overscrollIfScrollable(state, guard.overscroll)`.
 */
class SheetListGuard internal constructor(val overscroll: OverscrollEffect?) : NestedScrollConnection {
    private var listMovedDuringFling = false

    override suspend fun onPreFling(available: Velocity): Velocity {
        listMovedDuringFling = false
        return Velocity.Zero
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (source == NestedScrollSource.SideEffect && consumed.y != 0f) listMovedDuringFling = true
        return Offset.Zero
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        val v = available.y
        val keep = v < 0f || (v > 0f && listMovedDuringFling) // bas de liste, ou lancer remonté jusqu'en haut
        if (!keep) return Velocity.Zero // geste de fermeture : laissé au panneau
        // L'élan non consommé devient l'étirement de la liste (« rien consommé » = tout pour l'effet).
        overscroll?.applyToFling(Velocity(0f, v)) { Velocity.Zero }
        return Velocity(0f, v)
    }
}

@Composable
fun rememberSheetListGuard(): SheetListGuard {
    val effect = rememberOverscrollEffect()
    return remember(effect) { SheetListGuard(effect) }
}
