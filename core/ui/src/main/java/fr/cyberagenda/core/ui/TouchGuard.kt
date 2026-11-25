package fr.cyberagenda.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

/**
 * Protection anti-tapjacking pour les dialogues et panneaux du bas : ils ont leur propre
 * fenêtre, que le réglage posé sur la fenêtre de l'activité ne couvre pas. À appeler au début
 * du contenu du dialogue : les touches reçues alors qu'une autre app recouvre la fenêtre
 * (overlay transparent) sont ignorées.
 */
@Composable
fun FilterObscuredTouches() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.rootView.filterTouchesWhenObscured = true
        onDispose { }
    }
}
