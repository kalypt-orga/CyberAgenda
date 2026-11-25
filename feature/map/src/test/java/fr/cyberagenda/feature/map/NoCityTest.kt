package fr.cyberagenda.feature.map

import fr.cyberagenda.core.geo.City
import fr.cyberagenda.core.geo.GeoPoint
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Message « Aucune commune trouvée » : ni clignotement pendant la frappe, ni faux positif. */
class NoCityTest {
    private val none = emptyList<City>()
    private val nimes = listOf(City("Nîmes", "30", GeoPoint(43.8, 4.36)))

    @Test fun firstLetterThenSecondDoesNotFlash() = assertFalse(noCityFor("Ni", searched = "N", suggestions = none))
    @Test fun shownWhenRealSearchFoundNothing() = assertTrue(noCityFor("Xyz", searched = "Xyz", suggestions = none))
    @Test fun staysWhileTypingFurther() = assertTrue(noCityFor("Xyzz", searched = "Xyz", suggestions = none))
    @Test fun staysOnBackspaceUntilNewResult() = assertTrue(noCityFor("Xy", searched = "Xyz", suggestions = none))
    @Test fun hiddenWhenFieldNearlyEmpty() = assertFalse(noCityFor("X", searched = "Xyz", suggestions = none))
    @Test fun notShownWithResults() = assertFalse(noCityFor("Nim", searched = "Nim", suggestions = nimes))
    @Test fun notShownBeforeAnySearch() = assertFalse(noCityFor("Ni", searched = null, suggestions = none))
}
