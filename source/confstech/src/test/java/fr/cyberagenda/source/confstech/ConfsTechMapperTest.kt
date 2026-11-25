package fr.cyberagenda.source.confstech

import fr.cyberagenda.core.model.EventFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConfsTechMapperTest {
    private val dto = ConfDto("Big Sec Conf", "https://conf.example.org", "2027-03-10", "2027-03-11", "Berlin", "Germany", online = true)

    @Test fun onlineAbroadKept() = assertEquals(EventFormat.ONLINE, ConfsTechMapper.map(dto, "confstech")!!.format)
    @Test fun onsiteAbroadDropped() = assertNull(ConfsTechMapper.map(dto.copy(online = false), "confstech"))
    @Test fun franceOnsite() {
        val e = ConfsTechMapper.map(dto.copy(online = false, city = "Paris", country = "France"), "confstech")!!
        assertEquals(EventFormat.ONSITE, e.format); assertEquals("Paris", e.location)
    }
    @Test fun idIsSlug() = assertEquals("confstech:big-sec-conf-2027-03-10", ConfsTechMapper.map(dto, "confstech")!!.id)

    @Test fun offTopicDropped() {
        assertNull(ConfsTechMapper.map(dto.copy(name = "API Conference Berlin"), "confstech"))
        assertNull(ConfsTechMapper.map(dto.copy(name = "All Day AI"), "confstech"))
        org.junit.Assert.assertNotNull(ConfsTechMapper.map(dto.copy(name = "Unhacked 360 Germany: FutureSec Summit"), "confstech"))
        org.junit.Assert.assertNotNull(ConfsTechMapper.map(dto.copy(name = "IT Security Summit Munich"), "confstech"))
    }
}
