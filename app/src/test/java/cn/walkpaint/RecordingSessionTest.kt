package cn.walkpaint

import org.junit.Assert.*
import org.junit.Test

class RecordingSessionTest {
    @Test fun stoppingRejectsQueuedPositions() {
        val s=RecordingSession();val ticket=s.start();assertTrue(s.accepts(ticket))
        s.stop();assertFalse(s.active);assertFalse(s.accepts(ticket))
    }
    @Test fun restartAcceptsNewPositionsButNeverOldQueuedPositions() {
        val s=RecordingSession();val old=s.start();s.stop();val resumed=s.start()
        assertTrue(s.active);assertTrue(s.accepts(resumed));assertFalse(s.accepts(old))
    }
    @Test fun repeatedStopsCannotReviveAClosedSession() {
        val s=RecordingSession();val old=s.start();s.stop();s.stop()
        assertFalse(s.accepts(old));assertFalse(s.accepts(s.ticket()))
    }
}
