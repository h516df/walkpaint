package cn.walkpaint

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class TracksTest {
    private fun point(lat: Double, lon: Double, time: Long, segment: Int = 0) =
        TrackPoint(lat, lon, time, 5f, segment)

    @Test fun distanceComputesPlausibleMeters() {
        val a = point(31.0, 121.0, 1000)
        val b = point(31.001, 121.0, 11000)
        assertTrue(Geo.meters(a, b) in 110.0..112.0)
        assertEquals(0.0, Geo.meters(a, a), 1e-5)
    }

    @Test fun rejectsJumpsBadAccuracyAndDuplicates() {
        val a = point(31.0, 121.0, 1000)
        assertFalse(Geo.accept(a, point(31.02, 121.0, 2000)))
        assertFalse(Geo.accept(a, point(31.0, 121.0, 2000)))
        assertFalse(Geo.accept(a, TrackPoint(31.0001, 121.0, 2000, 90f)))
        assertTrue(Geo.accept(a, point(31.0001, 121.0, 6000)))
    }

    @Test fun doesNotConnectTrackSegments() {
        val points = listOf(point(31.0, 121.0, 1000), point(31.0001, 121.0, 6000), point(31.1, 121.0, 7000, 1))
        assertTrue(Geo.distance(points) in 10.0..12.0)
    }

    @Test fun codecRoundTripKeepsActiveAndDemoSeparate() {
        val track = Track("walk", 1234, false, listOf(point(31.0, 121.0, 1000), point(31.0001, 121.0, 6000)))
        val demo = Track("demo", 1235, true, listOf(point(31.1, 121.1, 2000)))
        val state = TrackState(listOf(track, demo), "walk")
        val restored = TrackCodec.decode(TrackCodec.encode(state))
        assertEquals(state, restored)
        assertEquals(1, restored.realTracks.size)
        assertTrue(restored.totalMeters in 10.0..12.0)
    }

    @Test fun atomicStoreCanReloadPersistedState() {
        val dir = java.nio.file.Files.createTempDirectory("walkpaint-test").toFile()
        try {
            val f = File(dir, "tracks.json")
            TrackStore(f).update { TrackState(listOf(Track("persist", 10L, false))) }
            assertEquals("persist", TrackStore(f).snapshot().tracks.single().id)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun corruptFileIsNotOverwritten() {
        val dir = java.nio.file.Files.createTempDirectory("walkpaint-bad").toFile()
        try {
            val f = File(dir, "tracks.json")
            f.writeText("broken json")
            val store = TrackStore(f)
            assertNotNull(store.loadError)
            assertThrows(IllegalStateException::class.java) { store.update { it } }
            assertEquals("broken json", f.readText())
        } finally {
            dir.deleteRecursively()
        }
    }
}
