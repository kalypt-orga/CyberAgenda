package fr.cyberagenda.feature.map

import fr.cyberagenda.core.model.Event
import fr.cyberagenda.core.model.EventFormat
import fr.cyberagenda.core.model.EventKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CollapseSeriesTest {
    private fun item(id: String, start: Long, title: String = "Portes ouvertes", fav: Boolean = false) = PlaceEvent(
        Event(id = "adl:$id", sourceId = "adl", title = title, description = "", startsAtEpochMs = start, endsAtEpochMs = start + 1,
            format = EventFormat.ONSITE, kind = EventKind.WORKSHOP, url = "https://a.org", location = "Vanves"),
        sourceName = "ADL", favorite = fav,
    )

    @Test fun eightOccurrencesBecomeOne() {
        val out = collapseSeries((1..8).map { item("$it", it * 100L) } + item("x", 50, title = "Autre"))
        assertEquals(2, out.size)
        val series = out.single { it.event.title == "Portes ouvertes" }
        assertEquals(100L, series.event.startsAtEpochMs) // prochaine date
        assertEquals(7, series.otherDates)
    }

    @Test fun pastKeepsMostRecent() =
        assertEquals(800L, collapseSeries((1..8).map { item("$it", it * 100L) }, newestFirst = true).single().event.startsAtEpochMs)

    @Test fun favoriteOnAnyOccurrenceKept() =
        assertTrue(collapseSeries(listOf(item("1", 100), item("2", 200, fav = true))).single().favorite)
}
