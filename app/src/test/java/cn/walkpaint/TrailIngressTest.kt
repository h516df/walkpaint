package cn.walkpaint
import org.junit.Assert.*
import org.junit.Test

class TrailIngressTest {
    @Test fun firstValidObservationWaitsForMotionInsteadOfClaimingDuplicateTime() {
        val ingress=TrailIngress();assertNull(ingress.offer(p(0.0,1)))
        assertEquals("STATIONARY_OR_SMALL_MOVE",ingress.rejection)
    }
    private fun p(m: Double, s: Long, east: Double = 0.0) = TrackPoint(31.0+m/111195.0,121.0+east/95312.0,s*1000,8f)
    @Test fun minutesOfStationaryFixesProduceNoBatch() {
        val ingress=TrailIngress()
        for(s in 1L..600L step 2) assertNull(ingress.offer(p(0.0,s)))
    }
    @Test fun slowWalkingRetainsActualTimeAnchorsAndNoInventedCoordinates() {
        val ingress=TrailIngress(); val input=(1L..121L step 2).map { p(it*.2,it) }
        val batches=input.mapNotNull { ingress.offer(it,true) }
        assertTrue(batches.isNotEmpty())
        assertTrue(batches.flatMap { it.points }.all { it in input })
        assertTrue(batches.flatMap { it.points.zipWithNext() }.all { (a,b) -> b.time-a.time<=32_000 })
        assertTrue(batches.first().newSegment)
        assertTrue(batches.drop(1).all { !it.newSegment })
    }
    @Test fun actualGapAndRejectedJumpCannotJoinOldTravel() {
        val ingress=TrailIngress()
        ingress.offer(p(0.0,1)); assertNotNull(ingress.offer(p(20.0,11)))
        assertNull(ingress.offer(p(10000.0,12)))
        assertNull(ingress.offer(p(1000.0,80)))
        val batch=ingress.offer(p(1020.0,90))!!
        assertTrue(batch.newSegment); assertEquals(80_000L,batch.points.first().time)
    }
    @Test fun turnIsKeptAndBufferCannotProveContinuityAcrossRestart() {
        val ingress=TrailIngress()
        val a=p(0.0,1); val b=p(5.0,6); val c=p(5.0,11,10.0)
        assertNull(ingress.offer(a)); assertNull(ingress.offer(b))
        assertTrue(ingress.offer(c)!!.points.contains(b))
        ingress.reset(); assertNull(ingress.offer(p(20.0,13)))
        assertTrue(ingress.offer(p(40.0,23))!!.newSegment)
    }
}
