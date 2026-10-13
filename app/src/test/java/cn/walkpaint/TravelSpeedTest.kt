package cn.walkpaint

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The fast-travel gate must keep walking, cycling and driving while refusing a GPS jump.
 * These run as plain JVM tests, so the rule can be re-checked without an emulator.
 */
class TravelSpeedTest {

    private companion object {
        /** Geo.meters uses a spherical earth; a pure north offset converts exactly like this. */
        const val METERS_PER_DEGREE = 6371000.0 * Math.PI / 180.0
        const val TWO_SECONDS = 2.0
    }

    /** A fix [northMeters] north of 31N/121E, [timeMillis] after the start. */
    private fun at(northMeters: Double, timeMillis: Long, accuracy: Float = 5f) =
        TrackPoint(31.0 + northMeters / METERS_PER_DEGREE, 121.0, timeMillis, accuracy)

    private fun accepted(
        meters: Double,
        seconds: Double = TWO_SECONDS,
        accuracy: Float = 5f,
        reportedSpeed: Double? = null,
    ): Boolean {
        val previous = at(0.0, 0L, accuracy)
        val next = at(meters, (seconds * 1000).toLong(), accuracy)
        return Geo.rejectionReason(previous, next, confidentlyMoving = false, reportedSpeed = reportedSpeed) == null
    }

    @Test fun walkingIsAccepted() {
        assertTrue("a 4 m/s walk must be recorded", accepted(meters = 8.0))
    }

    @Test fun cyclingIsAccepted() {
        assertTrue("a 7 m/s ride must be recorded", accepted(meters = 14.0))
    }

    @Test fun fasterCyclingIsAcceptedWhenTheReceiverAgrees() {
        // 10 m/s needs a precise fix and a matching Doppler speed.
        assertTrue(accepted(meters = 20.0, reportedSpeed = 10.0))
        assertFalse("displacement 10 m/s while the receiver measures 0 m/s is a jump",
            accepted(meters = 20.0, reportedSpeed = 0.0))
    }

    @Test fun drivingIsAccepted() {
        // 20 m/s is 72 km/h.
        assertTrue(accepted(meters = 40.0, accuracy = 8f, reportedSpeed = 20.0))
    }

    @Test fun highwayDrivingIsAcceptedEvenWithoutAReportedSpeed() {
        // 33 m/s is 120 km/h. No receiver speed available must not silently drop a real drive.
        assertTrue(accepted(meters = 66.0, reportedSpeed = null))
    }

    @Test fun jumpIsRejectedByTheSharedCeiling() {
        // 100 m in 2 s is 180 km/h. The former 60 m/s ceiling let this through.
        assertFalse(accepted(meters = 100.0))
        assertFalse(accepted(meters = 300.0))
    }

    @Test fun jumpIsRejectedWhenTheReceiverReportsStandingStill() {
        // 25 m/s displacement while the chip measures 0 m/s.
        assertFalse(accepted(meters = 50.0, reportedSpeed = 0.0))
    }

    @Test fun implausibleReportedSpeedIsRejected() {
        assertFalse(accepted(meters = 40.0, accuracy = 8f, reportedSpeed = 100.0))
        assertFalse(accepted(meters = 40.0, accuracy = 8f, reportedSpeed = Double.NaN))
    }

    @Test fun aJumpIsNeitherCountedNorDrawn() {
        val jumped = at(0.0, 0L) to at(100.0, 2000L)
        assertFalse("a jump must not join two drawn points", RecordedTrail.connected(jumped.first, jumped.second))
        assertEquals(0.0, Geo.distance(listOf(jumped.first, jumped.second)), 1e-6)

        val driven = at(0.0, 0L, 8f) to at(40.0, 2000L, 8f)
        assertTrue("real driving must still draw", RecordedTrail.connected(driven.first, driven.second))
        assertTrue(Geo.distance(listOf(driven.first, driven.second)) in 39.0..41.0)
    }

    @Test fun storageAndDrawingShareOneCeiling() {
        // Just under the shared ceiling, and just over it.
        val under = RecordedTrail.MAX_SPEED_MPS * TWO_SECONDS - 10.0
        val over = RecordedTrail.MAX_SPEED_MPS * TWO_SECONDS + 10.0
        assertTrue(RecordedTrail.connected(at(0.0, 0L), at(under, 2000L)))
        assertTrue(accepted(meters = under))
        assertFalse(RecordedTrail.connected(at(0.0, 0L), at(over, 2000L)))
        assertFalse(accepted(meters = over))
    }
}
