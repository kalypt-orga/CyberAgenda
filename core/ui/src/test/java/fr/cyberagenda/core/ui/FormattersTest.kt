package fr.cyberagenda.core.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.TimeZone

/** Libellés de dates des miniatures et des fiches (bêta-test : « 00:00 – 00:00 », fins à minuit). */
class FormattersTest {
    private val paris = ZoneId.of("Europe/Paris")
    private fun at(day: Int, h: Int, m: Int = 0) = ZonedDateTime.of(2026, 10, day, h, m, 0, 0, paris).toInstant().toEpochMilli()

    @Before fun zone() = TimeZone.setDefault(TimeZone.getTimeZone(paris))

    @Test fun timedRange() = assertEquals("15:00 – 17:30", Formatters.shortSpan(at(3, 15), at(3, 17, 30)))
    @Test fun unknownHoursAllDay() = assertEquals(Formatters.UNKNOWN_TIME, Formatters.shortSpan(at(3, 0), at(3, 23, 59) + 59_999))
    @Test fun zeroDurationAtMidnightIsUnknown() = assertEquals(Formatters.UNKNOWN_TIME, Formatters.shortSpan(at(3, 0), at(3, 0)))
    @Test fun zeroDurationKeepsStartTime() = assertEquals("à 18:00", Formatters.shortSpan(at(3, 18), at(3, 18)))

    @Test fun endingAtMidnightIsSameDay() {
        // CubeCTF : 16:00 → 00:00 le lendemain = une soirée, pas deux jours.
        assertFalse(Formatters.isMultiDay(at(3, 16), at(4, 0)))
        assertEquals("16:00 – 00:00", Formatters.shortSpan(at(3, 16), at(4, 0)))
    }
    @Test fun twoFullDaysEndingAtMidnight() {
        assertTrue(Formatters.isMultiDay(at(21, 0), at(23, 0)))
        assertEquals("du 21 au 22 oct.", Formatters.daySpan(at(21, 0), at(23, 0)))
    }
    @Test fun realMultiDay() = assertTrue(Formatters.isMultiDay(at(3, 9), at(4, 18)))
    @Test fun fullDayMidnightToMidnight() = assertEquals("00:00 – 24:00", Formatters.shortSpan(at(21, 0), at(22, 0)))
}
