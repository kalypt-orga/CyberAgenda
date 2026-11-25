package fr.cyberagenda.feature.settings

import android.content.ActivityNotFoundException
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.TimePicker
import java.time.LocalTime
import fr.cyberagenda.core.ui.ScreenHeader
import fr.cyberagenda.core.ui.scrolledFromTop
import fr.cyberagenda.core.ui.ScrollDivider
import fr.cyberagenda.core.ui.overscrollIfScrollable
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import fr.cyberagenda.core.model.EventFormat
import fr.cyberagenda.core.model.EventKind
import fr.cyberagenda.core.ui.AgendaTheme
import fr.cyberagenda.core.ui.Formatters
import fr.cyberagenda.core.ui.Mono
import fr.cyberagenda.core.ui.SectionHeader
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
fun ProposeEventRoute(onBack: () -> Unit) {
    val context = LocalContext.current
    var p by remember { mutableStateOf(Proposal()) }
    var submitted by remember { mutableStateOf(false) }
    val errors = p.errors()
    fun err(field: String) = if (submitted) errors[field] else null

    Column(Modifier.fillMaxSize()) {
        // Titre au style des écrans principaux (« proposer_ »), à côté de la flèche de retour.
        Row(Modifier.fillMaxWidth().padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour") }
            ScreenHeader("proposer", "un événement qui manque à l'agenda", startPadding = 4.dp, modifier = Modifier.weight(1f))
        }
        val scroll = rememberScrollState()
        ScrollDivider(scrolledFromTop(scroll))
        Column(
            Modifier.fillMaxSize().verticalScroll(scroll, overscrollEffect = overscrollIfScrollable(scroll)).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "Votre proposition s'ouvre pré-remplie sur GitHub, où elle sera publique et vérifiée avant d'être ajoutée.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            SectionHeader("l'événement")
            Field("Titre *", p.title, err("title")) { p = p.copy(title = it.take(150)) }
            Chips(EventKind.entries.filter { it != EventKind.OTHER }, p.kind, Formatters::kind) { p = p.copy(kind = it) }
            Chips(EventFormat.entries, p.format, Formatters::format) { p = p.copy(format = it) }

            SectionHeader("quand")
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DateField("Début *", p.start, err("start"), Modifier.weight(1f)) { p = p.copy(start = it) }
                DateField("Fin", p.end, err("end"), Modifier.weight(1f)) { p = p.copy(end = it) }
            }
            // Heures facultatives : sans elles, l'événement s'affichera « horaire inconnu ».
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TimeField("Heure de début", p.startTime, err("startTime"), Modifier.weight(1f)) { p = p.copy(startTime = it) }
                TimeField("Heure de fin", p.endTime, err("endTime"), Modifier.weight(1f)) { p = p.copy(endTime = it) }
            }

            SectionHeader("où et comment")
            if (p.format != EventFormat.ONLINE) {
                Field("Lieu et ville * (ex. « Médiathèque, Bordeaux (33) »)", p.location, err("location")) { p = p.copy(location = it.take(200)) }
            }
            Field("Lien https:// *", p.url, err("url"), KeyboardType.Uri) { p = p.copy(url = it.take(500)) }
            Field("Organisation", p.organizer, null) { p = p.copy(organizer = it.take(150)) }

            SectionHeader("tarif")
            Chips(PriceChoice.entries, p.price, { when (it) { PriceChoice.UNKNOWN -> "Non précisé"; PriceChoice.FREE -> "Gratuit"; PriceChoice.PAID -> "Payant" } }) {
                p = p.copy(price = it)
            }
            if (p.price == PriceChoice.PAID) {
                Field("Montant * (ex. « 20 € », « 10–40 € »)", p.priceText, err("priceText")) { p = p.copy(priceText = it.take(60)) }
            }
            Field("Description", p.description, err("description"), minLines = 3) { p = p.copy(description = it.take(1_000)) }
            Row(
                Modifier.fillMaxWidth().clickable { p = p.copy(beginnerFriendly = !p.beginnerFriendly) }.padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(p.beginnerFriendly, onCheckedChange = { p = p.copy(beginnerFriendly = it) })
                Text("Accessible aux débutants, sans prérequis", style = MaterialTheme.typography.bodyMedium)
            }

            Button(
                onClick = {
                    submitted = true
                    if (errors.isEmpty()) openGitHub(context, p)
                },
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
            ) { Text("Proposer sur GitHub ↗") }
            // Secours sans compte GitHub : seulement si une adresse de contact est configurée.
            if (BuildConfig.PROPOSE_EMAIL.isNotBlank()) {
                TextButton(
                    onClick = {
                        submitted = true
                        if (errors.isEmpty()) sendEmail(context, p)
                    },
                    modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                ) { Text("Pas de compte GitHub ? Envoyer par e-mail") }
            }
            if (submitted && errors.isNotEmpty()) {
                Text("Vérifiez les champs signalés.", color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 20.dp))
            }
        }
    }
}

@Composable
private fun Field(
    label: String, value: String, error: String?, keyboard: KeyboardType = KeyboardType.Text,
    minLines: Int = 1, onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value, onValueChange = onChange,
        label = { Text(label) },
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        singleLine = minLines == 1, minLines = minLines,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    )
}

@Composable
private fun <T> Chips(values: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        values.forEach { v ->
            FilterChip(
                selected = v == selected, onClick = { onSelect(v) },
                label = { Text(label(v), style = Mono.label) },
                shape = MaterialTheme.shapes.small,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(label: String, date: LocalDate?, error: String?, modifier: Modifier, onPick: (LocalDate) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Surface(
        onClick = { open = true }, modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = Mono.tag, color = AgendaTheme.colors.muted)
            Text(date?.let { Formatters.dayLong(it) } ?: "Choisir…", style = MaterialTheme.typography.bodyLarge)
            if (error != null) Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
    if (open) {
        val state = rememberDatePickerState(initialSelectedDateMillis = date?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    open = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Annuler") } },
        ) { DatePicker(state) }
    }
}

/** Ouvre, dans le navigateur, la page « nouvelle issue » du dépôt avec la proposition pré-remplie. */
private fun openGitHub(context: Context, p: Proposal) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(p.toGitHubIssueUrl(BuildConfig.PROPOSE_REPO)))
        .addCategory(Intent.CATEGORY_BROWSABLE)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "Aucun navigateur pour ouvrir GitHub", Toast.LENGTH_SHORT).show()
    }
}

/** Secours : messagerie de l'utilisateur, message pré-rempli vers l'adresse de contact. */
private fun sendEmail(context: Context, p: Proposal) {
    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
        .putExtra(Intent.EXTRA_EMAIL, arrayOf(BuildConfig.PROPOSE_EMAIL))
        .putExtra(Intent.EXTRA_SUBJECT, "[CyberAgenda] Proposition : ${p.title.trim().take(80)}")
        .putExtra(Intent.EXTRA_TEXT, p.toMessage())
    try {
        context.startActivity(Intent.createChooser(intent, "Envoyer la proposition"))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "Aucune application de messagerie", Toast.LENGTH_SHORT).show()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeField(label: String, time: LocalTime?, error: String?, modifier: Modifier, onPick: (LocalTime?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Surface(
        onClick = { open = true }, modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = Mono.tag, color = AgendaTheme.colors.muted)
            Text(time?.toString() ?: "Facultatif", style = MaterialTheme.typography.bodyLarge,
                color = if (time == null) AgendaTheme.colors.muted else MaterialTheme.colorScheme.onSurface)
            if (error != null) Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
    if (open) {
        val state = rememberTimePickerState(initialHour = time?.hour ?: 18, initialMinute = time?.minute ?: 0, is24Hour = true)
        AlertDialog(
            onDismissRequest = { open = false },
            confirmButton = { TextButton(onClick = { onPick(LocalTime.of(state.hour, state.minute)); open = false }) { Text("OK") } },
            // « Effacer » : revenir à « horaire inconnu ».
            dismissButton = { TextButton(onClick = { onPick(null); open = false }) { Text("Effacer") } },
            text = { TimePicker(state) },
        )
    }
}
