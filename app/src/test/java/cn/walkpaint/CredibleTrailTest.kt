package cn.walkpaint

import org.junit.Assert.*
import org.junit.Test

class CredibleTrailTest {
    private fun p(m: Double, seconds: Long) = TrackPoint(31.0 + m / 111195.0, 121.0, seconds * 1000, 8f)
    @Test fun stationaryMinutesCannotBecomeNewSavedStarts() {
        for (seconds in listOf(46L, 120L, 600L))
            assertEquals("STATIONARY_OR_SMALL_MOVE", Geo.rejectionReason(p(0.0, 1), p(0.0, seconds)))
    }
    @Test fun loneFixAndZeroLengthPathDoNotRevealGround() {
        assertFalse(RecordedTrail.visible(listOf(p(0.0, 1))))
        assertFalse(RecordedTrail.visible(listOf(p(0.0, 1), p(0.0, 2))))
        assertTrue(RecordedTrail.visible(listOf(p(0.0, 1), p(3.5, 3))))
    }
}
