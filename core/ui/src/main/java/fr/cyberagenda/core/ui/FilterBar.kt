package fr.cyberagenda.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fr.cyberagenda.core.model.BeginnerFilter
import fr.cyberagenda.core.model.EventFilter
import fr.cyberagenda.core.model.EventFormat
import fr.cyberagenda.core.model.EventKind

/**
 * Barre de filtre commune aux vues : un bouton « Filtres » et les filtres actifs, retirables
 * d'un toucher. Le filtre est partagé et enregistré : il s'applique partout et reste actif.
 */
@Composable
fun FilterBar(filter: EventFilter, onChange: (EventFilter) -> Unit, modifier: Modifier = Modifier, resultCount: Int? = null) {
    var open by rememberSaveable { mutableStateOf(false) }
    Row(
        modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            onClick = { open = true },
            shape = MaterialTheme.shapes.small,
            color = if (filter.isDefault) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.primary,
            contentColor = if (filter.isDefault) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimary,
            border = BorderStroke(1.dp, if (filter.isDefault) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary),
            modifier = Modifier.semantics { contentDescription = "Filtres, ${filter.activeCount} actif(s)" },
        ) {
            Text(
                if (filter.isDefault) "≡ Filtres" else "≡ Filtres · ${filter.activeCount}",
                style = Mono.label, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        if (filter.isDefault) {
            Text("tous les événements", style = Mono.meta, color = AgendaTheme.colors.muted)
        } else {
            activeTags(filter).forEach { (label, without) -> RemovableTag(label) { onChange(without) } }
        }
        if (resultCount != null && !filter.isDefault) {
            Text("→ $resultCount", style = Mono.meta, color = AgendaTheme.colors.muted)
        }
    }
    if (open) FilterSheet(filter, onChange) { open = false }
}

/** Libellé de chaque filtre actif, avec le filtre obtenu si on le retire. */
private fun activeTags(f: EventFilter): List<Pair<String, EventFilter>> = buildList {
    f.formats.sortedBy { it.ordinal }.forEach { add(Formatters.format(it) to f.copy(formats = f.formats - it)) }
    f.kinds.sortedBy { it.ordinal }.forEach { add(Formatters.kind(it) to f.copy(kinds = f.kinds - it)) }
    when (f.beginner) {
        BeginnerFilter.ONLY -> add("Débutants bienvenus" to f.copy(beginner = BeginnerFilter.ALL))
        BeginnerFilter.HIDE -> add("Sans « débutants »" to f.copy(beginner = BeginnerFilter.ALL))
        BeginnerFilter.ALL -> Unit
    }
    if (f.favoritesOnly) add("Favoris" to f.copy(favoritesOnly = false))
    if (f.includePast) add("+ passés 3 mois" to f.copy(includePast = false))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RemovableTag(label: String, onRemove: () -> Unit) {
    InputChip(
        selected = true,
        onClick = onRemove,
        label = { Text(label, style = Mono.label) },
        trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Retirer le filtre $label", modifier = Modifier.size(16.dp)) },
        shape = MaterialTheme.shapes.small,
        colors = InputChipDefaults.inputChipColors(selectedContainerColor = AgendaTheme.colors.accentSoft),
    )
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun FilterSheet(filter: EventFilter, onChange: (EventFilter) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        FilterObscuredTouches()
        // Zone défilante protégée : un lancer au-delà du contenu ne fait plus bouger le panneau.
        val guard = rememberSheetListGuard()
        val scroll = rememberScrollState()
        Column(
            Modifier.nestedScroll(guard)
                .verticalScroll(scroll, overscrollEffect = overscrollIfScrollable(scroll, guard.overscroll))
                .padding(bottom = 24.dp)
        ) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Filtres", style = MaterialTheme.typography.titleLarge)
                    Text("Appliqués à l'agenda, au calendrier et à la carte.",
                        style = MaterialTheme.typography.bodySmall, color = AgendaTheme.colors.muted)
                }
                TextButton(onClick = { onChange(EventFilter()) }, enabled = !filter.isDefault) { Text("Tout effacer") }
            }

            SectionHeader("format")
            ChipRow {
                listOf(EventFormat.ONLINE, EventFormat.ONSITE).forEach { f ->
                    Toggle(Formatters.format(f), f in filter.formats) {
                        onChange(filter.copy(formats = if (f in filter.formats) filter.formats - f else filter.formats + f))
                    }
                }
            }

            SectionHeader("type")
            FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EventKind.entries.forEach { k ->
                    Toggle(Formatters.kind(k), k in filter.kinds, kind = k) {
                        onChange(filter.copy(kinds = if (k in filter.kinds) filter.kinds - k else filter.kinds + k))
                    }
                }
            }

            SectionHeader("niveau")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                val options = listOf(BeginnerFilter.ALL to "Tous", BeginnerFilter.ONLY to "Débutants", BeginnerFilter.HIDE to "Confirmés")
                options.forEachIndexed { i, (value, label) ->
                    SegmentedButton(
                        selected = filter.beginner == value,
                        onClick = { onChange(filter.copy(beginner = value)) },
                        shape = SegmentedButtonDefaults.itemShape(i, options.size, MaterialTheme.shapes.small),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = MaterialTheme.colorScheme.primary,
                            activeContentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                        icon = {},
                        label = { Text(label, style = Mono.label) },
                    )
                }
            }
            Text(
                when (filter.beginner) {
                    BeginnerFilter.ALL -> "Tous les événements, quel que soit le niveau."
                    BeginnerFilter.ONLY -> "Seulement les événements ouverts à toutes et tous, sans prérequis."
                    BeginnerFilter.HIDE -> "Masque les événements étiquetés « débutants bienvenus »."
                },
                style = MaterialTheme.typography.bodySmall, color = AgendaTheme.colors.muted,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            )

            SectionHeader("période")
            // Toute la ligne est cliquable, pas seulement l'interrupteur.
            Row(
                Modifier.fillMaxWidth()
                    .toggleable(value = filter.includePast, role = Role.Switch) { onChange(filter.copy(includePast = it)) }
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Inclure les 3 derniers mois", style = MaterialTheme.typography.bodyLarge)
                    Text("Événements terminés récemment, estompés.", style = MaterialTheme.typography.bodySmall, color = AgendaTheme.colors.muted)
                }
                Switch(
                    checked = filter.includePast, onCheckedChange = null,
                    colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary),
                )
            }

            SectionHeader("favoris")
            Row(
                Modifier.fillMaxWidth()
                    .toggleable(value = filter.favoritesOnly, role = Role.Switch) { onChange(filter.copy(favoritesOnly = it)) }
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Uniquement mes favoris", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Switch(
                    checked = filter.favoritesOnly,
                    onCheckedChange = null,
                    colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

@Composable
private fun Toggle(label: String, selected: Boolean, kind: EventKind? = null, onClick: () -> Unit) {
    FilterChip(
        selected = selected, onClick = onClick,
        label = { Text(label, style = Mono.label) },
        leadingIcon = kind?.let { { KindIcon(it, 16.dp, tint = if (selected) MaterialTheme.colorScheme.onPrimary else kindColor(it)) } },
        shape = MaterialTheme.shapes.small,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
        ),
    )
}
