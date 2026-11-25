package fr.cyberagenda.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AddressHintsTest {
    private fun ev(location: String?, desc: String, format: EventFormat = EventFormat.ONSITE, lat: Double? = null) = Event(
        id = "s:1", sourceId = "s", title = "T", description = desc, startsAtEpochMs = 0, endsAtEpochMs = 1,
        format = format, kind = EventKind.CTF, url = "https://a.org", location = location, latitude = lat, longitude = lat,
    )

    @Test fun oteriaAddressFromDescription() {
        val e = AddressHints.apply(ev("France, Gennevilliers", "Lieu : 1 Rue du 19 Mars 1962, 92230 Gennevilliers, France\nDurée : 6 h"))
        assertEquals("1 Rue du 19 Mars 1962, 92230 Gennevilliers", e.location)
        assertNull(e.latitude) // recalculé par le géocodage
    }

    @Test fun preciseLocationKept() {
        val loc = "Ressourcerie RESISTES, 10 rue Richard Waddington, Darnétal"
        assertEquals(loc, AddressHints.apply(ev(loc, "RDV au 5 avenue Foch, 75016 Paris")).location)
    }

    @Test fun onlineIgnored() = assertNull(AddressHints.apply(ev(null, "Siège : 1 rue X, 75001 Paris", EventFormat.ONLINE)).location)

    @Test fun noAddressNoChange() = assertEquals("Rennes (35)", AddressHints.apply(ev("Rennes (35)", "Conférences et ateliers")).location)

    @Test fun missingLocationFilled() =
        assertEquals("12 bis place Carnot, 84000 Avignon", AddressHints.apply(ev(null, "Rendez-vous 12 bis place Carnot, 84000 Avignon.")).location)

    // --- Garde-fous de l'audit (DATA-07) ---

    @Test fun addressInOtherCityIgnored() = assertEquals("Rennes (35)",
        AddressHints.apply(ev("Rennes (35)", "Parking conseillé au 2 rue du Parking, 35400 Saint-Malo.")).location)

    @Test fun sameCityAddressStillUsed() = assertEquals("4 rue de Paris, 35000 Rennes",
        AddressHints.apply(ev("Rennes (35)", "Parking au 2 rue X, 35400 Saint-Malo. Lieu : 4 rue de Paris, 35000 Rennes.")).location)

    @Test fun sourceCoordinatesWin() {
        val e = AddressHints.apply(ev("Rennes", "Lieu : 4 rue de Paris, 35000 Rennes", lat = 48.1))
        assertEquals("Rennes", e.location)
        assertEquals(48.1, e.latitude!!, 0.0)
    }
}
