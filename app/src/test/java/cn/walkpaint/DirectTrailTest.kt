package cn.walkpaint

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject

class DirectTrailTest {
    private fun p(metres:Double,seconds:Long,segment:Int=0,accuracy:Float=5f)=
        TrackPoint(31.0+metres/111195.0,121.0,seconds*1000,accuracy,segment)
    @Test fun walkingCyclingAndDrivingNeedNoRoadCatalogOrManualMode() {
        for(speed in listOf(1.5,7.0,15.0,33.0)) {
            val a=p(0.0,1);val b=p(speed*10,11)
            assertNull(Geo.rejectionReason(a,b,reportedSpeed=speed))
            assertTrue(Geo.distance(listOf(a,b))>speed*9)
            assertEquals(1,RecordedTrail.paths(listOf(Track("real",a.time,false,listOf(a,b)))).size)
        }
    }
    @Test fun missingSpeedStillAllowsAccurateNormalDrivingButRejectsTeleport() {
        assertNull(Geo.rejectionReason(p(0.0,1),p(300.0,11)))
        assertEquals("JUMP_OR_TOO_FAST",Geo.rejectionReason(p(0.0,1),p(1000.0,2)))
        assertEquals("JUMP_OR_TOO_FAST",Geo.rejectionReason(p(0.0,1),p(300.0,11),reportedSpeed=0.0))
    }
    @Test fun driftPoorAccuracyAndDuplicateTimeAreStillRejected() {
        val a=p(0.0,1)
        assertNotNull(Geo.rejectionReason(a,p(1.0,5)))
        assertNotNull(Geo.rejectionReason(a,p(30.0,11,accuracy=90f)))
        assertNotNull(Geo.rejectionReason(a,p(30.0,1)))
    }
    @Test fun recoveringAfterGapStoresNewStartWithoutDrawingMissingJourney() {
        val points=listOf(p(0.0,1),p(20.0,11),p(10000.0,80),p(10020.0,90))
        assertNull(Geo.rejectionReason(points[1],points[2]))
        assertEquals(listOf(2,2),RecordedTrail.paths(listOf(Track("trip",1000,false,points))).map{it.size})
        assertEquals(40.0,Geo.distance(points),0.1)
    }
    @Test fun tripsSegmentsAndDemosNeverJoinOrLeakIntoDirectTrail() {
        val a=Track("a",1000,false,listOf(p(0.0,1),p(10.0,11),p(20.0,21,1),p(30.0,31,0)))
        val b=Track("b",40000,false,listOf(p(100.0,40)))
        val demo=Track("demo",50000,true,listOf(p(200.0,50),p(300.0,60)))
        assertEquals(listOf(2,1,1,1),RecordedTrail.paths(listOf(a,b,demo)).map{it.size})
        val f=JSONObject(TrackMapJson.encode(listOf(a,b,demo))).getJSONArray("features")
        assertEquals(1,(0 until f.length()).count{f.getJSONObject(it).getJSONObject("geometry").getString("type")=="LineString"})
    }
}
