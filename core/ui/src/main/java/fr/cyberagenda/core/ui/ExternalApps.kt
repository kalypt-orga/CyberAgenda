package fr.cyberagenda.core.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import android.widget.Toast
import androidx.core.content.FileProvider
import fr.cyberagenda.core.formats.IcsWriter
import fr.cyberagenda.core.model.Event
import fr.cyberagenda.core.model.EventFormat
import java.io.File
import java.time.ZoneOffset

/**
 * Ouverture d'applications externes. Toujours via le sélecteur d'Android : c'est l'utilisateur
 * qui choisit l'application (Maps ou Waze, Google Agenda ou un autre…), jamais l'app à sa place.
 */
object ExternalApps {
    private const val EVENT_TYPE = "vnd.android.cursor.dir/event"

    /** Lien geo: vers le lieu : l'adresse en texte est géolocalisée précisément par l'app choisie. */
    private fun directionsIntent(event: Event): Intent? {
        val uri = when {
            !event.location.isNullOrBlank() -> Uri.parse("geo:0,0?q=" + Uri.encode(event.location))
            event.latitude != null && event.longitude != null ->
                Uri.parse("geo:${event.latitude},${event.longitude}?q=${event.latitude},${event.longitude}")
            else -> return null
        }
        return Intent(Intent.ACTION_VIEW, uri)
    }

    /** Applications de cartographie installées (Maps, Waze, OsmAnd…). */
    fun directionApps(context: Context, event: Event): List<AppTarget> =
        directionsIntent(event)?.let { targetsFor(context, it) }.orEmpty()

    fun openDirections(context: Context, event: Event, app: AppTarget) {
        val intent = directionsIntent(event)?.setClassName(app.packageName, app.activity) ?: return
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(context, "Impossible d'ouvrir ${app.label}", Toast.LENGTH_SHORT).show()
        }
    }

    fun canGetDirections(event: Event) =
        event.format != EventFormat.ONLINE && (!event.location.isNullOrBlank() || event.latitude != null)

    /** Application proposée dans le panneau de choix (agenda, cartographie…). */
    data class AppTarget(val label: String, val icon: android.graphics.drawable.Drawable, val packageName: String, val activity: String)

    /** Applications installées capables de traiter [intent], une par paquet, triées par nom. */
    private fun targetsFor(context: Context, intent: Intent): List<AppTarget> {
        val pm = context.packageManager
        return pm.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.exported && it.activityInfo.packageName != context.packageName }
            .distinctBy { it.activityInfo.packageName }
            .map { AppTarget(it.loadLabel(pm).toString(), it.loadIcon(pm), it.activityInfo.packageName, it.activityInfo.name) }
            .sortedBy { it.label.lowercase() }
    }

    /**
     * Agendas installés qui acceptent l'ajout direct (Google, Proton, Outlook, Samsung, Xiaomi…).
     * L'app affiche elle-même ce choix : les sélecteurs de certaines surcouches (HyperOS) masquent
     * des agendas (priorité donnée à l'agenda système, cibles explicites ignorées).
     * La liste se limite aux agendas grâce à la déclaration <queries> du manifeste.
     */
    fun calendarApps(context: Context): List<AppTarget> =
        targetsFor(context, Intent(Intent.ACTION_INSERT).setDataAndType(CalendarContract.Events.CONTENT_URI, EVENT_TYPE))

    /** Ouvre l'écran d'ajout de l'agenda choisi, pré-rempli (aucune permission calendrier). */
    fun addToCalendar(context: Context, event: Event, app: AppTarget) {
        val compliant = app.packageName in ALL_DAY_COMPLIANT
        // Journée entière + agenda qui ignore l'option d'Android (ex. Proton) : on lui ouvre un
        // fichier .ics, où « journée entière » est explicite (DTSTART;VALUE=DATE).
        if (isAllDay(event) && !compliant) {
            val uri = writeIcs(context, listOf(event), event.title)
            val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "text/calendar")
                .setPackage(app.packageName)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            view.clipData = ClipData.newRawUri(event.title, uri)
            if (context.packageManager.queryIntentActivities(view, 0).isNotEmpty()) {
                try {
                    context.startActivity(view)
                    return
                } catch (_: Exception) {
                    // On retombe sur l'ajout direct ci-dessous.
                }
            }
        }
        val intent = insertIntent(event, followsAllDayConvention = compliant)
            .setClassName(app.packageName, app.activity)
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(context, "Impossible d'ouvrir ${app.label}", Toast.LENGTH_SHORT).show()
        }
    }

    /** Secours si aucun agenda n'accepte l'ajout direct : fichier .ics à ouvrir. */
    fun openIcs(context: Context, event: Event) {
        val uri = writeIcs(context, listOf(event), event.title)
        val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "text/calendar")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        view.clipData = ClipData.newRawUri(event.title, uri)
        try {
            context.startActivity(Intent.createChooser(view, "Ouvrir le fichier .ics avec…").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "Aucune application d'agenda installée", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Agendas qui respectent la convention « journée entière » d'Android (minuit UTC, fin exclusive).
     * Les autres (Proton, Outlook…) ignorent ce drapeau : on leur envoie des heures locales simples.
     */
    private val ALL_DAY_COMPLIANT = setOf(
        "com.google.android.calendar", "com.xiaomi.calendar", "com.android.calendar", "com.samsung.android.calendar",
    )

    private fun insertIntent(event: Event, followsAllDayConvention: Boolean = true): Intent {
        val allDay = isAllDay(event)
        // Journée entière : la convention d'Android attend un début et une fin à minuit UTC, fin
        // exclusive (lendemain du dernier jour). Pour un agenda qui l'ignore, on envoie plutôt
        // 00:00 → 23:59 en heure locale, qui s'affiche correctement tel quel.
        val (begin, end) = if (allDay && !followsAllDayConvention) {
            event.startsAtEpochMs to event.endsAtEpochMs
        } else if (allDay) {
            val first = Formatters.zoned(event.startsAtEpochMs).toLocalDate()
            val last = Formatters.zoned(event.endsAtEpochMs).toLocalDate()
            first.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() to
                maxOf(last, first).plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        } else event.startsAtEpochMs to event.endsAtEpochMs
        return Intent(Intent.ACTION_INSERT)
            // Type explicite : certains sélecteurs (HyperOS) ne le retrouvent pas seuls et ne
            // proposent alors qu'une seule appli au lieu de tous les agendas compatibles.
            .setDataAndType(CalendarContract.Events.CONTENT_URI, EVENT_TYPE)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end)
            .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, allDay && followsAllDayConvention)
            .putExtra(CalendarContract.Events.TITLE, event.title)
            .putExtra(CalendarContract.Events.EVENT_LOCATION, event.location ?: if (event.format == EventFormat.ONLINE) "En ligne" else "")
            .putExtra(CalendarContract.Events.DESCRIPTION, event.url + if (event.price != null) "\nTarif : ${event.price}" else "")
    }

    /**
     * Fichier .ics pour tout agenda qui sait l'importer (Proton, Outlook, Thunderbird…).
     * Partagé via un FileProvider non exporté, limité au dossier cache/ics, en lecture seule
     * et pour la seule application choisie.
     */
    fun exportIcs(context: Context, events: List<Event>, title: String) {
        if (events.isEmpty()) return
        val uri = writeIcs(context, events, title)
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/calendar")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, title)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri(title, uri)
        val view = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "text/calendar")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        // Le sélecteur propose à la fois « ouvrir avec » (import direct) et « partager » (e-mail, fichiers…).
        val chooser = Intent.createChooser(send, "Ouvrir ou partager le fichier .ics")
            .putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(view))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            context.startActivity(chooser)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "Aucune application pour ouvrir le fichier", Toast.LENGTH_SHORT).show()
        }
    }

    /** Écrit le .ics dans cache/ics (anciens exports effacés) et renvoie son URI partageable. */
    private fun writeIcs(context: Context, events: List<Event>, title: String): Uri {
        val dir = File(context.cacheDir, "ics").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, IcsWriter.fileName(title))
        file.writeText(IcsWriter.write(events.map(::toEntry)), Charsets.UTF_8)
        return FileProvider.getUriForFile(context, context.packageName + ".files", file)
    }

    private fun isAllDay(e: Event): Boolean {
        val s = Formatters.zoned(e.startsAtEpochMs)
        val end = Formatters.zoned(e.endsAtEpochMs)
        return s.hour == 0 && s.minute == 0 && end.hour == 23 && end.minute == 59
    }

    private fun toEntry(e: Event) = IcsWriter.Entry(
        uid = e.id + "@cyberagenda",
        summary = e.title,
        description = buildString {
            append(e.url)
            e.price?.let { append("\nTarif : ").append(it) }
            if (e.description.isNotBlank()) append("\n\n").append(e.description.take(2_000))
        },
        location = e.location ?: if (e.format == EventFormat.ONLINE) "En ligne" else null,
        url = e.url,
        startEpochMs = e.startsAtEpochMs,
        endEpochMs = e.endsAtEpochMs,
        allDay = isAllDay(e),
    )
}
