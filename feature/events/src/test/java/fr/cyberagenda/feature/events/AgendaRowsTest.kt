package fr.cyberagenda.feature.events

import fr.cyberagenda.core.model.Event
import fr.cyberagenda.core.model.EventFormat
import fr.cyberagenda.core.model.EventKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class AgendaRowsTest {
    private val paris = ZoneId.of("Europe/Paris")
    private fun at(d: Int) = ZonedDateTime.of(2026, 11, d, 10, 0, 0, 0, paris).toInstant().toEpochMilli()
    private fun item(id: String, day: Int, kind: EventKind = EventKind.CTF, format: EventFormat = EventFormat.ONLINE) = EventItem(
        Event(id = "s:$id", sourceId = "s", title = id, description = "", startsAtEpochMs = at(day), endsAtEpochMs = at(day) + 1,
            format = format, kind = kind, url = "https://a.org"),
        sourceName = "S", favorite = false,
    )

    @Test fun onlineCtfOfSameWeekAreGrouped() {
        // 23 nov. 2026 = lundi ; 27 et 28 dans la même semaine, 30 la suivante
        val rows = buildAgendaRows(listOf(item("a", 27), item("b", 28), item("c", 30), item("conf", 26, EventKind.CONFERENCE, EventFormat.ONSITE)), true, emptySet(), paris)
        assertEquals(3, rows.size)
        val group = rows.filterIsInstance<AgendaRow.OnlineCtfWeek>().single()
        assertEquals(LocalDate.of(2026, 11, 23), group.weekStart)
        assertEquals(listOf("a", "b"), group.items.map { it.event.title })
        assertEquals("conf", (rows[0] as AgendaRow.Single).item.event.title) // ordre chronologique conservé
    }

    @Test fun onsiteCtfNotGrouped() {
        val rows = buildAgendaRows(listOf(item("a", 27, format = EventFormat.ONSITE), item("b", 28, format = EventFormat.ONSITE)), true, emptySet(), paris)
        assertTrue(rows.all { it is AgendaRow.Single })
    }

    @Test fun groupingCanBeDisabled() =
        assertEquals(2, buildAgendaRows(listOf(item("a", 27), item("b", 28)), false, emptySet(), paris).size)

    @Test fun expandedStateKept() {
        val rows = buildAgendaRows(listOf(item("a", 27), item("b", 28)), true, setOf(LocalDate.of(2026, 11, 23)), paris)
        assertTrue((rows.single() as AgendaRow.OnlineCtfWeek).expanded)
    }
}
