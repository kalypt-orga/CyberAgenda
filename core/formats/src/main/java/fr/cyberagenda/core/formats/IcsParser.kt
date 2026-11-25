package fr.cyberagenda.core.formats

import java.time.DateTimeException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Événement iCalendar (RFC 5545) réduit aux champs utiles. */
data class IcsEvent(
    val uid: String,
    val summary: String,
    val description: String,
    val location: String,
    val url: String?,
    val startEpochMs: Long,
    val endEpochMs: Long,
    val allDay: Boolean,
)

/**
 * Analyseur iCalendar volontairement minimal et défensif : il ne gère que les VEVENT,
 * borne le nombre d'événements et la taille des lignes, et ignore tout ce qu'il ne
 * comprend pas au lieu d'échouer (pas de récurrence RRULE, pas de pièces jointes).
 */
object IcsParser {
    private const val MAX_LINE = 10_000
    private val defaultZone: ZoneId = ZoneId.of("Europe/Paris")
    private val dateFmt = DateTimeFormatter.ofPattern("yyyyMMdd")
    private val dateTimeFmt = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")

    fun parse(text: String, maxEvents: Int = 1_000): List<IcsEvent> {
        val events = mutableListOf<IcsEvent>()
        var current: MutableMap<String, Pair<Map<String, String>, String>>? = null
        for (line in unfold(text)) {
            if (line.length > MAX_LINE) continue
            when {
                line.equals("BEGIN:VEVENT", ignoreCase = true) -> current = mutableMapOf()
                line.equals("END:VEVENT", ignoreCase = true) -> {
                    current?.let { props -> toEvent(props)?.let(events::add) }
                    current = null
                    if (events.size >= maxEvents) break
                }
                current != null -> parseProperty(line)?.let { (name, params, value) ->
                    // On garde la première occurrence de chaque propriété.
                    current.putIfAbsent(name, params to value)
                }
            }
        }
        return events
    }

    /** RFC 5545 §3.1 : une ligne qui commence par un espace ou une tabulation prolonge la précédente. */
    internal fun unfold(text: String): List<String> {
        val out = ArrayList<String>()
        val sb = StringBuilder()
        for (raw in text.split("\r\n", "\n", "\r")) {
            if (raw.startsWith(" ") || raw.startsWith("\t")) {
                if (sb.length < MAX_LINE) sb.append(raw, 1, raw.length)
            } else {
                if (sb.isNotEmpty()) out += sb.toString()
                sb.setLength(0)
                sb.append(raw)
            }
        }
        if (sb.isNotEmpty()) out += sb.toString()
        return out
    }

    private fun parseProperty(line: String): Triple<String, Map<String, String>, String>? {
        // Le séparateur nom/valeur est le premier ':' hors guillemets.
        var inQuotes = false
        var colon = -1
        for ((i, c) in line.withIndex()) {
            if (c == '"') inQuotes = !inQuotes
            if (c == ':' && !inQuotes) { colon = i; break }
        }
        if (colon <= 0) return null
        val head = line.substring(0, colon).split(';')
        val params = head.drop(1).mapNotNull { p ->
            val eq = p.indexOf('=')
            if (eq <= 0) null else p.substring(0, eq).uppercase() to p.substring(eq + 1).trim('"')
        }.toMap()
        return Triple(head[0].uppercase(), params, line.substring(colon + 1))
    }

    private fun toEvent(p: Map<String, Pair<Map<String, String>, String>>): IcsEvent? {
        val (startParams, startRaw) = p["DTSTART"] ?: return null
        val allDay = startParams["VALUE"].equals("DATE", true) || startRaw.length == 8
        val start = parseInstant(startRaw, startParams, startOfDay = true) ?: return null
        val end = p["DTEND"]?.let { (params, raw) ->
            if (allDay && raw.length == 8) {
                // DTEND d'un événement « journée entière » est exclusif.
                parseInstant(raw, params, startOfDay = true)?.minus(1)
            } else {
                parseInstant(raw, params, startOfDay = false)
            }
        } ?: if (allDay) start + DAY_MS - 1 else start

        return IcsEvent(
            uid = p["UID"]?.second?.trim().orEmpty().ifEmpty { return null },
            summary = unescape(p["SUMMARY"]?.second.orEmpty()),
            description = unescape(p["DESCRIPTION"]?.second.orEmpty()),
            location = unescape(p["LOCATION"]?.second.orEmpty()),
            url = p["URL"]?.second?.trim(),
            startEpochMs = start,
            endEpochMs = maxOf(start, end),
            allDay = allDay,
        )
    }

    private fun parseInstant(raw: String, params: Map<String, String>, startOfDay: Boolean): Long? = try {
        val zone = params["TZID"]?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: defaultZone
        val v = raw.trim()
        when {
            v.length == 8 -> LocalDate.parse(v, dateFmt).atStartOfDay(zone).toInstant().toEpochMilli()
            v.endsWith("Z") -> LocalDateTime.parse(v.dropLast(1), dateTimeFmt).toInstant(ZoneOffset.UTC).toEpochMilli()
            else -> LocalDateTime.parse(v, dateTimeFmt).atZone(zone).toInstant().toEpochMilli()
        }
    } catch (_: DateTimeException) {
        null
    }

    /** RFC 5545 §3.3.11 : \n, \, , \; et \\ sont échappés dans les valeurs texte. */
    internal fun unescape(s: String): String {
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (val n = s[i + 1]) {
                    'n', 'N' -> sb.append('\n')
                    else -> sb.append(n)
                }
                i += 2
            } else {
                sb.append(c); i++
            }
        }
        return sb.toString()
    }

    private const val DAY_MS = 24L * 60 * 60 * 1000
}
