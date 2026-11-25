package fr.cyberagenda.core.ui

import androidx.compose.foundation.background
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import fr.cyberagenda.core.model.EventKind

/** Bouton ⓘ qui ouvre la légende « Comment lire l'agenda ». */
@Composable
fun LegendButton(modifier: Modifier = Modifier) {
    var open by rememberSaveable { mutableStateOf(false) }
    IconButton(onClick = { open = true }, modifier = modifier) {
        // Couleur fixée : identique partout, même dans un champ de texte (carte) qui grise ses icônes.
        Icon(Icons.Default.Info, contentDescription = "Comment lire l'agenda : couleurs et symboles", tint = MaterialTheme.colorScheme.onBackground)
    }
    if (open) LegendSheet { open = false }
}

private val kindHelp = listOf(
    EventKind.CTF to "Compétition de sécurité : on résout des défis pour trouver des « flags ».",
    EventKind.CONFERENCE to "Exposés, salons et forums : on écoute, on rencontre des pros.",
    EventKind.MEETUP to "Rencontre conviviale : café vie privée, apéro, discussion libre.",
    EventKind.WORKSHOP to "Atelier pratique : on apprend en faisant, souvent pour débuter.",
    EventKind.OTHER to "Autre format (journée, permanence…).",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegendSheet(onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        FilterObscuredTouches()
        // Étirement natif en bout de liste ; l'élan des lancers ne part pas dans le panneau.
        val guard = rememberSheetListGuard()
        val scroll = rememberScrollState()
        Column(
            Modifier.nestedScroll(guard)
                .verticalScroll(scroll, overscrollEffect = overscrollIfScrollable(scroll, guard.overscroll))
                .padding(bottom = 32.dp)
        ) {
            Text("Comment lire l'agenda", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp))
            Text(
                "Chaque type d'événement a sa couleur et son pictogramme, repris partout : cartes, calendrier, filtres.",
                style = MaterialTheme.typography.bodySmall, color = AgendaTheme.colors.muted,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )

            SectionHeader("types")
            kindHelp.forEach { (kind, help) ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(36.dp).clip(MaterialTheme.shapes.small).background(kindColor(kind).copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center,
                    ) { KindIcon(kind, 20.dp) }
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        Text(Formatters.kind(kind).uppercase(Formatters.locale), style = Mono.tag, color = kindColor(kind))
                        Text(help, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            SectionHeader("repères")
            LegendRow({ Tag("Débutants bienvenus", AgendaTheme.colors.ok) }, "Ouvert à toutes et tous, sans prérequis.")
            LegendRow({ Tag("+3 dates", MaterialTheme.colorScheme.primary) }, "Événement qui revient : la carte montre la prochaine date.")
            LegendRow({ Text("×6 CTF", style = Mono.label) }, "Plusieurs CTF en ligne la même semaine, regroupés : touchez pour les voir.")
            LegendRow({ Icon(Icons.Default.Favorite, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)) },
                "Favori : retrouvez-le avec le filtre « favoris » et recevez un rappel.")
            LegendRow({ Text("EN LIGNE", style = Mono.tag, color = AgendaTheme.colors.muted) }, "Depuis chez soi. « Sur place » : il faut se déplacer.")

            SectionHeader("calendrier et carte")
            LegendRow({ KindDot(kindColor(EventKind.CTF), 6) }, "Point sous une date : événement d'une journée.")
            LegendRow({ Box(Modifier.width(28.dp).height(3.dp).clip(CircleShape).background(kindColor(EventKind.CONFERENCE))) },
                "Barre : événement sur plusieurs jours. « +2 » : d'autres ne tiennent pas.")
            LegendRow({ Box(Modifier.size(14.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary)) },
                "Rond sur la carte : plus il est gros, plus il y a d'événements au même endroit.")
            LegendRow({ Box(Modifier.size(10.dp).clip(CircleShape).background(AgendaTheme.colors.muted)) },
                "Rond gris : événement terminé ces 3 derniers mois (option « passés » des filtres).")
            LegendRow({ Tag("Terminé", AgendaTheme.colors.muted) }, "Événement passé, affiché estompé.")
        }
    }
}

@Composable
private fun LegendRow(symbol: @Composable () -> Unit, text: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(156.dp), contentAlignment = Alignment.CenterStart) { symbol() }
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}
