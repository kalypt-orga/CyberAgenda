package fr.cyberagenda.core.formats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class IcsParserTest {
    private val paris = ZoneId.of("Europe/Paris")

    private val sample = """
        BEGIN:VCALENDAR
        BEGIN:VEVENT
        UID:ecw2026
        DTSTART;TZID=Europe/Paris:20261116T000000
        DTEND;TZID=Europe/Paris:20261119T235959
        SUMMARY:European Cyber Week (ECW)
        LOCATION:Couvent des Jacobins\, Rennes (35)
        URL:https://www.european-cyber-week.eu/
        DESCRIPTION:Site : https://www.european-cyber-week.eu/\n Conferences\, Exh
         ibitors
        END:VEVENT
        BEGIN:VEVENT
        UID:sstic
        DTSTART;VALUE=DATE:20270603
        DTEND;VALUE=DATE:20270606
        SUMMARY:SSTIC
        END:VEVENT
        BEGIN:VEVENT
        SUMMARY:Sans UID ni date
        END:VEVENT
        END:VCALENDAR
    """.trimIndent()

    @Test fun parsesEventsAndSkipsInvalid() {
        val events = IcsParser.parse(sample)
        assertEquals(2, events.size)
        val ecw = events[0]
        assertEquals("Couvent des Jacobins, Rennes (35)", ecw.location)
        assertTrue(ecw.description.endsWith("Conferences, Exhibitors"))
        assertEquals(ZonedDateTime.of(2026, 11, 16, 0, 0, 0, 0, paris).toInstant().toEpochMilli(), ecw.startEpochMs)
    }

    @Test fun allDayEndIsExclusive() {
        val sstic = IcsParser.parse(sample)[1]
        assertTrue(sstic.allDay)
        val lastDay = ZonedDateTime.of(2027, 6, 5, 23, 59, 59, 0, paris).toInstant().toEpochMilli()
        assertEquals(lastDay + 999, sstic.endEpochMs)
    }

    @Test fun utcTimes() {
        val e = IcsParser.parse("BEGIN:VEVENT\nUID:a\nDTSTART:20261201T100000Z\nEND:VEVENT")[0]
        assertEquals(1796119200000L, e.startEpochMs)
    }

    @Test fun maxEventsBounded() {
        val many = (1..50).joinToString("\n") { "BEGIN:VEVENT\nUID:$it\nDTSTART:20261201T100000Z\nEND:VEVENT" }
        assertEquals(10, IcsParser.parse(many, maxEvents = 10).size)
    }
}
