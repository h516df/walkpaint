package cn.walkpaint

import org.junit.Assert.*
import org.junit.Test

class CredibleTrailTest {
    private fun p(m: Double, seconds: Long) = TrackPoint(31.0 + m / 111195.0, 121.0, seconds * 1000, 8f)
    @Test fun stationaryMinutesCannotBecomeNewSavedStarts() {
        for (seconds in listOf(46L, 120L, 600L))
            assertEquals("STATIONARY_OR_SMALL_MOVE", Geo.rejectionReason(p(0.0, 1), p(0.0, seconds)))
    }
    @Test fun loneFixAndZeroLengthPathAreNotTravelledCorridors() {
        assertFalse(RecordedTrail.visible(listOf(p(0.0, 1))))
        assertFalse(RecordedTrail.visible(listOf(p(0.0, 1), p(0.0, 2))))
        assertTrue(RecordedTrail.visible(listOf(p(0.0, 1), p(3.5, 3))))
    }
    @Test fun acceptedFirstFixRevealsFootprintWithoutJoiningGapsOrDemoData() {
        val a=p(0.0,1);val b=p(50.0,100)
        val actual=RecordedTrail.exploredPaths(listOf(Track("real",0,false,listOf(a,b)),Track("demo",0,true,listOf(p(100.0,1)))))
        assertEquals(listOf(listOf(a),listOf(b)),actual)
        assertTrue(RecordedTrail.visiblePaths(listOf(Track("real",0,false,listOf(a)))).isEmpty())
    }
}
