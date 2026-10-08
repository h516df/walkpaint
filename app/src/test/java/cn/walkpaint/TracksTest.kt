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
        assertFalse(Geo.accept(a, TrackPoint(31.0001, 121.0, 2000, 0f)))
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

    @Test fun shortWalkWithReliableMotionIsNotDiscardedOrHiddenFromStats() {
        val first = TrackPoint(31.0, 121.0, 1_000L, 16f)
        val second = TrackPoint(31.00009, 121.0, 9_000L, 16f)
        // A ten-meter real walk can be lost with the former accuracy x 1.25 gate.
        assertFalse(Geo.accept(first, second))
        assertTrue(Geo.accept(first, second, confidentlyMoving = true))
        val savedTrack = Track("moving", first.time, false, listOf(first, second))
        assertTrue(TrackState(listOf(savedTrack)).totalMeters > 9.0)
    }

    @Test fun stationaryDriftAndTeleportAreRejectedEvenWithMotionOption() {
        val atRest = TrackPoint(31.0, 121.0, 1_000L, 16f)
        val noise = TrackPoint(31.00005, 121.0, 5_000L, 16f)
        assertFalse(Geo.accept(atRest, noise))
        val teleport = TrackPoint(31.01, 121.0, 2_000L, 5f)
        assertFalse(Geo.accept(atRest, teleport, confidentlyMoving = true))
    }

    @Test fun segmentGapDoesNotAddFictitiousWalkDistance() {
        val first = TrackPoint(31.0, 121.0, 1_000L, 5f, 0)
        val last = TrackPoint(31.001, 121.0, 70_000L, 5f, 1)
        assertEquals(0.0, Geo.distance(listOf(first, last)), 1e-6)
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
