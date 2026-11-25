package fr.cyberagenda.core.formats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class IcsWriterTest {
    private val paris = ZoneId.of("Europe/Paris")
    private fun at(d: Int, h: Int) = ZonedDateTime.of(2027, 3, d, h, 0, 0, 0, paris).toInstant().toEpochMilli()
    private val entry = IcsWriter.Entry(
        uid = "selection:ph0wn-2027@cyberagenda", summary = "Ph0wn CTF, édition 2027; IoT",
        description = "Ligne 1\nLigne 2 : « défis » \\ fin", location = "Campus SophiaTech, Biot (06)",
        url = "https://ph0wn.org/", startEpochMs = at(12, 9), endEpochMs = at(13, 18), allDay = false,
    )

    @Test fun roundTripWithParser() {
        val parsed = IcsParser.parse(IcsWriter.write(listOf(entry))).single()
        assertEquals(entry.summary, parsed.summary)
        assertEquals(entry.description, parsed.description)
        assertEquals(entry.location, parsed.location)
        assertEquals(entry.startEpochMs, parsed.startEpochMs)
        assertEquals(entry.endEpochMs, parsed.endEpochMs)
    }

    @Test fun allDayEndIsExclusive() {
        val ics = IcsWriter.write(listOf(entry.copy(startEpochMs = at(12, 0), endEpochMs = at(13, 23), allDay = true)))
        assertTrue(ics.contains("DTSTART;VALUE=DATE:20270312"))
        assertTrue(ics.contains("DTEND;VALUE=DATE:20270314"))
    }

    @Test fun linesFoldedAt75Octets() {
        val ics = IcsWriter.write(listOf(entry.copy(description = "é".repeat(200))))
        ics.split("\r\n").forEach { assertTrue("ligne trop longue : ${it.length}", it.toByteArray(Charsets.UTF_8).size <= 75) }
        assertEquals("é".repeat(200), IcsParser.parse(ics).single().description) // dépliage sans corruption UTF-8
    }

    @Test fun usesCrlf() = assertTrue(IcsWriter.write(listOf(entry)).startsWith("BEGIN:VCALENDAR\r\nVERSION:2.0\r\n"))

    @Test fun safeFileName() = assertEquals("ph0wn-ctf-edition-2027-iot.ics", IcsWriter.fileName("Ph0wn CTF, édition 2027; IoT"))
}
