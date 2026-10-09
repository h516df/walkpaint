package cn.walkpaint

import org.junit.Assert.*
import org.junit.Test

class RoadMapFocusTest {
    private val point=TrackPoint(51.754,-1.255,100,5f)
    @Test fun currentFixWinsOverOldFootprintAndCatalog() {
        val current=point.copy(lat=51.755,time=200)
        assertEquals(current,RoadMapFocus.choose(TrackState(listOf(Track("old",0,false,listOf(point)))),current,RoadState()))
    }
    @Test fun latestRealFootprintWinsRegardlessOfTrackOrder() {
        val latest=point.copy(time=200)
        val state=TrackState(listOf(Track("old",0,false,listOf(point)),Track("new",0,false,listOf(latest)),
            Track("demo",0,true,listOf(point.copy(time=900,lat=31.2)))))
        assertEquals(latest,RoadMapFocus.choose(state,null,RoadState()))
    }
    @Test fun emptyDataDoesNotInventALocation() {
        assertNull(RoadMapFocus.choose(TrackState(),null,RoadState()))
    }
    @Test fun importedGeometryIsFallbackOnlyWithoutAnyFix() {
        val road=RoadSegment("test",listOf(point,point.copy(lat=51.7542)))
        assertEquals(road.points[1],RoadMapFocus.choose(TrackState(),null,RoadState(roads=listOf(road))))
    }
}
