package cn.walkpaint

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A backup file is the last line of defence for someone's recorded footprints: if a phone is
 * replaced, or the app has to be reinstalled, the only way the history comes back is
 * export -> import. [FootprintDatabase.importBackup] decodes the whole backup text with
 * [TrackCodec], so the file format itself has to stay readable. These tests pin that contract
 * without a device.
 */
class TrackCodecTest {

    private val realTrack = Track(
        id = "1f2e3d4c-0000-4a5b-8c7d-9e0f1a2b3c4d",
        started = 1_760_000_000_000L,
        demo = false,
        points = listOf(
            TrackPoint(51.7520, -1.2577, 1_760_000_000_000L, 4.5f, 0),
            TrackPoint(51.7524, -1.2570, 1_760_000_008_000L, 12.25f, 0),
            TrackPoint(51.7529, -1.2562, 1_760_000_120_000L, 3.7f, 1),
        ),
    )

    private val demoTrack = Track(
        id = "demo-track",
        started = 1_759_000_000_000L,
        demo = true,
        points = listOf(TrackPoint(31.2304, 121.4737, 1_759_000_000_000L, 8.0f, 0)),
    )

    @Test
    fun roundTripKeepsEveryAcceptedPoint() {
        val original = TrackState(listOf(realTrack, demoTrack), activeId = realTrack.id)
        val decoded = TrackCodec.decode(TrackCodec.encode(original))
        assertEquals(original.tracks, decoded.tracks)
        assertEquals(original.activeId, decoded.activeId)
    }

    @Test
    fun accuracySurvivesTheFloatRoundTrip() {
        val original = TrackState(listOf(realTrack))
        val decoded = TrackCodec.decode(TrackCodec.encode(original))
        val before = original.tracks.single().points.map { it.accuracy }
        val after = decoded.tracks.single().points.map { it.accuracy }
        assertEquals(before, after)
    }

    @Test
    fun missingActiveIdStaysMissing() {
        val decoded = TrackCodec.decode(TrackCodec.encode(TrackState(listOf(realTrack), activeId = null)))
        assertNull(decoded.activeId)
    }

    /**
     * [FootprintDatabase.exportBackup] wraps the track payload in a JSON object that also carries
     * the road packs. Import hands that whole text to [TrackCodec.decode], so the envelope must not
     * break decoding.
     */
    @Test
    fun exportEnvelopeWithRoadPacksStillDecodes() {
        val tracks = TrackCodec.encode(TrackState(listOf(realTrack, demoTrack), activeId = null))
        val envelope = JSONObject(tracks)
            .put("roadBackupVersion", 1)
            .put("roadPacks", JSONArray())
            .toString()
        val decoded = TrackCodec.decode(envelope)
        assertEquals(listOf(realTrack, demoTrack), decoded.tracks)
    }

    @Test
    fun unsupportedVersionIsRejected() {
        val text = JSONObject().put("version", 2).put("activeId", JSONObject.NULL)
            .put("tracks", JSONArray()).toString()
        val error = assertThrows(IllegalArgumentException::class.java) { TrackCodec.decode(text) }
        assertTrue("版本错误应给出可读原因，实际 ${error.message}", error.message?.contains("不支持") == true)
    }

    @Test
    fun outOfRangeCoordinateInABackupIsRejected() {
        val bad = JSONObject(TrackCodec.encode(TrackState(listOf(realTrack)))).toString()
            .replace("51.752", "95.752")
        assertThrows(IllegalArgumentException::class.java) { TrackCodec.decode(bad) }
    }

    @Test
    fun emptyBackupIsStillValid() {
        val decoded = TrackCodec.decode(TrackCodec.encode(TrackState()))
        assertTrue(decoded.tracks.isEmpty())
        assertNull(decoded.activeId)
    }
}
