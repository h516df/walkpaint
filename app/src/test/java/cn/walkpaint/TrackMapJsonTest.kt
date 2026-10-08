package cn.walkpaint

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class TrackMapJsonTest {
    private fun p(lat: Double, lon: Double, seconds: Long, segment: Int = 0) =
        TrackPoint(lat, lon, seconds * 1_000L, 5f, segment)

    @Test fun firstAcceptedFixPaintsVisibleSeedWithoutInventingDistance() {
        val track = Track("short", 1_000L, false, listOf(p(31.23040, 121.47370, 1)))
        val state = TrackState(listOf(track))
        val result = JSONObject(TrackMapJson.encode(state.realTracks))
        val features = result.getJSONArray("features")
        assertEquals(1, features.length())
        assertEquals("Point", features.getJSONObject(0).getJSONObject("geometry").getString("type"))
        assertEquals(0.0, state.totalMeters, 0.001)
    }

    @Test fun shortPlausibleWalkPaintsLineAndSeed() {
        val track = Track("short", 1_000L, false, listOf(
            p(31.23040, 121.47370, 1),
            p(31.23048, 121.47378, 5),
            p(31.23056, 121.47386, 9)
        ))
        val result = JSONObject(TrackMapJson.encode(listOf(track)))
        val features = result.getJSONArray("features")
        assertEquals(3, features.length())
        val geometry = features.getJSONObject(2).getJSONObject("geometry")
        assertEquals("LineString", geometry.getString("type"))
        assertEquals(3, geometry.getJSONArray("coordinates").length())
        assertTrue(Geo.distance(track.points) > 10.0)
    }

    @Test fun gapsNeverBecomeImaginaryConnectingRoad() {
        val track = Track("split", 1_000L, false, listOf(
            p(31.0, 121.0, 1, 0), p(31.0001, 121.0, 5, 0),
            p(32.0, 122.0, 8, 1), p(32.0001, 122.0, 12, 1)
        ))
        val f = JSONObject(TrackMapJson.encode(listOf(track))).getJSONArray("features")
        assertEquals(6, f.length())
        assertEquals(2, (0 until f.length()).count {
            f.getJSONObject(it).getJSONObject("geometry").getString("type") == "LineString"
        })
        assertTrue(Geo.distance(track.points) < 30.0)
    }
}
