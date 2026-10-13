package cn.walkpaint

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class RoadPackTest {
    private fun text() = javaClass.getResource("/oxford-roads.geojson")!!.readText()
    private fun pack() = RoadPackCodec.decode(text())
    @Test fun realOsmRoadsKeepStableIdsAndCurve() {
        val pack = pack()
        assertEquals(6,pack.roads.size)
        assertTrue(pack.roads.all { it.stableId.startsWith("osm:way:") && it.length > 10 })
        val road = pack.roads.first { it.stableId.startsWith("osm:way:1675518:") }
        val a = road.points[1].copy(time=1000)
        val b = road.points[2].copy(time=21000)
        val interval = RoadCatalog(pack.roads).traversed(a,b)!!
        assertEquals(Geo.meters(a,b),interval.to-interval.from,0.1)
        val state = RoadState(pack.roads,listOf(interval))
        assertEquals("LineString",JSONObject(state.geoJson(true)).getJSONArray("features").getJSONObject(0)
            .getJSONObject("geometry").getString("type"))
        assertEquals(pack.roads.map { it.stableId },RoadPackCodec.decode(RoadPackCodec.encode(pack)).roads.map { it.stableId })
    }
    @Test fun reversedGeometryAndRepeatImportKeepIdentity() {
        val root = JSONObject(text())
        val coords = root.getJSONArray("features").getJSONObject(0).getJSONObject("geometry").getJSONArray("coordinates")
        val reversed = org.json.JSONArray((0 until coords.length()).reversed().map { coords.getJSONArray(it) })
        root.getJSONArray("features").getJSONObject(0).getJSONObject("geometry").put("coordinates",reversed)
        assertEquals(pack().roads[0].stableId,RoadPackCodec.decode(root.toString()).roads[0].stableId)
        reversed.getJSONArray(1).put(0,reversed.getJSONArray(1).getDouble(0)+0.00001)
        assertNotEquals(pack().roads[0].stableId,RoadPackCodec.decode(root.toString()).roads[0].stableId)
    }
    @Test fun noisyGpsPaintsTheRealCurveRatherThanGpsChord() {
        val roads=pack().roads
        val road=roads.first { it.stableId.startsWith("osm:way:1675518:") }
        val a=road.points[1].copy(lat=road.points[1].lat+4.0/111195,time=1000)
        val b=road.points[2].copy(lat=road.points[2].lat+4.0/111195,time=21000)
        val interval=RoadCatalog(roads).traversed(a,b)!!
        val painted=RoadCoverage(listOf(interval)).highlighted(road).single()
        assertTrue(Geo.meters(a,painted.first())>3.0)
        assertTrue(Geo.meters(b,painted.last())>3.0)
        assertTrue(RoadMatcher.match(painted.first(),roads)!!.error<0.01)
    }
    @Test fun rejectsLicenseCoordinatesTileSourcesAndMissingIdentity() {
        fun rejected(change: (JSONObject)->Unit) {
            val root=JSONObject(text()); change(root)
            try { RoadPackCodec.decode(root.toString()); fail("Unsafe pack accepted") } catch(_:Exception) { }
        }
        rejected { it.getJSONObject("walkpaint").put("license","unknown") }
        rejected { it.getJSONObject("walkpaint").put("coordinates","GCJ02") }
        rejected { it.getJSONObject("walkpaint").put("source","https://tiles.openfreemap.org/") }
        rejected { it.getJSONArray("features").getJSONObject(0).getJSONObject("properties").remove("osm_way_id") }
        rejected { it.getJSONArray("features").getJSONObject(0).getJSONObject("geometry").getJSONArray("coordinates")
            .getJSONArray(0).put(1,200.0) }
        rejected { it.getJSONArray("features").put(it.getJSONArray("features").getJSONObject(0)) }
    }
    @Test fun realRoadRepeatedReversedAndUnmappedAreHonest() {
        val catalog=RoadCatalog(pack().roads)
        val r=catalog.roads.first { it.stableId.startsWith("osm:way:1675518:") }
        val a=r.points[1].copy(time=1000); val b=r.points[2].copy(time=21000)
        val first=catalog.traversed(a,b)!!
        val reverse=catalog.traversed(b.copy(time=22000),a.copy(time=42000))!!
        assertEquals(first.to-first.from,RoadCoverage(listOf(first,reverse,first)).meters,0.1)
        assertNull(catalog.traversed(a,b.copy(time=47000)))
        assertNull(catalog.traversed(a,b.copy(accuracy=30f)))
        assertNull(catalog.traversed(a,b.copy(segment=1)))
        assertNull(catalog.traversed(a.copy(lat=31.0),b.copy(lat=31.001)))
        // The shared OSM junction is ambiguous, never guessed from two GPS fixes.
        val junction=r.points.last()
        assertNull(RoadMatcher.match(junction,catalog.roads))
    }
    @Test fun sameRoadEndpointsDoNotPaintThroughCrossing() {
        fun p(lat:Double,lon:Double,time:Long=0)=TrackPoint(lat,lon,time,5f)
        val horizontal=RoadSegment("a",listOf(p(51.0,0.0),p(51.0,0.001)))
        val vertical=RoadSegment("b",listOf(p(50.9995,0.0005),p(51.0005,0.0005)))
        assertNull(RoadMatcher.traversed(p(51.0,0.0001,1000),p(51.0,0.0009,20000),listOf(horizontal,vertical)))
    }
    @Test fun selfCrossingAndSparseLongTraversalAreRejected() {
        fun p(lat:Double,lon:Double,time:Long=0)=TrackPoint(lat,lon,time,5f)
        val loop=RoadSegment("loop",listOf(p(51.0,0.0),p(51.001,0.001),p(51.0,0.001),p(51.001,0.0)))
        assertNull(RoadMatcher.match(p(51.0005,0.0005),listOf(loop)))
        val long=RoadSegment("long",listOf(p(51.0,0.0),p(51.003,0.0)))
        assertNull(RoadMatcher.traversed(p(51.0,0.0,1000),p(51.002,0.0,45000),listOf(long)))
    }
    @Test fun catalogIncludesCrossingAwayFromEndpointCellsNearPole() {
        fun p(lat:Double,lon:Double,time:Long=0)=TrackPoint(lat,lon,time,5f)
        val main=RoadSegment("main",listOf(p(84.0,0.0),p(84.0,0.008)))
        val crossing=RoadSegment("cross",listOf(p(83.9995,0.004),p(84.0005,0.004)))
        assertNull(RoadCatalog(listOf(main,crossing)).traversed(p(84.0,0.0001,1000),p(84.0,0.0079,21000)))
    }
}
