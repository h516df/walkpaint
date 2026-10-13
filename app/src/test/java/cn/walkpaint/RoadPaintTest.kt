package cn.walkpaint

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class RoadPaintTest {
    private fun p(lat: Double, lon: Double, time: Long = 0, segment: Int = 0) = TrackPoint(lat, lon, time, 5f, segment)
    private fun fixture(): RoadSegment {
        val json = JSONObject(javaClass.getResource("/road-fixture.json")!!.readText())
        val coords = json.getJSONArray("coordinates")
        return RoadSegment(json.getString("id"), (0 until coords.length()).map {
            val c = coords.getJSONArray(it); p(c.getDouble(1), c.getDouble(0))
        })
    }
    @Test fun followsCurvedGeometryAndHighlightsOnlyObservedSection() {
        val road = fixture()
        val interval = RoadMatcher.traversed(p(31.00005,121.0,1000), p(31.0001,121.00005,16000),listOf(road))!!
        val c = RoadCoverage(listOf(interval))
        val shape = c.highlighted(road).single()
        assertEquals(3,shape.size)
        assertEquals(31.0001,shape[1].lat,0.000001)
        assertEquals(121.0,shape[1].lon,0.000001)
        assertTrue(c.meters > Geo.meters(shape.first(),shape.last()))
        assertTrue(c.meters < road.length)
    }
    @Test fun reverseTraversalAndRepeatedDaysDoNotDoubleCount() {
        val road = fixture()
        val a = p(31.00005,121.0,1000); val b = p(31.0001,121.00005,16000)
        val forward = RoadMatcher.traversed(a,b,listOf(road))!!
        val reverse = RoadMatcher.traversed(b.copy(time=20000),a.copy(time=35000),listOf(road.copy(geometry=road.geometry.reversed())))!!
        val c = RoadCoverage(listOf(forward,reverse,forward))
        assertEquals(forward.to-forward.from,c.meters,0.01)
        assertEquals(1,c.snapshot().size)
        assertEquals(c.meters,RoadCoverage(c.snapshot()).meters,0.001)
    }
    @Test fun overlappingIntervalsMergeButUnvisitedGapsRemain() {
        val c = RoadCoverage(listOf(PaintedInterval("v1:r",0.0,10.0),PaintedInterval("v1:r",5.0,20.0),PaintedInterval("v1:r",25.0,30.0)))
        assertEquals(25.0,c.meters,0.001); assertEquals(2,c.snapshot().size)
    }
    @Test fun differentDatasetVersionsNeverShareCoverage() {
        val c = RoadCoverage(listOf(PaintedInterval("v1:r",0.0,10.0),PaintedInterval("v2:r",0.0,10.0)))
        assertEquals(2,c.snapshot().size)
    }
    @Test fun rejectsAmbiguousParallelRoads() {
        val roads = listOf(RoadSegment("v1:a",listOf(p(31.0,121.0),p(31.001,121.0))),
            RoadSegment("v1:b",listOf(p(31.0,121.00005),p(31.001,121.00005))))
        assertNull(RoadMatcher.match(p(31.0005,121.000025),roads))
    }
    @Test fun rejectsFarAndInaccurateFixesAndMissingGeometry() {
        assertNull(RoadMatcher.match(p(32.0,122.0),listOf(fixture())))
        assertNull(RoadMatcher.match(p(31.0,121.0).copy(accuracy=30f),listOf(fixture())))
        assertNull(RoadMatcher.match(p(31.0,121.0),emptyList()))
        assertNull(RoadMatcher.match(p(31.0,121.0),listOf(RoadSegment("",fixture().geometry))))
    }
    @Test fun gapsSegmentsDifferentRoadsAndStationaryFixesNeverPaint() {
        val a=p(31.00005,121.0,1000);val b=p(31.0001,121.00005,16000)
        assertNull(RoadMatcher.traversed(a,b.copy(time=50000),listOf(fixture())))
        assertNull(RoadMatcher.traversed(a,b.copy(segment=1),listOf(fixture())))
        assertNull(RoadMatcher.traversed(a,a.copy(time=2000),listOf(fixture())))
        assertNull(RoadMatcher.traversed(b,a,listOf(fixture())))
        val second=RoadSegment("v1:other",listOf(p(31.001,121.001),p(31.002,121.001)))
        assertNull(RoadMatcher.traversed(a,p(31.001,121.001,2000),listOf(fixture(),second)))
    }
    @Test fun implausibleSpeedCannotPaintRoad() {
        assertNull(RoadMatcher.traversed(p(31.0,121.0,1000),p(31.0002,121.0001,1100),listOf(fixture())))
    }
    @Test(expected=IllegalArgumentException::class) fun invalidIntervalsCannotCorruptCoverage() {
        RoadCoverage(listOf(PaintedInterval("v1:r",Double.NaN,10.0)))
    }
}
