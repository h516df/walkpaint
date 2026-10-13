package cn.walkpaint

import org.junit.Assert.*
import org.junit.Test

class ProblemHistoryTest {
    @Test fun repeatedEventsAggregateAndSurviveRestart() {
        val h=ProblemHistory(now=1000);repeat(1000) { h.record(ProblemCode.GPS_FILTERED,1000+it.toLong()) }
        val restored=ProblemHistory(h.encode(),now=2000)
        assertEquals(1,restored.snapshot(2000).size);assertEquals(1000L,restored.snapshot(2000).single().count)
        assertNull(ProblemCode.fromRecording("heartbeat","lat=51.75 secret",null))
        assertNull(ProblemCode.fromRecording("gps","token=bad",null))
    }
    @Test fun retainedHistoryHasTimeCountAndByteLimits() {
        val h=ProblemHistory(now=1);repeat(700) { h.record(ProblemCode.RECORDING_FAILED,1+it*600_000L) }
        assertEquals(500,h.snapshot(1+699*600_000L).size)
        assertTrue(h.encode().toByteArray().size<=ProblemHistory.MAX_BYTES)
        assertTrue(h.snapshot(1+699*600_000L+ProblemHistory.KEEP_MS+1).isEmpty())
    }
    @Test fun disabledCollectionAndClearDoNotMutateOtherData() {
        val h=ProblemHistory(now=1);h.record(ProblemCode.BACKUP_FAILED,1);val stored=h.encode()
        h.setCollection(false);h.record(ProblemCode.GPS_GAP,2);assertEquals(stored,h.encode())
        h.clear();assertTrue(h.snapshot(2).isEmpty());assertFalse(h.enabled)
    }
    @Test fun corruptedAndPrivateFieldsAreNeverLoadedAsText() {
        val h=ProblemHistory("latitude=51.75|1|1|1\nTOKEN_secret|1|1|1\nRECORDING_FAILED|1|2|3\nGPS_GAP|9|2|4",now=3)
        assertEquals(1,h.snapshot(3).size);assertFalse(h.encode().contains("secret"))
        assertTrue(ProblemHistory("x".repeat(ProblemHistory.MAX_BYTES+1),now=3).snapshot(3).isEmpty())
    }
    @Test fun recordingMessagesAreMappedToFixedVocabulary() {
        assertEquals(ProblemCode.RECORDING_FAILED,ProblemCode.fromRecording("failure","DATABASE_secret_lat=51.75",null))
        assertEquals(ProblemCode.GPS_GAP,ProblemCode.fromRecording("gps","RAW_COORDINATES",45001))
    }
    @Test fun filtersRemainDistinguishableAndNoRoutineEventCreatesHistory() {
        assertEquals(ProblemCode.GPS_ACCURACY,ProblemCode.fromRecording("filtered","MISSING_ACCURACY",null))
        assertEquals(ProblemCode.GPS_STATIONARY,ProblemCode.fromRecording("filtered","STATIONARY_OR_SMALL_MOVE",null))
        assertEquals(ProblemCode.GPS_JUMP,ProblemCode.fromRecording("filtered","JUMP_OR_TOO_FAST",null))
        listOf("heartbeat","saved","valid_gps","gnss","power").forEach { assertNull(ProblemCode.fromRecording(it,"secret",null)) }
        assertTrue(ProblemCode.RECORDING_FAILED.critical);assertFalse(ProblemCode.GPS_ACCURACY.critical)
    }
    @Test fun continuousRepeatIsSplitIntoBoundedTimeBuckets() {
        val h=ProblemHistory(now=1)
        repeat(10) { h.record(ProblemCode.GPS_FILTERED,1+it*60_000L) }
        assertEquals(2,h.snapshot(1+9*60_000L).size)
        assertEquals(10L,h.snapshot(1+9*60_000L).sumOf { it.count })
    }
    @Test fun lateSystemExitDoesNotEraseNewerRecoveryOrFailureClues() {
        val h=ProblemHistory(now=10_000)
        h.record(ProblemCode.RECOVERY,10_000)
        h.record(ProblemCode.PROCESS_EXIT,5_000,10_000)
        assertEquals(2,h.snapshot(10_000).size)
        assertEquals(ProblemCode.RECOVERY,h.snapshot(10_000).first().code)
    }
    @Test fun expiredOrFutureSystemEventsCannotAdvanceOrReviveHistory() {
        val now=ProblemHistory.KEEP_MS+1000
        val h=ProblemHistory(now=now);h.record(ProblemCode.RECOVERY,now)
        h.record(ProblemCode.PROCESS_EXIT,1,now)
        h.record(ProblemCode.PROCESS_EXIT,now+1000,now)
        assertEquals(1,h.snapshot(now).size)
        assertEquals(ProblemCode.RECOVERY,h.snapshot(now).single().code)
    }
}
