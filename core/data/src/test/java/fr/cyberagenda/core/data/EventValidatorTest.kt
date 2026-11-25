package fr.cyberagenda.core.data

import fr.cyberagenda.core.model.Event
import fr.cyberagenda.core.model.EventFormat
import fr.cyberagenda.core.model.EventKind
import fr.cyberagenda.core.model.EventValidator
import fr.cyberagenda.core.network.SafeUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class EventValidatorTest {
    private val base = Event(
        id = "test:1", sourceId = "test", title = "CTF", description = "desc",
        startsAtEpochMs = 1_000, endsAtEpochMs = 2_000, format = EventFormat.ONLINE,
        kind = EventKind.CTF, url = "https://example.org/",
    )

    /** « Maintenant » fixé à l'époque 0 : les dates de [base] sont dans la fenêtre acceptée. */
    private fun check(e: Event) = EventValidator.sanitize(e, SafeUrl::isHttps, nowMs = 0)

    @Test fun validEventPasses() { assertNotNull(check(base)) }
    @Test fun httpUrlRejected() { assertNull(check(base.copy(url = "http://example.org/"))) }
    @Test fun javascriptUrlRejected() { assertNull(check(base.copy(url = "javascript:alert(1)"))) }
    @Test fun endBeforeStartRejected() { assertNull(check(base.copy(endsAtEpochMs = 0))) }
    @Test fun foreignIdPrefixRejected() { assertNull(check(base.copy(id = "other:1"))) }
    @Test fun blankTitleRejected() { assertNull(check(base.copy(title = " ​ "))) }

    @Test fun bidiCharactersStripped() {
        assertEquals("CTF gratuit", check(base.copy(title = "CTF‮ gratuit"))!!.title)
    }

    @Test fun invalidCoordinatesDropped() {
        val e = check(base.copy(latitude = 999.0, longitude = 2.0))!!
        assertNull(e.latitude); assertNull(e.longitude)
    }

    @Test fun longTitleTruncated() {
        assertEquals(EventValidator.MAX_TITLE, check(base.copy(title = "a".repeat(1_000)))!!.title.length)
    }

    // --- Garde-fous de l'audit (DATA-03, DATA-05) ---
    private val day = 24L * 60 * 60 * 1000

    @Test fun durationOverflowRejected() =
        assertNull(check(base.copy(startsAtEpochMs = -9_151_578_247_219_200_000, endsAtEpochMs = 9_151_453_912_780_800_000)))
    @Test fun farFutureRejected() = assertNull(check(base.copy(startsAtEpochMs = 6 * 366 * day, endsAtEpochMs = 6 * 366 * day + 1)))
    @Test fun oldPastRejected() = assertNull(check(base.copy(startsAtEpochMs = -500 * day, endsAtEpochMs = -499 * day)))
    @Test fun invisibleTitleRejected() = assertNull(check(base.copy(title = "\u3164\u3164\u115F")))
    @Test fun moreInvisiblesStripped() {
        // Marque arabe (bidi), tags Unicode, contrôle C1, trait d'union conditionnel, séparateur de ligne.
        val t = check(base.copy(title = "C\u061CT\uDB40\uDC41F\u0085x\u00AD\u2028y"))!!.title
        assertEquals("CTF\nx\ny", t)
    }
    @Test fun truncationKeepsWholeEmoji() {
        val title = "a".repeat(EventValidator.MAX_TITLE - 2) + "\uD83D\uDE00" + "bbbb"
        val out = check(base.copy(title = title))!!.title
        assertFalse(Character.isHighSurrogate(out[out.length - 2]))
    }

    // --- Fin manquante (iCal sans DTEND) ---
    private val paris = java.time.ZoneId.of("Europe/Paris")
    private fun at(h: Int, m: Int = 0) = java.time.ZonedDateTime.of(2027, 3, 10, h, m, 0, 0, paris).toInstant().toEpochMilli()
    private fun checkAt(e: Event) = EventValidator.sanitize(e, SafeUrl::isHttps, nowMs = at(0), zone = paris)

    @Test fun midnightWithoutEndBecomesAllDay() {
        val out = checkAt(base.copy(startsAtEpochMs = at(0), endsAtEpochMs = at(0)))!!
        assertEquals(at(23, 59) + 59_999, out.endsAtEpochMs)
    }
    @Test fun timedWithoutEndKeepsStart() =
        assertEquals(at(18), checkAt(base.copy(startsAtEpochMs = at(18), endsAtEpochMs = at(18)))!!.endsAtEpochMs)
    @Test fun realEndUntouched() =
        assertEquals(at(17, 30), checkAt(base.copy(startsAtEpochMs = at(15), endsAtEpochMs = at(17, 30)))!!.endsAtEpochMs)
}
