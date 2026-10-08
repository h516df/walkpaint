package cn.walkpaint

import org.junit.Assert.*
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale

class FootprintInsightsTest {
    private fun instant(value: String): Long =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).parse(value)!!.time

    @Test fun repeatedPointsInSameGridOnlyCountOnce() {
        val base = instant("2026-10-07 12:00:00")
        val track = Track("walk1", base, false, listOf(
            TrackPoint(31.23040, 121.47370, base, 5f),
            TrackPoint(31.23050, 121.47380, base + 10_000, 5f)
        ))
        assertEquals(1, FootprintInsights.exploredCells(TrackState(listOf(track))))
    }

    @Test fun demoDataDoesNotAffectVisits() {
        val base = instant("2026-10-07 12:00:00")
        val demo = Track("demo", base, true, listOf(TrackPoint(31.20, 121.40, base, 5f)))
        val real = Track("real", base, false, listOf(TrackPoint(31.23, 121.47, base, 5f)))
        val state = TrackState(listOf(real, demo))
        assertEquals(1, FootprintInsights.traveledDays(state))
        assertEquals(1, FootprintInsights.exploredCells(state))
        assertEquals(1, FootprintInsights.lastVisited(state).size)
        assertEquals("real", FootprintInsights.lastVisited(state).first().id)
    }

    @Test fun daysAreCountedFromAcceptedRealFixes() {
        val a = instant("2026-10-06 12:00:00")
        val b = instant("2026-10-07 12:00:00")
        val trip1 = Track("a", a, false, listOf(TrackPoint(31.0, 121.0, a, 5f)))
        val trip2 = Track("b", b, false, listOf(TrackPoint(32.0, 122.0, b, 5f)))
        assertEquals(2, FootprintInsights.traveledDays(TrackState(listOf(trip1, trip2))))
    }

    @Test fun rejectsOutdatedOrFutureGpsTimestamps() {
        val now = 1_791_453_240_000L
        assertTrue(Geo.plausibleTimestamp(now, now))
        assertTrue(Geo.plausibleTimestamp(now - 90_000L, now))
        assertFalse(Geo.plausibleTimestamp(0L, now))
        assertFalse(Geo.plausibleTimestamp(now - 500_000L, now))
        assertFalse(Geo.plausibleTimestamp(now + 120_000L, now))
    }

    @Test fun stationaryGpsDriftIsNotPainted() {
        val a = TrackPoint(31.0, 121.0, 1000L, 10f)
        val jitter = TrackPoint(31.00004, 121.0, 5000L, 10f)
        val move = TrackPoint(31.0002, 121.0, 11_000L, 10f)
        assertFalse(Geo.accept(a, jitter))
        assertTrue(Geo.accept(a, move))
    }
}
