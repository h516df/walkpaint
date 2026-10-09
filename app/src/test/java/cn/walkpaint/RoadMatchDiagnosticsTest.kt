package cn.walkpaint
import org.junit.Assert.*
import org.junit.Test
class RoadMatchDiagnosticsTest {
    private val a=TrackPoint(51.75,-1.25,1000,5f)
    private val b=a.copy(lon=-1.2497,time=11000)
    private val road=RoadSegment("public-fixture",listOf(a.copy(lon=-1.251),a.copy(lon=-1.249)))
    @Test fun acceptedGpsCanHaveZeroRoadCoverageWithoutFalseColor() {
        assertTrue(Geo.accept(a,b))
        val catalog=RoadCatalog(emptyList())
        assertNull(catalog.traversed(a,b))
        assertEquals("当前道路目录无附近候选",RoadMatchDiagnostics.reason(a,b,catalog))
    }
    @Test fun retainedGpsBetween20And50MetersDoesNotMeetRoadPrecision() {
        val poor=b.copy(accuracy=30f)
        assertTrue(Geo.accept(null,poor))
        val catalog=RoadCatalog(listOf(road))
        assertNull(catalog.traversed(a,poor))
        assertTrue(RoadMatchDiagnostics.reason(a,poor,catalog).contains("精度不足"))
    }
    @Test fun driftAmbiguityStaysRejected() {
        val parallel=RoadSegment("parallel",road.points.map { it.copy(lat=it.lat+0.00001) })
        val catalog=RoadCatalog(listOf(road,parallel))
        assertNull(catalog.traversed(a,b))
        assertTrue(RoadMatchDiagnostics.reason(a,b,catalog).contains("无法唯一匹配"))
    }
    @Test fun reportIsCoordinateFreeAndDoesNotMutateHistory() {
        val state=TrackState(listOf(Track("private-id",1000,false,listOf(a,b))))
        val roads=RoadState(listOf(road))
        val report=RoadMatchDiagnostics.report(state,roads)
        assertFalse(report.contains("private-id"));assertFalse(report.contains("51.75"))
        assertTrue(report.contains("当前道路包可确认"));assertEquals(0.0,roads.meters,0.0)
        assertEquals(listOf(a,b),state.realTracks.single().points)
    }
}
