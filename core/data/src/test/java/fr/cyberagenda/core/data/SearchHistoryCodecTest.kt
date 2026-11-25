package fr.cyberagenda.core.data

import fr.cyberagenda.core.model.RecentPlace
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchHistoryCodecTest {
    @Test fun placesRoundTrip() {
        val places = listOf(RecentPlace("Avignon", "84", 43.95, 4.81, 25), RecentPlace("Saint-Denis", "974", -20.88, 55.45, 10))
        assertEquals(places, SearchHistory.decodePlaces(SearchHistory.encodePlaces(places)))
    }
    @Test fun corruptLinesIgnored() = assertEquals(1, SearchHistory.decodePlaces("x\ty\nRennes\t35\t48.1\t-1.6\t50\nA\t1\t999\t0\t10").size)
    @Test fun tabsInNamesNeutralised() =
        assertEquals("Ville bizarre", SearchHistory.decodePlaces(SearchHistory.encodePlaces(listOf(RecentPlace("Ville\tbizarre", "35", 1.0, 1.0, 10))))[0].name)
    @Test fun queriesRoundTrip() = assertEquals(listOf("ctf", "Rennes"), SearchHistory.decodeQueries(SearchHistory.encodeQueries(listOf("ctf", "Rennes"))))
}
