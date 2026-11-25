package fr.cyberagenda.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecentSearchesTest {
    private fun push(list: List<String>, q: String) = RecentSearches.push(list, q, RecentSearches::key)

    @Test fun newestFirst() = assertEquals(listOf("b", "a"), push(listOf("a"), "b"))
    @Test fun duplicateMovesToTopIgnoringCaseAndAccents() =
        assertEquals(listOf("Sécurité", "ctf"), push(listOf("ctf", "securite"), "Sécurité"))
    @Test fun keepsTen() = assertEquals(10, (1..15).fold(emptyList<String>()) { l, i -> push(l, "q$i") }.size)
    @Test fun oldestDropped() = assertEquals("q15", (1..15).fold(emptyList<String>()) { l, i -> push(l, "q$i") }.first())
    @Test fun tooShortIgnored() = assertNull(RecentSearches.cleanQuery(" a "))
    @Test fun controlCharactersRemoved() = assertEquals("CTF Rennes", RecentSearches.cleanQuery("CTF‮ Rennes"))

    @Test fun showsThreeWhenEmpty() = assertEquals(listOf("a1", "a2", "a3"),
        RecentSearches.suggestions(listOf("a1", "a2", "a3", "a4"), "", { it }))
    @Test fun filtersByPrefix() = assertEquals(listOf("Rennes", "rencontre"),
        RecentSearches.suggestions(listOf("Rennes", "Paris", "rencontre"), "ren", { it }))
    @Test fun exactMatchNotSuggested() = assertEquals(emptyList<String>(),
        RecentSearches.suggestions(listOf("Paris"), "paris", { it }))

    @Test fun placeKeyIgnoresRadius() = assertEquals(
        RecentPlace("Rennes", "35", 48.1, -1.6, 10).key, RecentPlace("rennes", "35", 48.1, -1.6, 50).key)
}
