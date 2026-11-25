package fr.cyberagenda.core.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FrenchGeocoderTest {
    private val table = """
        # nom	dep	lat	lon	pop
        Paris	75	48.8589	2.3470	2103778
        Rennes	35	48.1159	-1.6884	230890
        Versailles	78	48.8040	2.1200	85000
        Saint-Denis	93	48.9300	2.3500	113000
        Saint-Denis	974	-20.8800	55.4500	153000
        Toulouse	31	43.6000	1.4400	500000
        Vanves	92	48.8214	2.2869	28622
        Avignon	84	43.9500	4.8100	91000
        Darnétal	76	49.4400	1.1500	9500
    """.trimIndent()
    private val geo = FrenchGeocoder { table }

    @Test fun cityWithDepartment() = assertEquals(48.1159, geo.locate("Couvent des Jacobins, Rennes (35)")!!.latitude, 1e-6)
    @Test fun departmentDisambiguates() = assertEquals(55.45, geo.locate("Stade, Saint-Denis (974)")!!.longitude, 1e-6)
    @Test fun departmentFilterBeatsOtherCity() = assertEquals(2.347, geo.locate("Paris Expo Porte de Versailles, Paris (75)")!!.longitude, 1e-6)
    @Test fun fallbackToDepartment() = assertEquals(43.6, geo.locate("MEETT, Aussonne (31)")!!.latitude, 1e-6)
    @Test fun postalCode() = assertEquals(2.2869, geo.locate("2 rue X 92170 Vanves")!!.longitude, 1e-6)
    @Test fun accentsAndCase() = assertEquals(48.1159, geo.locate("RENNES, France")!!.latitude, 1e-6)
    @Test fun searchByPrefixSortedByPopulation() = assertEquals(listOf("974", "93"), geo.search("saint-d").map { it.department })
    @Test fun searchIgnoresAccents() = assertEquals("Rennes", geo.search("RENN")[0].name)
    @Test fun distanceParisToulouse() = assertEquals(589.0, geo.locate("Paris (75)")!!.distanceKm(geo.locate("Toulouse (31)")!!), 10.0)
    @Test fun searchTooShort() = assertEquals(0, geo.search("p").size)
    @Test fun unknown() = assertNull(geo.locate("Las Vegas, USA"))

    // --- Libellé court des miniatures : « (84) Avignon » ---
    @Test fun labelCityWithDepartment() = assertEquals("(35) Rennes", geo.shortLabel("Couvent des Jacobins, Rennes (35)"))
    @Test fun labelPostalAddress() = assertEquals("(84) Avignon", geo.shortLabel("12 bis place Carnot, 84000 Avignon"))
    @Test fun labelPrefersLastPart() = assertEquals("(31) Toulouse", geo.shortLabel("Parc des Expositions de Toulouse, Toulouse (31)"))
    @Test fun labelUnknownCityKeepsDepartment() = assertEquals("(31) Aussonne", geo.shortLabel("MEETT, Parc des Expositions de Toulouse, Aussonne (31)"))
    @Test fun labelAccents() = assertEquals("(76) Darnétal", geo.shortLabel("Ressourcerie RESISTES, 10 rue Richard Waddington, Darnétal"))
    @Test fun labelIgnoresCountry() = assertEquals("(75) Paris", geo.shortLabel("Paris, France"))
    @Test fun labelUnknownPlace() = assertEquals("Las Vegas", geo.shortLabel("Las Vegas"))
}
