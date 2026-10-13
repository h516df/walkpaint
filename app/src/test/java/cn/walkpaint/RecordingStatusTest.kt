package cn.walkpaint
import org.junit.Assert.*
import org.junit.Test
class RecordingStatusTest {
    private val base = RecordingSignals(now=100_000, startedAt=10_000, callbackAt=99_000, validAt=99_000)
    @Test fun serviceHeartbeatCannotHideStalledCallbackOrOldCommit() {
        val status=RecordingStatus.assess(base.copy(callbackAt=20_000,validAt=20_000,savedAt=20_000))
        assertEquals("CALLBACK_INTERRUPTED",status.code); assertFalse(status.healthy)
    }
    @Test fun newSessionDoesNotReuseOldGpsAndSave() {
        assertEquals("STARTING",RecordingStatus.assess(base.copy(startedAt=99_900,callbackAt=99_000,savedAt=99_000)).code)
    }
    @Test fun callbackIsNotValidGpsAndValidGpsIsNotACommit() {
        assertEquals("INVALID_FIX",RecordingStatus.assess(base.copy(validAt=0)).code)
        assertEquals("WAITING_MOVEMENT",RecordingStatus.assess(base).code)
        assertEquals("SAVED",RecordingStatus.assess(base.copy(savedAt=99_500)).code)
    }
    @Test fun freshRejectedFixDoesNotReportHealthyRecording() {
        for(reason in listOf("UNRELIABLE_ACCURACY","STALE_FIX","INVALID_COORDINATE","MISSING_ACCURACY")) {
            assertFalse(RecordingStatus.assess(base.copy(filterAt=99_500,filter=reason,savedAt=98_000)).healthy)
        }
    }
    @Test fun permissionProviderPauseAndServiceStopAreDistinct() {
        assertEquals("PAUSED",RecordingStatus.assess(base.copy(enabled=false)).code)
        assertEquals("PERMISSION",RecordingStatus.assess(base.copy(fine=false)).code)
        assertEquals("GPS_DISABLED",RecordingStatus.assess(base.copy(gpsEnabled=false)).code)
        assertEquals("SERVICE_STOPPED",RecordingStatus.assess(base.copy(running=false)).code)
    }
    @Test fun failedWriteStaysVisibleUntilActualCommit() {
        assertEquals("SAVE_FAILED",RecordingStatus.assess(base.copy(failure="DATABASE_WRITE_FAILED",failureAt=99_500,savedAt=99_000)).code)
        assertEquals("SAVED",RecordingStatus.assess(base.copy(failure="DATABASE_WRITE_FAILED",failureAt=99_000,savedAt=99_500)).code)
    }
    @Test fun rejectedMovementAfterCommitDoesNotClaimThatNewFixWasSaved() {
        val status=RecordingStatus.assess(base.copy(savedAt=98_000,filterAt=99_500,filter="JUMP_OR_TOO_FAST"))
        assertEquals("WAITING_MOVEMENT",status.code)
        assertTrue(status.hint.contains("本次未保存"))
    }
    @Test fun newCallbackCannotReuseValidFixFromPreviousSession() {
        assertEquals("INVALID_FIX",RecordingStatus.assess(base.copy(startedAt=99_500,callbackAt=99_900,validAt=99_000)).code)
    }
    @Test fun registrationFailureAndFutureTimestampsAreNotHealthy() {
        assertEquals("REGISTER_FAILED",RecordingStatus.assess(base.copy(callbackAt=20_000,failure="GPS_REGISTER_SecurityException",failureAt=99_000)).code)
        assertFalse(RecordingStatus.assess(base.copy(callbackAt=101_000,validAt=101_000)).healthy)
    }
}
