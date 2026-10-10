package cn.walkpaint

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The motion cue decides whether the store may use its relaxed displacement gate. If it fails to
 * fire while walking, every walk is sampled at roughly the accuracy-scaled gate (about 11 m at
 * 9 m accuracy) instead of about 5 m, which is what made recorded trails look sparse and cut
 * corners. These run as plain JVM tests.
 */
class MovementCueTest {

    private companion object {
        const val METERS_PER_DEGREE = 6371000.0 * Math.PI / 180.0
    }

    /** A fix [north] metres north and [east] metres east of an origin, [atMs] after the start. */
    private fun fix(north: Double, east: Double, atMs: Long, accuracy: Float = 9f) = MotionFix(
        lat = 31.0 + north / METERS_PER_DEGREE,
        lon = 121.0 + east / (METERS_PER_DEGREE * Math.cos(Math.toRadians(31.0))),
        time = atMs,
        accuracy = accuracy,
    )

    /** Three consecutive fixes walking [speed] m/s due north at a two-second cadence. */
    private fun walking(speed: Double, accuracy: Float = 9f): Boolean {
        val step = speed * 2.0
        return MovementCue.fromGeometry(
            fix(0.0, 0.0, 1_000L, accuracy),
            fix(step, 0.0, 3_000L, accuracy),
            fix(step * 2, 0.0, 5_000L, accuracy),
        )
    }

    @Test fun ordinaryWalkingIsRecognised() {
        assertTrue("a 1.4 m/s walk must count as movement", walking(1.4))
        assertTrue("a brisk 2.0 m/s walk must count as movement", walking(2.0))
    }

    @Test fun theWalkWindowIsShorterThanTheOldSevenMetreRule() {
        // Two-second cadence at 1.4 m/s spans 5.6 m across three fixes. The previous rule
        // required more than 7 m and therefore never fired for a pedestrian.
        val net = Geo.meters(
            TrackPoint(31.0, 121.0, 1_000L, 9f),
            TrackPoint(31.0 + 5.6 / METERS_PER_DEGREE, 121.0, 5_000L, 9f),
        )
        assertTrue("window must stay under the old threshold, was $net", net in 5.0..6.5)
        assertTrue(walking(1.4))
    }

    @Test fun stationaryWanderIsNotMovement() {
        // A receiver at rest wanders: the path travelled is long, the net displacement is short.
        val wandered = MovementCue.fromGeometry(
            fix(0.0, 0.0, 1_000L),
            fix(6.0, 4.0, 3_000L),
            fix(1.0, 0.5, 5_000L),
        )
        assertFalse("wandering at rest must not look like travel", wandered)
    }

    @Test fun noiseBelowTheStepFloorIsNotMovement() {
        assertFalse(MovementCue.fromGeometry(
            fix(0.0, 0.0, 1_000L),
            fix(1.0, 0.0, 3_000L),
            fix(2.0, 0.0, 5_000L),
        ))
    }

    @Test fun aSharpTurnIsNotYetConfident() {
        // 90 degrees between consecutive fixes: the direction of travel is not established.
        assertFalse(MovementCue.fromGeometry(
            fix(0.0, 0.0, 1_000L),
            fix(6.0, 0.0, 3_000L),
            fix(6.0, 6.0, 5_000L),
        ))
    }

    @Test fun poorAccuracyDisablesTheGeometricCue() {
        assertFalse(walking(1.4, accuracy = 25f))
    }

    @Test fun providerSpeedCoversSlowWalking() {
        assertTrue(MovementCue.fromProviderSpeed(true, 0.5f, false, 0f))
        assertTrue(MovementCue.fromProviderSpeed(true, 1.4f, true, 0.4f))
        assertFalse("below the travel floor the receiver is at rest", MovementCue.fromProviderSpeed(true, 0.1f, false, 0f))
    }

    @Test fun unreliableProviderSpeedIsIgnored() {
        assertFalse(MovementCue.fromProviderSpeed(true, 1.4f, true, 2.0f))
    }

    @Test fun cyclingFallsInsideTheCueBand() {
        // 5 m/s is 18 km/h: the strict gate would sample this at about 11 m, the cue at about 5 m.
        assertTrue(walking(5.0))
    }
}
