package fr.cyberagenda.core.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset

/**
 * Révélation d'un nouveau thème (déblocage des thèmes secrets) : l'écran actuel est figé puis
 * s'ouvre en un cercle qui grandit depuis [origin] (coordonnées de la fenêtre) sur le nouveau thème.
 * Appeler AVANT de changer le thème. Sans fournisseur (aperçus, tests), ne fait rien.
 */
fun interface ThemeReveal {
    fun start(origin: Offset)
}

val LocalThemeReveal = staticCompositionLocalOf { ThemeReveal { } }
