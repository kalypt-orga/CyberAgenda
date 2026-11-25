package fr.cyberagenda.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class DeduplicatorTest {
    private val paris = ZoneId.of("Europe/Paris")
    private fun at(day: Int, hour: Int = 9) = ZonedDateTime.of(2027, 6, day, hour, 0, 0, 0, paris).toInstant().toEpochMilli()
    private fun ev(
        id: String, source: String, title: String, day: Int = 2, lat: Double? = null,
        hour: Int = 9, format: EventFormat = EventFormat.ONSITE, url: String = "https://x.org",
    ) = Event(
        id = "$source:$id", sourceId = source, title = title, description = "", startsAtEpochMs = at(day, hour),
        endsAtEpochMs = at(day, 18), format = format, kind = EventKind.CONFERENCE,
        url = url, latitude = lat, longitude = lat,
    )
    private val priority = mapOf("selection" to 0, "frcyber" to 1, "confstech" to 2, "agendadulibre" to 3, "ctftime" to 4)
    private fun merge(vararg e: Event) = Deduplicator.merge(e.toList(), { priority[it] ?: 9 }, paris)

    @Test fun mergesSameTitleWithYear() {
        val out = merge(ev("a", "selection", "SSTIC 2027"), ev("b", "frcyber", "SSTIC", lat = 48.1))
        assertEquals(1, out.size)
        assertEquals("selection:a", out[0].id)
        assertEquals(listOf("frcyber:b"), out[0].duplicateIds)
        assertEquals(48.1, out[0].latitude!!, 0.0) // coordonnées récupérées du doublon
    }

    @Test fun mergesAbbreviationVariant() =
        assertTrue(Deduplicator.sameEvent(ev("a", "s", "European Cyber Week (ECW)"), ev("b", "t", "European Cyber Week 2026")))

    @Test fun differentDaysNotMerged() = assertEquals(2, merge(ev("a", "selection", "SSTIC"), ev("b", "frcyber", "SSTIC", day = 3)).size)

    @Test fun differentEventsSameDayNotMerged() =
        assertFalse(Deduplicator.sameEvent(ev("a", "s", "Botconf 2027"), ev("b", "t", "THCon 2027 Toulouse Hacking Convention")))

    @Test fun sameSourceNeverMerged() = assertEquals(2, merge(ev("a", "selection", "Café vie privée"), ev("b", "selection", "Café vie privée")).size)

    // --- Garde-fous de l'audit (DATA-01) ---

    @Test fun openSourceCannotReplaceAnother() {
        // Fausse fiche CTFtime au titre identique, le même jour : l'atelier reste affiché avec SON lien.
        val legit = ev("1", "agendadulibre", "Café vie privée Rennes", url = "https://agendadulibre.org/events/1")
        val fake = ev("x", "ctftime", "Café vie privée Rennes", hour = 0, url = "https://evil.example/")
        val out = merge(fake, legit)
        assertEquals(1, out.size)
        assertEquals("https://agendadulibre.org/events/1", out[0].url)
    }

    @Test fun catchAllTitleDoesNotSwallowEvents() {
        val bait = ev("x", "ctftime", "Café vie privée Rennes Cryptoparty Lyon BSides Paris Atelier chiffrement Nantes", hour = 0)
        val out = merge(bait, ev("1", "agendadulibre", "Café vie privée Rennes"), ev("2", "agendadulibre", "Cryptoparty Lyon"),
            ev("3", "confstech", "BSides Paris"))
        assertEquals(4, out.size)
    }

    @Test fun noTransitiveMerge() {
        // B ressemble à A et à C, mais A et C ne se ressemblent pas : pas de groupe A+B+C.
        val out = merge(ev("a", "selection", "Cyber Night Lille"), ev("b", "frcyber", "Cyber Night"),
            ev("c", "confstech", "Cyber Night Lyon"))
        assertEquals(2, out.size)
    }

    @Test fun onlineNeverMergedWithOnsite() =
        assertFalse(Deduplicator.sameEvent(ev("a", "s", "BreizhCTF", format = EventFormat.ONLINE), ev("b", "t", "BreizhCTF")))

    @Test fun untrustedDuplicateGivesNoCoordinates() {
        val out = merge(ev("a", "agendadulibre", "Cryptoparty Lyon"), ev("b", "ctftime", "Cryptoparty Lyon", lat = 10.0))
        assertEquals("agendadulibre:a", out[0].id)
        assertEquals(null, out[0].latitude)
    }
}
