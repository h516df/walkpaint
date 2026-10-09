package cn.walkpaint

import org.junit.Assert.*
import org.junit.Test

class DiagnosticBufferTest {
    @Test fun frequentCallbacksFoldWithoutRequestingPerFixDiskWrites() {
        val buffer = DiagnosticBuffer()
        repeat(1000) { i ->
            assertFalse(buffer.record("gps", "GPS_CALLBACK", i * 2000L))
            assertFalse(buffer.record("saved", "SQLITE_SAVED", i * 2000L + 1))
        }
        assertEquals(1000L, buffer.long("gps_count"))
        assertEquals(1000L, buffer.long("saved_count"))
        assertEquals(1_998_001L, buffer.long("saved_at"))
        assertTrue(buffer.dirty)
        assertEquals(30_000L, DiagnosticBuffer.FLUSH_INTERVAL_MS)
    }
    @Test fun startStopFailureRegistrationAndGapRequestImmediateFlush() {
        val buffer = DiagnosticBuffer()
        listOf("start", "stop", "failure", "register").forEach {
            assertTrue(buffer.record(it, "REASON", 1000))
        }
        assertFalse(buffer.record("heartbeat", "SERVICE_ALIVE", 2000))
        assertFalse(buffer.record("filtered", "STATIONARY_OR_SMALL_MOVE", 3000))
        assertTrue(buffer.record("gps", "GPS_CALLBACK", 70000, 68000))
        assertEquals(68000L, buffer.long("max_gap_ms"))
        assertEquals(1L, buffer.long("gap_count"))
    }
    @Test fun validGpsPowerAndRecoveryHaveSeparateCoordinateFreeEvidence() {
        val b=DiagnosticBuffer()
        b.record("gps","GPS_CALLBACK",1000)
        assertEquals(0L,b.long("valid_gps_at"))
        b.record("valid_gps","VALID_GPS_RECEIVED",2000)
        assertEquals(2000L,b.long("valid_gps_at"));assertEquals(0L,b.long("saved_at"))
        assertTrue(b.record("power","SCREEN_OFF_IDLE_true_SAVER_false",3000))
        assertTrue(b.record("recovery","PREVIOUS_SESSION_WITHOUT_STOP_REASON",4000))
        assertTrue(b.record("exit","ANDROID_REASON_10",2500))
        assertEquals(2500L,b.long("process_exit_at"))
        assertFalse(b.snapshot().keys.any { it.contains("lat") || it.contains("lon") || it.contains("route") })
    }
    @Test fun persistedSnapshotSurvivesReloadAndIncludesPendingRoutineCounts() {
        val buffer = DiagnosticBuffer(mapOf("saved_count" to 5L, "gps_count" to 8L))
        buffer.record("saved", "SQLITE_SAVED", 1000)
        assertTrue(buffer.record("stop", "USER_PAUSED", 2000))
        val disk = buffer.snapshot()
        buffer.persisted()
        assertFalse(buffer.dirty)
        val reloaded = DiagnosticBuffer(disk)
        assertEquals(6L, reloaded.long("saved_count"))
        assertEquals("USER_PAUSED", reloaded.snapshot()["stop_reason"])
        assertFalse(reloaded.record("saved", "SQLITE_SAVED", 3000))
        assertEquals(7L, reloaded.long("saved_count"))
        assertEquals(6L, disk["saved_count"])
    }
}
