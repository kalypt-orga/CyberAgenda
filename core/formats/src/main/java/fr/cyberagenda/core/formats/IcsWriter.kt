package fr.cyberagenda.core.formats

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Génère un fichier iCalendar (RFC 5545) importable dans n'importe quelle application d'agenda
 * (Google, Outlook, Proton, Thunderbird…). Échappement des valeurs texte et repli des lignes
 * à 75 octets conformes à la norme.
 */
object IcsWriter {
    data class Entry(
        val uid: String,
        val summary: String,
        val description: String,
        val location: String?,
        val url: String?,
        val startEpochMs: Long,
        val endEpochMs: Long,
        val allDay: Boolean,
    )

    private val utc = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)
    private val date = DateTimeFormatter.ofPattern("yyyyMMdd")

    fun write(entries: List<Entry>, nowEpochMs: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.of("Europe/Paris")): String {
        val lines = mutableListOf("BEGIN:VCALENDAR", "VERSION:2.0", "PRODID:-//CyberAgenda//FR", "CALSCALE:GREGORIAN", "METHOD:PUBLISH")
        for (e in entries) {
            lines += "BEGIN:VEVENT"
            lines += "UID:" + escape(e.uid)
            lines += "DTSTAMP:" + utc.format(Instant.ofEpochMilli(nowEpochMs))
            if (e.allDay) {
                val start = Instant.ofEpochMilli(e.startEpochMs).atZone(zone).toLocalDate()
                val last = Instant.ofEpochMilli(e.endEpochMs).atZone(zone).toLocalDate()
                lines += "DTSTART;VALUE=DATE:" + start.format(date)
                // DTEND d'un événement « journée entière » est exclusif.
                lines += "DTEND;VALUE=DATE:" + maxOf(last, start).plusDays(1).format(date)
            } else {
                lines += "DTSTART:" + utc.format(Instant.ofEpochMilli(e.startEpochMs))
                lines += "DTEND:" + utc.format(Instant.ofEpochMilli(maxOf(e.endEpochMs, e.startEpochMs)))
            }
            lines += "SUMMARY:" + escape(e.summary)
            e.location?.let { lines += "LOCATION:" + escape(it) }
            e.url?.let { lines += "URL:" + it.filter { c -> c > ' ' } }
            lines += "DESCRIPTION:" + escape(e.description)
            lines += "END:VEVENT"
        }
        lines += "END:VCALENDAR"
        return lines.joinToString("") { fold(it) + "\r\n" }
    }

    /** RFC 5545 §3.3.11 : \ ; , et retours à la ligne sont échappés. */
    internal fun escape(s: String): String = buildString {
        for (c in s) when (c) {
            '\\' -> append("\\\\")
            ';' -> append("\\;")
            ',' -> append("\\,")
            '\n' -> append("\\n")
            '\r' -> Unit
            else -> if (c >= ' ' || c == '\t') append(c)
        }
    }

    /** RFC 5545 §3.1 : lignes de 75 octets max, suite précédée d'un espace (sans couper un caractère UTF-8). */
    internal fun fold(line: String): String {
        val out = StringBuilder()
        var bytes = 0
        var i = 0
        while (i < line.length) {
            val cp = line.codePointAt(i)
            val len = String(Character.toChars(cp)).toByteArray(Charsets.UTF_8).size
            if (bytes + len > 75) {
                out.append("\r\n ")
                bytes = 1
            }
            out.appendCodePoint(cp)
            bytes += len
            i += Character.charCount(cp)
        }
        return out.toString()
    }

    /** Nom de fichier sûr : lettres, chiffres et tirets uniquement. */
    fun fileName(title: String): String =
        java.text.Normalizer.normalize(title.lowercase(), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "").replace(Regex("[^a-z0-9]+"), "-").trim('-').take(40).ifEmpty { "evenement" } + ".ics"

}
