package fr.cyberagenda.core.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Recherches récentes sous un champ de recherche : un toucher reprend la recherche, la croix
 * la retire de l'historique. [entries] associe chaque élément à son libellé affiché.
 */
@Composable
fun <T> RecentSearchList(
    entries: List<Pair<T, String>>,
    onPick: (T) -> Unit,
    onRemove: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = entries.isNotEmpty(),
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier,
    ) {
        Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text("récent", style = Mono.tag, color = AgendaTheme.colors.muted, modifier = Modifier.padding(start = 4.dp, bottom = 2.dp))
            entries.forEach { (item, label) ->
                Row(
                    Modifier.fillMaxWidth().clickable { onPick(item) }.padding(start = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, tint = AgendaTheme.colors.muted, modifier = Modifier.size(18.dp))
                    Text(
                        label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                    )
                    IconButton(
                        onClick = { onRemove(item) },
                        modifier = Modifier.semantics { contentDescription = "Retirer « $label » de l'historique" },
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = AgendaTheme.colors.muted, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}
