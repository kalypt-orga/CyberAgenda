package fr.cyberagenda.source.agendadulibre

import fr.cyberagenda.core.model.EventFormat
import fr.cyberagenda.core.model.EventKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgendaDuLibreMapperTest {
    private val dto = AdlEventDto(
        id = 1, title = "Café vie privée", description = "<p>Venez <b>apprendre</b></p>",
        startTime = "2026-12-02T17:00:00.000Z", endTime = "2026-12-02T20:00:00.000Z",
        placeName = "Médiathèque", address = "1 rue de Brest", city = "Rennes", url = "http://insecure.example",
        regionId = 6, region = "Bretagne", latitude = 48.1, longitude = -1.6,
    )

    @Test fun mapsFrenchEvent() {
        val e = AgendaDuLibreMapper.map(dto, "agendadulibre")!!
        assertEquals("Venez apprendre", e.description)
        assertEquals("Médiathèque, 1 rue de Brest, Rennes", e.location)
        assertEquals(EventKind.MEETUP, e.kind)
        assertTrue(e.beginnerFriendly)
        // URL http refusée → repli sur la fiche Agenda du Libre
        assertEquals("https://www.agendadulibre.org/events/1", e.url)
    }

    @Test fun dropsForeignRegion() = assertNull(AgendaDuLibreMapper.map(dto.copy(regionId = 139, region = "Wallonie"), "agendadulibre"))

    @Test fun onlineHasNoLocation() {
        val e = AgendaDuLibreMapper.map(dto.copy(regionId = 999, region = "Internet"), "agendadulibre")!!
        assertEquals(EventFormat.ONLINE, e.format)
        assertNull(e.location); assertNull(e.latitude)
    }
}
