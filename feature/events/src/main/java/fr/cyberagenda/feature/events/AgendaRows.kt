package fr.cyberagenda.feature.events

import fr.cyberagenda.core.model.EventFormat
import fr.cyberagenda.core.model.EventKind
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** Une ligne de l'agenda : un événement, ou un groupe de CTF en ligne d'une même semaine. */
sealed interface AgendaRow {
    val key: String
    val startsAtEpochMs: Long

    data class Single(val item: EventItem) : AgendaRow {
        override val key get() = item.event.id
        override val startsAtEpochMs get() = item.event.startsAtEpochMs
    }

    data class OnlineCtfWeek(val weekStart: LocalDate, val items: List<EventItem>, val expanded: Boolean) : AgendaRow {
        override val key get() = "ctf-week-$weekStart"
        override val startsAtEpochMs get() = items.first().event.startsAtEpochMs
        val favorites: Int get() = items.count { it.favorite }
    }
}

/**
 * Construit les lignes de l'agenda :
 *  - les CTF en ligne (nombreux, souvent internationaux) sont regroupés par semaine dès qu'il y en a
 *    au moins [MIN_GROUP] la même semaine ;
 *  - le reste est affiché tel quel, par ordre chronologique.
 */
internal fun buildAgendaRows(
    items: List<EventItem>,
    groupOnlineCtf: Boolean,
    expandedWeeks: Set<LocalDate>,
    zone: ZoneId = ZoneId.systemDefault(),
): List<AgendaRow> {
    if (!groupOnlineCtf) return items.map { AgendaRow.Single(it) }
    val (ctf, others) = items.partition { it.event.kind == EventKind.CTF && it.event.format == EventFormat.ONLINE }
    val rows = others.map<EventItem, AgendaRow> { AgendaRow.Single(it) }.toMutableList()
    ctf.groupBy { weekOf(it.event.startsAtEpochMs, zone) }.forEach { (week, list) ->
        if (list.size >= MIN_GROUP) rows += AgendaRow.OnlineCtfWeek(week, list.sortedBy { it.event.startsAtEpochMs }, week in expandedWeeks)
        else list.forEach { rows += AgendaRow.Single(it) }
    }
    return rows.sortedBy { it.startsAtEpochMs }
}

internal fun weekOf(epochMs: Long, zone: ZoneId): LocalDate =
    Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

internal const val MIN_GROUP = 2
