package fr.cyberagenda.feature.events

import android.content.ActivityNotFoundException
import androidx.compose.ui.text.font.FontWeight
import fr.cyberagenda.core.ui.style
import androidx.compose.ui.text.style.TextOverflow
import fr.cyberagenda.core.ui.scrolledFromTop
import fr.cyberagenda.core.ui.ScrollDivider
import fr.cyberagenda.core.ui.overscrollIfScrollable
import fr.cyberagenda.core.ui.FilterObscuredTouches
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.cyberagenda.core.model.Event
import fr.cyberagenda.core.network.SafeUrl
import fr.cyberagenda.core.ui.AgendaTheme
import fr.cyberagenda.core.ui.ExternalApps
import fr.cyberagenda.core.ui.AppChooserSheet
import androidx.compose.runtime.remember
import androidx.compose.foundation.clickable
import androidx.compose.material3.ModalBottomSheet
import fr.cyberagenda.core.ui.Formatters
import fr.cyberagenda.core.ui.KindIcon
import fr.cyberagenda.core.ui.Mono
import fr.cyberagenda.core.ui.Tag
import fr.cyberagenda.core.ui.kindColor

@Composable
fun EventDetailRoute(onBack: () -> Unit, viewModel: EventDetailViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour") }
            Text("retour", style = Mono.meta, color = AgendaTheme.colors.muted, modifier = Modifier.weight(1f))
            val shareContext = LocalContext.current
            state.event?.let { e ->
                IconButton(onClick = { shareEvent(shareContext, e) }) {
                    Icon(Icons.Default.Share, contentDescription = "Partager l'événement")
                }
            }
            if (state.event != null) {
                IconButton(onClick = viewModel::toggleFavorite) {
                    Icon(
                        if (state.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (state.favorite) "Retirer des favoris" else "Ajouter aux favoris",
                        tint = if (state.favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        val event = state.event
        when {
            event != null -> EventDetail(event, state.sourceName, state.otherDates)
            state.loaded -> Text("Cet événement n'est plus disponible.", Modifier.padding(20.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EventDetail(event: Event, sourceName: String, otherDates: List<Event>) {
    val context = LocalContext.current
    var confirmUrl by rememberSaveable { mutableStateOf(false) }
    var calendarApps by remember { mutableStateOf<List<ExternalApps.AppTarget>?>(null) }
    var mapApps by remember { mutableStateOf<List<ExternalApps.AppTarget>?>(null) }
    // Itinéraire : une seule appli → ouverte directement ; plusieurs → même panneau de choix que l'agenda.
    val openDirections = {
        val apps = ExternalApps.directionApps(context, event)
        when (apps.size) {
            0 -> Toast.makeText(context, "Aucune application de cartographie installée", Toast.LENGTH_SHORT).show()
            1 -> ExternalApps.openDirections(context, event, apps.first())
            else -> mapApps = apps
        }
    }
    val host = SafeUrl.displayHost(event.url)
    val accent = kindColor(event.kind)

    val scroll = rememberScrollState()
    Column(Modifier.fillMaxSize()) {
        // En-tête fixe : type, titre et étiquettes restent visibles pendant qu'on lit les détails.
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KindIcon(event.kind, 18.dp)
                Text(
                    "${Formatters.kind(event.kind)} · ${Formatters.format(event.format)}".uppercase(Formatters.locale),
                    style = Mono.tag, color = accent,
                )
            }
            // Un titre très long est limité à 3 lignes pour laisser la place aux détails.
            val titleFont = AgendaTheme.style.eventTitleFont
            Text(
                event.title,
                style = if (titleFont == null) MaterialTheme.typography.headlineSmall
                else MaterialTheme.typography.headlineSmall.copy(fontFamily = titleFont, fontWeight = FontWeight.Bold),
                maxLines = 3, overflow = TextOverflow.Ellipsis,
            )
            val ended = event.endsAtEpochMs < System.currentTimeMillis()
            if (ended || event.beginnerFriendly) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (ended) Tag("Événement terminé", AgendaTheme.colors.muted)
                    if (event.beginnerFriendly) Tag("Débutants bienvenus", AgendaTheme.colors.ok)
                }
            }
        }
        ScrollDivider(scrolledFromTop(scroll))
        Column(
            Modifier.fillMaxSize().verticalScroll(scroll, overscrollEffect = overscrollIfScrollable(scroll))
                .padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                InfoLine("quand", Formatters.dateRange(event.startsAtEpochMs, event.endsAtEpochMs))
                val directions = ExternalApps.canGetDirections(event)
                // L'adresse elle-même ouvre l'itinéraire (application de cartographie au choix).
                InfoLine(
                    "où",
                    event.location ?: if (event.format == fr.cyberagenda.core.model.EventFormat.ONLINE) "En ligne, depuis chez vous" else "Non précisé",
                    onClick = if (directions) openDirections else null,
                )
                InfoLine("tarif", event.price ?: "Non précisé, voir le site")
                event.organizer?.let { InfoLine("par", it) }
                InfoLine("source", sourceName)
            }

            // Actions : site, itinéraire (si lieu), agenda. Passent à la ligne sur les petits écrans.
            val compact = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { confirmUrl = true }, enabled = host != null, shape = MaterialTheme.shapes.small, contentPadding = compact) {
                    Text("Voir le site ↗")
                }
                if (ExternalApps.canGetDirections(event)) {
                    OutlinedButton(
                        onClick = openDirections,
                        shape = MaterialTheme.shapes.small, contentPadding = compact,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    ) { Text("Itinéraire") }
                }
                if (event.endsAtEpochMs >= System.currentTimeMillis()) {
                    OutlinedButton(
                        onClick = {
                            // Un seul agenda : on l'ouvre directement ; aucun : fichier .ics ; sinon : choix.
                            val apps = ExternalApps.calendarApps(context)
                            when (apps.size) {
                                0 -> ExternalApps.openIcs(context, event)
                                1 -> ExternalApps.addToCalendar(context, event, apps.first())
                                else -> calendarApps = apps
                            }
                        },
                        shape = MaterialTheme.shapes.small, contentPadding = compact,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    ) { Text("Ajouter à l'agenda") }
                }
            }

            if (otherDates.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("AUTRES DATES", style = Mono.tag, color = AgendaTheme.colors.muted)
                    Text("  ${otherDates.size}", style = Mono.tag, color = MaterialTheme.colorScheme.primary)
                    HorizontalDivider(Modifier.padding(start = 12.dp).weight(1f), color = AgendaTheme.colors.hairline)
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    otherDates.take(12).forEach {
                        Text("· " + Formatters.dateRange(it.startsAtEpochMs, it.endsAtEpochMs), style = Mono.meta)
                    }
                }
            }

            if (event.description.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("DESCRIPTION", style = Mono.tag, color = AgendaTheme.colors.muted)
                    HorizontalDivider(Modifier.padding(start = 12.dp).weight(1f), color = AgendaTheme.colors.hairline)
                }
                // Texte brut uniquement : aucun HTML interprété, aucune WebView.
                SelectionContainer {
                    Text(event.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f))
                }
            }
        }
    }

    calendarApps?.let { apps ->
        AppChooserSheet(
            title = "Ajouter avec…", apps = apps,
            onPick = { ExternalApps.addToCalendar(context, event, it) },
            onDismiss = { calendarApps = null },
            footerLabel = "Autre agenda (fichier .ics)", onFooter = { ExternalApps.openIcs(context, event) },
        )
    }
    mapApps?.let { apps ->
        AppChooserSheet(
            title = "Itinéraire avec…", apps = apps,
            onPick = { ExternalApps.openDirections(context, event, it) },
            onDismiss = { mapApps = null },
        )
    }

    if (confirmUrl && host != null) {
        AlertDialog(
            onDismissRequest = { confirmUrl = false },
            title = { Text("Ouvrir un site externe ?") },
            text = {
                FilterObscuredTouches()
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Vous allez quitter l'application pour :")
                    Text(host, style = Mono.label.copy(fontSize = MaterialTheme.typography.titleMedium.fontSize), color = MaterialTheme.colorScheme.primary)
                    Text(
                        "Vérifiez toujours l'adresse avant de saisir des informations personnelles.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = { TextButton(onClick = { confirmUrl = false; openUrl(context, event.url) }) { Text("Ouvrir") } },
            dismissButton = { TextButton(onClick = { confirmUrl = false }) { Text("Annuler") } },
        )
    }
}

@Composable
private fun InfoLine(label: String, value: String, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().then(
            if (onClick != null) Modifier.clickable(onClickLabel = "Ouvrir l'itinéraire", onClick = onClick) else Modifier
        ),
    ) {
        Text(label.uppercase(Formatters.locale), style = Mono.tag, color = AgendaTheme.colors.muted, modifier = Modifier.width(72.dp).padding(top = 3.dp))
        // Valeur cliquable : soulignée en pointillé discret, couleur d'accent.
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge.copy(
                textDecoration = if (onClick != null) TextDecoration.Underline else null,
            ),
            color = if (onClick != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Partage en texte brut via la feuille de partage Android (aucune donnée personnelle ajoutée). */
private fun shareEvent(context: Context, event: Event) {
    val text = buildString {
        appendLine(event.title)
        appendLine(Formatters.dateRange(event.startsAtEpochMs, event.endsAtEpochMs))
        appendLine(event.location ?: Formatters.format(event.format))
        appendLine(event.url)
        appendLine()
        append("Repéré avec CyberAgenda")
    }
    val intent = Intent(Intent.ACTION_SEND).setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, event.title)
        .putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(intent, "Partager l'événement"))
}

/** Ouvre l'URL dans le navigateur, après une seconde validation (https uniquement). */
private fun openUrl(context: Context, url: String) {
    val safe = SafeUrl.parseHttps(url) ?: return
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(safe.toString())).addCategory(Intent.CATEGORY_BROWSABLE)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "Aucun navigateur disponible", Toast.LENGTH_SHORT).show()
    }
}

