package cn.walkpaint

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class NearbyRoadsTest {
    private fun response(): JSONObject = JSONObject().put("elements", JSONArray().put(JSONObject()
        .put("type", "way").put("id", 1675518).put("version", 3)
        .put("tags", JSONObject().put("highway", "residential"))
        .put("nodes", JSONArray().put(1).put(2))
        .put("geometry", JSONArray().put(JSONObject().put("lat", 51.754).put("lon", -1.255))
            .put(JSONObject().put("lat", 51.7541).put("lon", -1.255)))))

    @Test fun completeWayUsesExistingCodecAndStableIdentity() {
        val pack = RoadPackCodec.decode(NearbyRoads.decode(response().toString()))
        assertEquals(1, pack.roads.size)
        assertEquals(3, pack.roads.single().osmVersion)
        assertTrue(pack.roads.single().stableId.startsWith("osm:way:1675518:g:"))
        assertEquals(NearbyRoads.ENDPOINT, pack.source)
    }
    @Test fun incompleteGeometryIsRejected() {
        val root = response()
        root.getJSONArray("elements").getJSONObject(0).getJSONArray("nodes").put(3)
        assertThrows(IllegalArgumentException::class.java) { NearbyRoads.decode(root.toString()) }
    }
    @Test fun partialTimeoutResultIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { NearbyRoads.decode(response().put("remark", "timeout").toString()) }
    }
    @Test fun emptyResponseIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { NearbyRoads.decode("{\"elements\":[]}") }
    }
    @Test fun invalidVersionIsRejected() {
        val root = response()
        root.getJSONArray("elements").getJSONObject(0).put("version", 0)
        assertThrows(IllegalArgumentException::class.java) { NearbyRoads.decode(root.toString()) }
    }
    @Test fun smallQuantizedRegionAndUnclippedOutput() {
        val area = NearbyRoads.area(51.75412, -1.25513)
        assertEquals(51.754, (area.south+area.north)/2, 0.000001)
        assertEquals(-1.255, (area.west+area.east)/2, 0.000001)
        assertEquals(1200.0, Geo.meters(TrackPoint(area.south,-1.255,0,5f), TrackPoint(area.north,-1.255,0,5f)), 2.0)
        assertTrue(NearbyRoads.query(area).endsWith("out meta geom;"))
        assertFalse(NearbyRoads.query(area).contains("geom("))
    }
    @Test fun invalidAreaIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { NearbyRoads.area(Double.NaN, 0.0) }
        assertThrows(IllegalArgumentException::class.java) { NearbyRoads.area(85.0, 0.0) }
    }
    @Test fun freshReliableLocationRequired() {
        assertTrue(NearbyRoads.usableFix(300_000_000_000L, 5f))
        assertFalse(NearbyRoads.usableFix(300_000_000_001L, 5f))
        assertFalse(NearbyRoads.usableFix(-1, 5f))
        assertFalse(NearbyRoads.usableFix(0, 0f))
        assertFalse(NearbyRoads.usableFix(0, Float.NaN))
        assertFalse(NearbyRoads.usableFix(0, 201f))
    }
}
