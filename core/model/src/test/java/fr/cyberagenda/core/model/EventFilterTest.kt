package fr.cyberagenda.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EventFilterTest {
    private fun ev(format: EventFormat = EventFormat.ONSITE, kind: EventKind = EventKind.CTF, beginner: Boolean = false) = Event(
        id = "s:1", sourceId = "s", title = "T", description = "", startsAtEpochMs = 0, endsAtEpochMs = 1,
        format = format, kind = kind, url = "https://a.org", beginnerFriendly = beginner,
    )

    @Test fun defaultShowsEverything() {
        assertTrue(EventFilter().matches(ev(), favorite = false))
        assertEquals(0, EventFilter().activeCount)
    }

    @Test fun hideBeginners() {
        val f = EventFilter(beginner = BeginnerFilter.HIDE)
        assertFalse(f.matches(ev(beginner = true), false))
        assertTrue(f.matches(ev(beginner = false), false))
    }

    @Test fun onlyBeginners() = assertFalse(EventFilter(beginner = BeginnerFilter.ONLY).matches(ev(beginner = false), false))

    @Test fun hybridMatchesBothFormats() {
        assertTrue(EventFilter(formats = setOf(EventFormat.ONLINE)).matches(ev(EventFormat.HYBRID), false))
        assertTrue(EventFilter(formats = setOf(EventFormat.ONSITE)).matches(ev(EventFormat.HYBRID), false))
        assertFalse(EventFilter(formats = setOf(EventFormat.ONLINE)).matches(ev(EventFormat.ONSITE), false))
    }

    @Test fun kinds() {
        val f = EventFilter(kinds = setOf(EventKind.CONFERENCE, EventKind.MEETUP))
        assertFalse(f.matches(ev(kind = EventKind.CTF), false))
        assertTrue(f.matches(ev(kind = EventKind.MEETUP), false))
    }

    @Test fun favoritesOnly() {
        val f = EventFilter(favoritesOnly = true)
        assertFalse(f.matches(ev(), favorite = false)); assertTrue(f.matches(ev(), favorite = true))
        assertEquals(1, f.activeCount)
    }
}
