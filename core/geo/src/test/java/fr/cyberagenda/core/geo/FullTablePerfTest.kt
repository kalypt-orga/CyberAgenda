package fr.cyberagenda.core.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Table complète (≈ 35 000 communes) : contenu attendu et temps de chargement / recherche raisonnables. */
class FullTablePerfTest {
    private val table = File("src/main/assets/communes.tsv").readText()
    private val geo = FrenchGeocoder { table }

    @Test fun loadsAndSearchesQuickly() {
        val t0 = System.nanoTime(); geo.warmUp(); val load = (System.nanoTime() - t0) / 1_000_000
        val t1 = System.nanoTime(); repeat(100) { geo.search("sa") }; val search = (System.nanoTime() - t1) / 100_000
        println("chargement : $load ms ; recherche moyenne : ${search / 10.0} ms")
        assertTrue("chargement trop lent : $load ms", load < 2_000)
        assertTrue("recherche trop lente", search / 10.0 < 20)
    }

    @Test fun smallTownsFound() = assertEquals("31", geo.search("Aussonne").first().department)
    @Test fun biggestFirst() = assertEquals("Saint-Denis", geo.search("saint-d").first().name)
    @Test fun labelForSmallTown() = assertEquals("(31) Aussonne", geo.shortLabel("MEETT, Parc des Expositions de Toulouse, Aussonne (31)"))
    @Test fun labelStreetAddress() = assertEquals("(76) Darnétal", geo.shortLabel("Ressourcerie RESISTES, 10 rue Richard Waddington, Darnétal"))
    @Test fun labelParisVenue() = assertEquals("(92) Puteaux", geo.shortLabel("Campus Cyber, Puteaux (92)"))
    @Test fun countryIgnored() = assertEquals("(75) Paris", geo.shortLabel("Paris, France"))

    /** La colonne précalculée doit être exactement la normalisation de l'app (sinon la recherche raterait des villes). */
    @Test fun precomputedKeysMatchAppNormalisation() {
        val wrong = table.lineSequence().filter { it.isNotBlank() && !it.startsWith("#") }
            .map { it.split('\t') }.filter { FrenchGeocoder.normalize(it[0]) != it[5] }.map { it[0] }.toList()
        assertEquals(emptyList<String>(), wrong.take(10))
    }
}
