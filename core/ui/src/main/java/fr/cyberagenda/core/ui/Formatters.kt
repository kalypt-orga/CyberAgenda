package fr.cyberagenda.core.ui

import fr.cyberagenda.core.model.EventFormat
import fr.cyberagenda.core.model.EventKind
import fr.cyberagenda.core.model.SourceError
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

object Formatters {
    val locale: Locale = Locale.FRANCE
    private val day = DateTimeFormatter.ofPattern("EEE d MMM yyyy", locale)
    private val dayShort = DateTimeFormatter.ofPattern("EEE d MMM", locale)
    private val dayTime = DateTimeFormatter.ofPattern("EEE d MMM yyyy 'à' HH:mm", locale)
    private val time = DateTimeFormatter.ofPattern("HH:mm", locale)

    fun zoned(ms: Long): ZonedDateTime = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())

    fun dateRange(startMs: Long, endMs: Long): String {
        val start = zoned(startMs)
        val end = zoned(endMs)
        return when {
            // Journée sans horaire précis (la source ne donne qu'une date).
            isAllDay(start, end) && start.toLocalDate() == end.toLocalDate() -> "${start.format(day)} · $UNKNOWN_TIME"
            // Fin inconnue (durée nulle) : jamais « 00:00 – 00:00 ».
            startMs == endMs && isMidnight(start) -> "${start.format(day)} · $UNKNOWN_TIME"
            startMs == endMs -> start.format(dayTime)
            !isMultiDay(startMs, endMs) -> "${start.format(dayTime)} – ${endTime(startMs, endMs)}"
            else -> "Du ${start.format(day)} au ${zoned(displayEnd(startMs, endMs)).format(day)}"
        }
    }

    private val monthAbbr = DateTimeFormatter.ofPattern("MMM", locale)

    /**
     * Fin utilisée pour les jours : une fin à 00:00 pile compte pour la veille (un CTF de
     * 16:00 à minuit ne dure qu'une soirée, pas deux jours). Les heures affichées ne changent pas.
     */
    fun displayEnd(startMs: Long, endMs: Long): Long {
        if (endMs <= startMs) return endMs
        val t = zoned(endMs)
        return if (t.hour == 0 && t.minute == 0 && t.second == 0 && t.nano == 0) endMs - 1 else endMs
    }

    fun isMultiDay(startMs: Long, endMs: Long) = zoned(startMs).toLocalDate() != zoned(displayEnd(startMs, endMs)).toLocalDate()

    /** « du 26 au 27 nov. » ou « du 30 nov. au 2 déc. » */
    fun daySpan(startMs: Long, endMs: Long): String {
        val s = zoned(startMs).toLocalDate()
        val e = zoned(displayEnd(startMs, endMs)).toLocalDate()
        return if (s.year == e.year && s.month == e.month) "du ${s.dayOfMonth} au ${e.dayOfMonth} ${e.format(monthAbbr)}"
        else "du ${s.dayOfMonth} ${s.format(monthAbbr)} au ${e.dayOfMonth} ${e.format(monthAbbr)}"
    }

    /** Ligne courte pour les cartes : « 18:00 – 20:00 » ou « du 26 au 27 nov. ». */
    fun shortSpan(startMs: Long, endMs: Long): String {
        val start = zoned(startMs)
        val end = zoned(endMs)
        return when {
            isMultiDay(startMs, endMs) -> daySpan(startMs, endMs)
            isAllDay(start, end) -> UNKNOWN_TIME
            // Fin inconnue (durée nulle) : jamais « 00:00 – 00:00 ».
            startMs == endMs && isMidnight(start) -> UNKNOWN_TIME
            startMs == endMs -> "à ${start.format(time)}"
            else -> "${start.format(time)} – ${endTime(startMs, endMs)}"
        }
    }

    /** Libellé d'un événement daté sans horaire : la plupart des sources ne donnent que le jour. */
    const val UNKNOWN_TIME = "horaire inconnu"

    /** Heure de fin ; une journée complète de minuit à minuit finit à « 24:00 », jamais « 00:00 – 00:00 ». */
    private fun endTime(startMs: Long, endMs: Long): String {
        val start = zoned(startMs)
        val end = zoned(endMs)
        return if (isMidnight(start) && isMidnight(end) && displayEnd(startMs, endMs) != endMs) "24:00" else end.format(time)
    }

    private fun isMidnight(t: ZonedDateTime) = t.hour == 0 && t.minute == 0

    private fun isAllDay(start: ZonedDateTime, end: ZonedDateTime) =
        start.hour == 0 && start.minute == 0 && end.hour == 23 && end.minute == 59

    fun dayNumber(ms: Long) = zoned(ms).dayOfMonth.toString()
    fun monthShort(ms: Long) = zoned(ms).month.getDisplayName(TextStyle.SHORT, locale).trimEnd('.').uppercase(locale)
    fun weekdayShort(ms: Long) = zoned(ms).dayOfWeek.getDisplayName(TextStyle.SHORT, locale).trimEnd('.').uppercase(locale)
    fun monthYear(ym: YearMonth) = ym.month.getDisplayName(TextStyle.FULL_STANDALONE, locale)
        .replaceFirstChar { it.titlecase(locale) } + " " + ym.year
    fun dayLong(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("EEEE d MMMM", locale))
        .replaceFirstChar { it.titlecase(locale) }

    fun dateTime(ms: Long): String = zoned(ms).format(dayTime)

    fun format(f: EventFormat) = when (f) {
        EventFormat.ONLINE -> "En ligne"
        EventFormat.ONSITE -> "Sur place"
        EventFormat.HYBRID -> "Hybride"
    }

    fun kind(k: EventKind) = when (k) {
        EventKind.CTF -> "CTF"
        EventKind.CONFERENCE -> "Conférence"
        EventKind.MEETUP -> "Rencontre"
        EventKind.WORKSHOP -> "Atelier"
        EventKind.OTHER -> "Événement"
    }

    fun error(e: SourceError) = when (e) {
        SourceError.NETWORK -> "Injoignable (réseau ou certificat refusé)"
        SourceError.INVALID_DATA -> "Données reçues invalides"
        SourceError.SIGNATURE -> "Signature invalide : données ignorées"
        SourceError.UNKNOWN -> "Erreur inconnue"
    }

    fun count(n: Int, singular: String, plural: String = singular + "s") = "$n ${if (n > 1) plural else singular}"
}
