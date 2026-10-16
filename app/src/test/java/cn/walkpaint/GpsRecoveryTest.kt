package cn.walkpaint
import org.junit.Assert.*
import org.junit.Test
class GpsRecoveryTest {
    @Test fun initialFailedRequestAlsoStartsTheRetryBackoff() {
        val p=GpsRecovery(1000);p.registrationAttempt(1000)
        assertEquals(GpsRecovery.Action.NONE,p.next(2000,false,true))
        assertEquals(GpsRecovery.Action.REGISTER,p.next(31000,false,true))
    }
    @Test fun failedRegistrationRetriesWithoutWaitingForColdAcquisition() {
        val p=GpsRecovery(1000)
        assertEquals(GpsRecovery.Action.REGISTER,p.next(2000,false,true))
        assertEquals(GpsRecovery.Action.NONE,p.next(31999,false,true))
        assertEquals(GpsRecovery.Action.REGISTER,p.next(32000,false,true))
        assertEquals(GpsRecovery.Action.REGISTER,p.next(62000,false,true))
        assertEquals(GpsRecovery.Action.NONE,p.next(92000,true,true))
    }
    @Test fun sixtyTwoSecondsAndFreshSatellitesCannotTriggerRepeatedColdStarts() {
        val p=GpsRecovery(1000)
        assertEquals(GpsRecovery.Action.NONE,p.next(63000,true,true))
        assertEquals(GpsRecovery.Action.NONE,p.next(180999,true,true))
        assertEquals(GpsRecovery.Action.PROBE,p.next(181000,true,true))
        assertTrue(p.probeFinished(196000))
        for(t in 197000L..480000L step 30000) assertEquals(GpsRecovery.Action.NONE,p.next(t,true,true))
        assertEquals(GpsRecovery.Action.PROBE,p.next(481000,true,true))
        assertFalse(p.probeFinished(496000))
    }
    @Test fun onlyContinuousCallbackStartsAnotherRecoveryEpisode() {
        val p=GpsRecovery(1000);p.callback(2000)
        assertEquals(GpsRecovery.Action.PROBE,p.next(92000,true,true))
        assertTrue(p.probeFinished(107000))
        p.callback(120000)
        assertEquals(GpsRecovery.Action.NONE,p.next(210000,true,true)) // five-minute probe limit survives recovery
        assertEquals(GpsRecovery.Action.PROBE,p.next(392000,true,true))
        p.callback(393000)
        assertFalse(p.probeFinished(394000)) // late probe completion cannot restart a healthy stream
    }
    @Test fun disabledProviderAndMissingRegistrationAreBounded() {
        val p=GpsRecovery(1000)
        assertEquals(GpsRecovery.Action.NONE,p.next(999999,false,false))
        assertEquals(GpsRecovery.Action.REGISTER,p.next(2000,false,true))
        assertEquals(GpsRecovery.Action.NONE,p.next(3000,false,true))
    }
    @Test fun cachedFutureExpiredAndCancelledGenerationCannotBeFresh() {
        assertFalse(GpsRecovery.fresh(100000,99999,100001))
        assertFalse(GpsRecovery.fresh(100000,100002,100001))
        assertFalse(GpsRecovery.fresh(100000,100001,115001))
        assertTrue(GpsRecovery.fresh(100000,100001,100002))
        assertNull(GpsReceipts.age(100002,100001))
    }
}
