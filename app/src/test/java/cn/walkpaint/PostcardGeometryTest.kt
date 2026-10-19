package cn.walkpaint

import org.junit.Assert.*
import org.junit.Test

class PostcardGeometryTest {
    private fun p(lat:Double,lon:Double,time:Long,segment:Int=0)=TrackPoint(lat,lon,time,5f,segment)
    @Test fun pointOnlyAndEmptyRoutesRemainFiniteAndCentered() {
        assertTrue(RouteOutline.from(emptyList()).fit(900.0,700.0).isEmpty())
        val outline=RouteOutline.from(listOf(Track("single",1,false,listOf(p(39.9,116.4,1)))))
        val point=outline.fit(900.0,700.0).single().single()
        assertEquals(CardPoint(450.0,350.0),point)
    }
    @Test fun gapsAndSeparateTripsAreNeverConnectedForThePostcard() {
        val points=listOf(p(39.9,116.4,1000),p(39.9001,116.4001,11000),p(39.901,116.401,120000,1),p(39.9011,116.4011,130000,1))
        val track=Track("test",1000,false,points)
        val original=track.copy(points=points.toList())
        val outline=RouteOutline.from(listOf(track,track.copy(id="other",points=listOf(p(39.902,116.402,150000)))))
        assertEquals(3,outline.paths.size);assertEquals(original,track)
        outline.fit(900.0,700.0).flatten().forEach {assertTrue(it.x in 0.0..900.0 && it.y in 0.0..700.0)}
    }
    @Test fun dateLineCrossingDoesNotFlattenTheRouteAcrossTheWorld() {
        val outline=RouteOutline.from(listOf(Track("date-line",1,false,listOf(p(0.0,179.9999,1000),p(0.0,-179.9999,11000)))))
        assertTrue(outline.width<.001)
        val fitted=outline.fit(900.0,700.0).flatten()
        assertTrue(fitted.maxOf {it.x}-fitted.minOf {it.x}>700)
    }
    @Test fun demoAndInvalidCoordinatePointsDoNotBecomeSharedRoutes() {
        val real=Track("real",1,false,listOf(p(39.9,116.4,1000),p(Double.NaN,116.4,2000),p(39.901,116.401,3000)))
        val outline=RouteOutline.from(listOf(real,real.copy(id="demo",demo=true)))
        assertEquals(2,outline.paths.size)
        assertTrue(outline.fit(900.0,700.0).flatten().all {it.x.isFinite() && it.y.isFinite()})
    }
}
