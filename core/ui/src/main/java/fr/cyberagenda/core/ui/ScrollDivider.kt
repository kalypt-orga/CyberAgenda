package fr.cyberagenda.core.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha

/**
 * Filet sous un en-tête fixe : visible (en fondu) dès que le contenu passe dessous, invisible en
 * haut de page. Il occupe toujours sa place : rien ne bouge quand il apparaît.
 */
@Composable
fun ScrollDivider(visible: Boolean, modifier: Modifier = Modifier) {
    val alpha by animateFloatAsState(if (visible) 1f else 0f, tween(150), label = "filet")
    HorizontalDivider(modifier.alpha(alpha), color = AgendaTheme.colors.hairline)
}

/** Vrai dès que la liste n'est plus tout en haut. */
@Composable
fun scrolledFromTop(list: LazyListState): Boolean {
    val scrolled by remember(list) { derivedStateOf { list.firstVisibleItemIndex > 0 || list.firstVisibleItemScrollOffset > 0 } }
    return scrolled
}

/** Vrai dès que la page n'est plus tout en haut. */
@Composable
fun scrolledFromTop(scroll: ScrollState): Boolean {
    val scrolled by remember(scroll) { derivedStateOf { scroll.value > 0 } }
    return scrolled
}
