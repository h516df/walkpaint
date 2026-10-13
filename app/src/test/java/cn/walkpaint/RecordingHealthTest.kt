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
    @Test fun retryRequiresProviderAndDoesNotThrashRecentRegistration() {
        assertTrue(RecordingHealth.shouldRetry(false, true, 0, 0))
        assertFalse(RecordingHealth.shouldRetry(false, false, 100_000, 100_000))
        assertFalse(RecordingHealth.shouldRetry(true, true, 100_000, 5_000))
        assertFalse(RecordingHealth.shouldRetry(true, true, 2_000, 100_000))
        assertTrue(RecordingHealth.shouldRetry(true, true, 100_000, 100_000))
    }
}
