package cn.walkpaint
import org.junit.Assert.*
import org.junit.Test

class BackupPolicyTest {
    @Test fun newFileStopsAtBudgetButExistingDayCanBeRefreshed() {
        assertNotNull(BackupBudget.decide(30,1000,0,false,BackupBudget.MIN_FREE).problem)
        assertNull(BackupBudget.decide(30,1000,100,true,BackupBudget.MIN_FREE).problem)
    }
    @Test fun existingOverBudgetFilesArePreservedByRefusingNewWrites() {
        assertNotNull(BackupBudget.decide(31,1000,100,true,Long.MAX_VALUE).problem)
        assertNotNull(BackupBudget.decide(1,BackupBudget.MAX_TOTAL+1,100,true,Long.MAX_VALUE).problem)
    }
    @Test fun remainingBudgetCapsTheOutputBeforeAtomicReplacement() {
        val d=BackupBudget.decide(2,BackupBudget.MAX_TOTAL-100,200,true,Long.MAX_VALUE)
        assertEquals(300L,d.maxBytes)
    }
    @Test fun lowStorageAndSingleSnapshotHaveExplicitLimits() {
        assertNotNull(BackupBudget.decide(0,0,0,false,1).problem)
        assertEquals(BackupBudget.MAX_FILE,BackupBudget.decide(0,0,0,false,Long.MAX_VALUE).maxBytes)
    }
    @Test fun pendingRequestsCoalesceAndFailureHasBoundedRetry() {
        val s=BackupSchedule()
        assertTrue(s.start(1_000_000,1000,0));assertFalse(s.start(1_000_001,1001,0))
        s.finished();assertFalse(s.start(1_000_002,1002,0))
        assertTrue(s.start(1_300_000,301_000,0))
    }
    @Test fun successIsOnlyCountedAfterTheWorkerCompletes() {
        val s=BackupSchedule();assertTrue(s.start(1_000_000,1000,0));s.finished()
        assertFalse(s.start(1_300_000,301_000,1_000_000))
        assertTrue(s.start(2_800_000,1_801_000,1_000_000))
    }
    @Test fun wallClockCorrectionDoesNotSuppressBackupsForever() {
        assertTrue(BackupSchedule().start(1_000_000,1000,9_000_000))
    }
    @Test fun closingPreventsNewWork() {
        val s=BackupSchedule();s.close();assertFalse(s.start(1_000_000,1000,0))
    }
}
