package fr.cyberagenda.feature.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShiftSouthTest {
    @Test fun zeroShiftIsIdentity() = assertEquals(43.9493, shiftSouth(43.9493, 7.8, 0.0), 1e-9)

    @Test fun shiftGoesSouth() = assertTrue(shiftSouth(43.9493, 7.8, 150.0) < 43.9493)

    /** Le résultat ne dépend que des entrées : appliquer N fois la même recherche ne dérive pas. */
    @Test fun deterministic() = assertEquals(shiftSouth(43.9493, 7.8, 150.0), shiftSouth(43.9493, 7.8, 150.0), 0.0)

    /**
     * Ordre de grandeur : à zoom 7.8 le monde fait 512·2^7.8 ≈ 114 000 dp, donc 150 dp ≈ 0,47° de
     * longitude, soit ≈ 0,47 × cos(44°) ≈ 0,34° de latitude à Avignon.
     */
    @Test fun magnitude() = assertEquals(0.34, 43.9493 - shiftSouth(43.9493, 7.8, 150.0), 0.02)

    /** Le rayon doublé fait perdre exactement un niveau de zoom. */
    @Test fun zoomHalvesWithDoubleRadius() =
        assertEquals(1.0, zoomToFit(25.0, 44.0, 150.0) - zoomToFit(50.0, 44.0, 150.0), 1e-9)

    /** 50 km sur 150 dp vers 44° N : ≈ 9,7 (monde de 512·2^z dp). */
    @Test fun zoomValue() = assertEquals(
        Math.log(150 * 40_075.017 * Math.cos(Math.toRadians(44.0)) / (512 * 50)) / Math.log(2.0),
        zoomToFit(50.0, 44.0, 150.0), 1e-9,
    )
}

class FrameTest {
    @Test fun franceFitsAndIsCentered() {
        val f = frame(FRANCE_BOUNDS, 380.0, 500.0)!!
        assertEquals(2.2, f.longitude, 0.01)
        assertTrue(f.latitude in 46.0..47.0)
        assertTrue(f.zoom in 4.0..6.0)
    }

    @Test fun singlePointUsesMaxZoom() = assertEquals(10.0, frame(listOf(43.9 to 4.8), 380.0, 500.0)!!.zoom, 0.0)

    @Test fun widerAreaZoomsOut() =
        assertTrue(frame(listOf(48.8 to 2.3, 43.6 to 1.4), 380.0, 500.0)!!.zoom < frame(listOf(48.8 to 2.3, 48.1 to -1.7), 380.0, 500.0)!!.zoom)

    @Test fun empty() = assertEquals(null, frame(emptyList(), 380.0, 500.0))

    @Test fun mercatorRoundTrip() = assertEquals(43.95, latitudeOf(mercatorY(43.95)), 1e-9)
}
