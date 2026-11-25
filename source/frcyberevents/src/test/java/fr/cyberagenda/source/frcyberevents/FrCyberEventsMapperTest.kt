package fr.cyberagenda.source.frcyberevents

import fr.cyberagenda.core.formats.IcsEvent
import fr.cyberagenda.core.model.EventKind
import org.junit.Assert.assertEquals
import org.junit.Test

class FrCyberEventsMapperTest {
    private val ics = IcsEvent("ecw<2026>", "European Cyber Week", "Conferences, Exhibitors",
        "Couvent des Jacobins, Rennes (35)", "javascript:alert(1)", 0, 1, allDay = false)

    @Test fun sanitizesIdAndUrl() {
        val e = FrCyberEventsMapper.map(ics, "frcyberevents")!!
        assertEquals("frcyberevents:ecw2026", e.id)
        assertEquals("https://github.com/alexandredubois/cybersecurity-events", e.url)
        assertEquals(EventKind.CONFERENCE, e.kind)
    }

    @Test fun descriptionCleaned() = org.junit.Assert.assertEquals(
        "Site de l'évènement : https://www.hexatrust.com/\nAu programme : conférences, exposants.\nÉvénement consacré au cloud de confiance",
        FrCyberEventsMapper.describe("Site de l'évènement : https://www.hexatrust.com/\n Conferences, Exhibitors\n Événement consacré au cloud de confiance"),
    )
}
