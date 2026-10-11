package cn.walkpaint

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrailShapeTest {

    private fun point(lat: Double, lon: Double) = TrackPoint(lat, lon, 0L, 5f)

    /** Roughly [metres] of latitude at this scale. */
    private fun north(base: Double, metres: Double) = base + metres / 111194.9

    @Test fun aStraightLineIsLeftAloneApartFromItsEnds() {
        val line = (0..20).map { point(north(31.0, it * 10.0), 121.0) }
        val simplified = TrailShape.simplify(line)
        assertEquals(2, simplified.size)
        assertEquals(line.first(), simplified.first())
        assertEquals(line.last(), simplified.last())
    }

    @Test fun jitterWithinTheToleranceIsRemoved() {
        // A straight walk where every fix wobbles two metres sideways: the wobble is drift.
        val line = (0..20).map { index ->
            point(north(31.0, index * 10.0), 121.0 + (if (index % 2 == 0) 2.0 else -2.0) / 95000.0)
        }
        val simplified = TrailShape.simplify(line)
        assertTrue("was ${simplified.size} of ${line.size}", simplified.size <= 3)
    }

    @Test fun aRealDetourIsKept() {
        // Out and back thirty metres: that is walking, not drift, and must survive.
        val line = listOf(
            point(31.0, 121.0),
            point(north(31.0, 10.0), 121.0),
            point(north(31.0, 10.0), 121.0 + 30.0 / 95000.0),
            point(north(31.0, 20.0), 121.0),
        )
        val simplified = TrailShape.simplify(line)
        assertTrue("the detour was flattened away, ${simplified.size} points", simplified.size >= 4)
    }

    @Test fun theEndsAreNeverDropped() {
        val line = listOf(point(31.0, 121.0), point(north(31.0, 5.0), 121.0), point(north(31.0, 60.0), 121.0))
        val simplified = TrailShape.simplify(line)
        assertEquals(line.first(), simplified.first())
        assertEquals(line.last(), simplified.last())
    }

    @Test fun tinyAndEmptyPathsAreReturnedUnchanged() {
        assertEquals(0, TrailShape.simplify(emptyList()).size)
        val one = listOf(point(31.0, 121.0))
        assertEquals(one, TrailShape.simplify(one))
        val two = listOf(point(31.0, 121.0), point(31.001, 121.0))
        assertEquals(two, TrailShape.simplify(two))
    }
}

class RecapTest {

    /**
     * DailyFootprint derives its metres from the tracks, and the app refuses teleporting points, so
     * the fixture walks the distance properly: five metres every two seconds, like a person.
     */
    private fun day(key: String, meters: Double): DailyFootprint {
        val step = 5.0
        val count = maxOf(2, Math.round(meters / step).toInt() + 1)
        val points = (0 until count).map { index ->
            TrackPoint(31.0 + (index * step) / 111194.9, 121.0, index * 2000L, 5f)
        }
        return DailyFootprint(key, listOf(Track("t-$key", 0L, false, points)))
    }

    @Test fun aMonthWithNothingInItHasNoRecap() {
        assertEquals(null, RecapMaker.of(listOf(day("2026-09-01", 100.0)), "2026-10"))
    }

    @Test fun theSummaryAddsUpThePeriodOnly() {
        val recap = RecapMaker.of(listOf(
            day("2026-10-01", 1000.0),
            day("2026-10-02", 3000.0),
            day("2026-09-30", 9999.0),
        ), "2026-10")!!
        assertEquals(4000.0, recap.meters, 20.0)
        assertEquals(2, recap.days)
        assertEquals("2026-10-02", recap.bestDay)
        assertEquals(3000.0, recap.bestDayMeters, 20.0)
        assertEquals("2026-10-01", recap.firstDay)
    }

    @Test fun theLongestRunIsFoundAnywhereNotJustAtTheEnd() {
        assertEquals(3, RecapMaker.longestStreak(listOf(
            "2026-10-01", "2026-10-02", "2026-10-03", "2026-10-09", "2026-10-10",
        )))
    }

    @Test fun aSingleDayIsARunOfOneAndNothingIsARunOfZero() {
        assertEquals(1, RecapMaker.longestStreak(listOf("2026-10-05")))
        assertEquals(0, RecapMaker.longestStreak(emptyList()))
    }

    @Test fun unsortedAndRepeatedDaysStillCountAsOneRun() {
        assertEquals(3, RecapMaker.longestStreak(listOf(
            "2026-10-03", "2026-10-01", "2026-10-02", "2026-10-02",
        )))
    }

    @Test fun aYearSummarySpansEveryMonth() {
        val recap = RecapMaker.of(listOf(
            day("2026-01-04", 500.0),
            day("2026-07-20", 2500.0),
        ), "2026")!!
        assertEquals(2, recap.days)
        assertEquals(3000.0, recap.meters, 20.0)
        assertEquals("2026", recap.period)
    }
}
