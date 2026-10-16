package cn.walkpaint

import org.junit.Assert.*
import org.junit.Test

class RecordingHealthTest {
    @Test fun gapsUseCallbacksIncludingFilteredFixesAndAreCountedOnce() {
        val h = RecordingHealth()
        assertNull(h.callback(1000))
        assertNull(h.callback(45_000))
        assertEquals(91_000L, h.callback(136_000))
        assertNull(h.callback(138_000))
    }
    @Test fun acquiringSatellitesMustNotRestartAnActiveGpsSubscription() {
        assertFalse(RecordingHealth.shouldRetry(true,true,3_691_000,100_000,acquiringGnss=true))
        assertTrue(RecordingHealth.shouldRetry(false,true,3_691_000,100_000,acquiringGnss=true))
        assertTrue(RecordingHealth.shouldRetry(true,true,3_691_000,100_000,acquiringGnss=false))
    }
    @Test fun zeroUsedSatellitesCannotDeferSilentGpsForever() {
        assertFalse(RecordingHealth.shouldRetry(true,true,120_000,858_000,true))
        // Replay the owner's 0.45 report: fresh satellite updates, no GPS for 266s.
        assertTrue(RecordingHealth.shouldRetry(true,true,266_000,858_000,true))
        assertFalse(RecordingHealth.shouldRetry(true,true,300_000,30_000,true))
        assertFalse(RecordingHealth.shouldRetry(true,true,1_000,858_000,true))
        assertFalse(RecordingHealth.shouldRetry(true,false,266_000,858_000,true))
    }
    @Test fun retryRequiresProviderAndDoesNotThrashRecentRegistration() {
        assertTrue(RecordingHealth.shouldRetry(false, true, 0, 0))
        assertFalse(RecordingHealth.shouldRetry(false, false, 100_000, 100_000))
        assertFalse(RecordingHealth.shouldRetry(true, true, 100_000, 5_000))
        assertFalse(RecordingHealth.shouldRetry(true, true, 2_000, 100_000))
        assertTrue(RecordingHealth.shouldRetry(true, true, 100_000, 100_000))
    }
}
