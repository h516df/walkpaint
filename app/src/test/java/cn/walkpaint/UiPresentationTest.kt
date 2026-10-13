package cn.walkpaint

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class UiPresentationTest {
    private fun point(time: Long, segment: Int = 0) = TrackPoint(48.0, 2.0 + time / 1_000_000_000.0, time, 5f, segment)
    @Test fun durationDoesNotCountGapsOrSeparateTrips() {
        val a = Track("a", 0, false, listOf(point(1000), point(11000), point(31000, 1), point(41000, 1), point(200000)))
        val b = Track("b", 500000, false, listOf(point(500000), point(505000)))
        val summary = TrailSummary.of(listOf(a, b, a.copy(demo = true)))
        assertEquals(25L, summary.seconds)
        assertEquals(7, summary.points)
        assertEquals("00:25", summary.duration)
    }
    @Test fun emptySummaryAndLongDurationRemainReadable() {
        assertEquals(TrailSummary(0.0, 0, 0), TrailSummary.of(emptyList()))
        assertEquals("1:01:01", TrailSummary(0.0, 3661, 0).duration)
    }
    @Test fun weekStartsMondayAndMonthNeverIncludesFutureDays() {
        val today = LocalDate.of(2026, 10, 12)
        assertTrue(FootprintRange.WEEK.includes("2026-10-12", today))
        assertFalse(FootprintRange.WEEK.includes("2026-10-11", today))
        assertFalse(FootprintRange.WEEK.includes("2026-10-13", today))
        assertTrue(FootprintRange.MONTH.includes("2026-10-01", today))
        assertFalse(FootprintRange.MONTH.includes("2026-09-30", today))
        assertFalse(FootprintRange.MONTH.includes("2026-10-20", today))
        assertFalse(FootprintRange.ALL.includes("not-a-date", today))
    }
}
