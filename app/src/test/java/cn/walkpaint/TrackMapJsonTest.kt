package cn.walkpaint

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class TrackMapJsonTest {
    private fun p(lat: Double, lon: Double, seconds: Long, segment: Int = 0) =
        TrackPoint(lat, lon, seconds * 1_000L, 5f, segment)

    @Test fun aLoneFixPaintsNoBeadAndInventsNoDistance() {
        // The renderer used to draw a circle for the start and end of every run, which read on the
        // phone as a string of beads sitting on the line. Ground is painted by the veil instead.
        val track = Track("short", 1_000L, false, listOf(p(31.23040, 121.47370, 1)))
        val state = TrackState(listOf(track))
        val features = JSONObject(TrackMapJson.encode(state.realTracks)).getJSONArray("features")
        assertEquals(0, features.length())
        assertEquals(0.0, state.totalMeters, 0.001)
    }

    @Test fun aShortWalkIsOneLineWithNoPoints() {
        val track = Track("short", 1_000L, false, listOf(
            p(31.23040, 121.47370, 1),
            p(31.23048, 121.47392, 5),   // a real bend, not a mathematically straight line
            p(31.23056, 121.47386, 9)
        ))
        val features = JSONObject(TrackMapJson.encode(listOf(track))).getJSONArray("features")
        assertEquals(1, features.length())
        val geometry = features.getJSONObject(0).getJSONObject("geometry")
        assertEquals("LineString", geometry.getString("type"))
        assertEquals(3, geometry.getJSONArray("coordinates").length())
        assertEquals(0, (0 until features.length()).count {
            features.getJSONObject(it).getJSONObject("geometry").getString("type") == "Point"
        })
        assertTrue(Geo.distance(track.points) > 10.0)
    }

    @Test fun gapsNeverBecomeImaginaryConnectingRoad() {
        val track = Track("split", 1_000L, false, listOf(
            p(31.0, 121.0, 1, 0), p(31.0001, 121.0, 5, 0),
            p(32.0, 122.0, 8, 1), p(32.0001, 122.0, 12, 1)
        ))
        val f = JSONObject(TrackMapJson.encode(listOf(track))).getJSONArray("features")
        assertEquals(2, f.length())
        assertEquals(2, (0 until f.length()).count {
            f.getJSONObject(it).getJSONObject("geometry").getString("type") == "LineString"
        })
        assertTrue(Geo.distance(track.points) < 30.0)
    }

    @Test fun walkingTheSameStreetTwiceLeavesOneLineNotTwo() {
        // The second pass carries its own GPS offset. Drawn faithfully it sits beside the first and
        // the pair reads as a braid; the drawn geometry has to collapse them.
        val out = listOf(p(31.0, 121.0, 1), p(31.001, 121.0, 11), p(31.002, 121.0, 21))
        val back = listOf(p(31.002, 121.00006, 31), p(31.001, 121.00006, 41), p(31.0, 121.00006, 51))
        val track = Track("revisit", 1_000L, false, out + back)
        val features = JSONObject(TrackMapJson.encode(listOf(track))).getJSONArray("features")
        assertEquals(1, features.length())
        assertTrue("a second line appeared",
            features.getJSONObject(0).getJSONObject("geometry").getJSONArray("coordinates").length() <= 4)
    }
}
