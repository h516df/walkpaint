package cn.walkpaint

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrailShapeTest {

    private fun point(lat: Double, lon: Double, time: Long = 0L) = TrackPoint(lat, lon, time, 5f)

    /** Roughly [metres] of latitude at this scale. */
    private fun north(base: Double, metres: Double) = base + metres / 111194.9
    private fun east(base: Double, metres: Double) = base + metres / 95000.0

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

class TrailDedupeTest {

    private fun point(lat: Double, lon: Double, time: Long = 0L) = TrackPoint(lat, lon, time, 5f)
    private fun north(base: Double, metres: Double) = base + metres / 111194.9
    private fun east(base: Double, metres: Double) = base + metres / 95000.0

    @Test fun aStreetWalkedTwiceKeepsOneLine() {
        // Each pass carries its own offset, so drawn faithfully the pair braids. The second pass
        // has to land on ground already drawn and add nothing at all.
        val out = listOf(point(north(31.0, 0.0), 121.0), point(north(31.0, 40.0), 121.0),
            point(north(31.0, 80.0), 121.0))
        val back = listOf(point(north(31.0, 80.0), east(121.0, 6.0)),
            point(north(31.0, 40.0), east(121.0, 6.0)),
            point(north(31.0, 0.0), east(121.0, 6.0)))
        val runs = TrailShape.dedupe(listOf(out, back))
        assertEquals(1, runs.size)
        assertEquals(out, runs[0])
    }

    @Test fun walkingBackDownTheStreetAddsNothingEither() {
        val there = listOf(point(north(31.0, 0.0), 121.0, 0L), point(north(31.0, 60.0), 121.0, 30_000L))
        val back = listOf(point(north(31.0, 60.0), 121.0, 40_000L), point(north(31.0, 0.0), 121.0, 70_000L))
        val runs = TrailShape.dedupe(listOf(there, back))
        assertEquals(1, runs.size)
        assertEquals(2, runs[0].size)
    }

    @Test fun aShortWalkKeepsItsWholeShape() {
        // A walk shorter than the de-duplication radius is all new ground and must not be trimmed.
        val short = listOf(point(31.0, 121.0), point(north(31.0, 9.0), 121.0),
            point(north(31.0, 18.0), 121.0))
        val runs = TrailShape.dedupe(listOf(short))
        assertEquals(1, runs.size)
        assertEquals(3, runs[0].size)
    }

    @Test fun forwardWalkingIsNotMistakenForAGap() {
        // Points a few metres apart are the walk itself. The line may thin out to one vertex every
        // few metres, but it must stay one run: closing the run on every skip is how a radius rule
        // turns a walk into a dotted line, and the ends have to survive either way.
        val line = (0..12).map { point(north(31.0, it * 5.61), 121.0, it * 2_000L) }
        val runs = TrailShape.dedupe(listOf(line))
        assertEquals(1, runs.size)
        assertEquals(line.first(), runs[0].first())
        assertEquals(line.last(), runs[0].last())
        assertTrue("kept only ${runs[0].size} of ${line.size}", runs[0].size >= line.size / 2)
    }

    @Test fun aStreetTwoHundredMetresAwayIsStillNewGround() {
        val here = listOf(point(north(31.0, 0.0), 121.0), point(north(31.0, 80.0), 121.0))
        val away = listOf(point(north(31.0, 0.0), east(121.0, 200.0)),
            point(north(31.0, 80.0), east(121.0, 200.0)))
        val runs = TrailShape.dedupe(listOf(here, away))
        assertEquals(2, runs.size)
        assertEquals(2, runs[1].size)
    }

    @Test fun aStretchFarAwayStartsANewRun() {
        val first = listOf(point(north(31.0, 0.0), 121.0), point(north(31.0, 80.0), 121.0))
        val far = listOf(point(north(31.0, 400.0), 121.0), point(north(31.0, 480.0), 121.0))
        val runs = TrailShape.dedupe(listOf(first, far))
        assertEquals(2, runs.size)
    }

    @Test fun aLoneFixIsGroundButNotALine() {
        assertEquals(0, TrailShape.dedupe(listOf(listOf(point(31.0, 121.0)))).size)
    }
}
