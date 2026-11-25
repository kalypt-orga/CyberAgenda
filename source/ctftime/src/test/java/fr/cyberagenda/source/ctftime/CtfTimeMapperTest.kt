package fr.cyberagenda.source.ctftime

import fr.cyberagenda.core.model.EventFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CtfTimeMapperTest {
    private val online = CtfTimeEventDto(
        id = 42, title = "Test CTF", start = "2026-12-01T10:00:00+00:00",
        finish = "2026-12-02T10:00:00+00:00", onsite = false,
        url = "https://ctf.example.org/", ctftimeUrl = "https://ctftime.org/event/42/",
        restrictions = "Open",
    )

    @Test fun onlineKept() {
        val e = CtfTimeMapper.map(online, "ctftime")!!
        assertEquals("ctftime:42", e.id)
        assertEquals(EventFormat.ONLINE, e.format)
        assertEquals("https://ctf.example.org/", e.url)
    }

    @Test fun onsiteInFranceKept() =
        assertNotNull(CtfTimeMapper.map(online.copy(onsite = true, location = "Rennes, France"), "ctftime"))

    @Test fun onsiteAbroadDropped() =
        assertNull(CtfTimeMapper.map(online.copy(onsite = true, location = "Las Vegas, USA"), "ctftime"))

    @Test fun prequalifiedDropped() =
        assertNull(CtfTimeMapper.map(online.copy(restrictions = "Prequalified"), "ctftime"))

    @Test fun httpUrlFallsBackToCtfTime() =
        assertEquals("https://ctftime.org/event/42/", CtfTimeMapper.map(online.copy(url = "http://x.org"), "ctftime")!!.url)

    @Test fun badDateDropped() = assertNull(CtfTimeMapper.map(online.copy(start = "demain"), "ctftime"))
}
