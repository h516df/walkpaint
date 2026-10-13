package cn.walkpaint

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosisTest {

    /** A session that is doing everything right. */
    private fun healthy() = DiagnosisFacts(
        enabled = true, running = true, fineLocation = true, backgroundLocation = true,
        phoneLocationOn = true, batteryWhitelisted = true,
        callbackAgeMs = 3_000L, savedAgeMs = 20_000L, savedCount = 120,
        filteredCount = 40, stationaryFiltered = 30, jumpFiltered = 5, accuracyFiltered = 5,
        longestGapMs = 4_000L, serviceStartCount = 1, databaseError = null, dozeIdle = false,
    )

    @Test fun aHealthySessionSaysThereIsNothingToDo() {
        val f = healthy()
        assertEquals(Diagnosis.SCREEN_OK, Diagnosis.syndromeId(f))
        assertTrue(Diagnosis.verdict(f).contains("正常"))
        assertEquals(Health.OK, Diagnosis.items(f).first { it.title == "定位信号" }.level)
    }

    @Test fun aPausedRecorderIsReportedAsAChoiceNotAFault() {
        val f = healthy().copy(enabled = false)
        assertEquals(Diagnosis.SCREEN_PAUSED, Diagnosis.syndromeId(f))
        assertTrue(Diagnosis.verdict(f).contains("关"))
    }

    @Test fun missingPermissionOutranksEveryOtherSymptom() {
        val f = healthy().copy(fineLocation = false, running = false, databaseError = "写不进去")
        assertEquals(Diagnosis.SCREEN_PERMISSION, Diagnosis.syndromeId(f))
    }

    @Test fun aDisabledPhoneLocationIsNamedAsSuch() {
        val f = healthy().copy(phoneLocationOn = false)
        assertEquals(Diagnosis.SCREEN_PHONE_LOCATION_OFF, Diagnosis.syndromeId(f))
    }

    @Test fun aStoppedBackgroundServiceIsNamedAndOffersTheWhitelist() {
        val f = healthy().copy(running = false, savedAgeMs = null)
        assertEquals(Diagnosis.SCREEN_BACKGROUND_STOPPED, Diagnosis.syndromeId(f))
        assertTrue(Diagnosis.verdict(f).contains("白名单"))
    }

    @Test fun noPositioningArrivingIsNamedWhenTheCallbackIsOld() {
        val f = healthy().copy(callbackAgeMs = 5 * 60_000L)
        assertEquals(Diagnosis.SCREEN_NO_SIGNAL, Diagnosis.syndromeId(f))
        val signal = Diagnosis.items(f).first { it.title == "定位信号" }
        assertEquals(Health.BAD, signal.level)
        assertNotNull(signal.action)
    }

    @Test fun aWalkRejectedAsStandingStillIsNamedAsAProblemToFix() {
        // Positioning is arriving, the receiver reported travel, and still nothing was stored.
        val f = healthy().copy(
            savedAgeMs = 10 * 60_000L, savedCount = 1,
            movingThisSession = 20, savedThisSession = 0,
            filteredCount = 500, stationaryFiltered = 500, jumpFiltered = 0, accuracyFiltered = 0,
        )
        assertEquals(Diagnosis.SCREEN_WALKING_FILTERED, Diagnosis.syndromeId(f))
        assertTrue(Diagnosis.verdict(f).contains("需要修"))
    }

    @Test fun aPhoneLeftOnADeskIsNeverCalledABrokenRecorder() {
        // The exact counters a stationary phone produces: no saves for ten minutes and hundreds of
        // "not moving" drops. Without travel evidence this must NOT be reported as a fault, which
        // is what the on-device run of the first version of this diagnosis got wrong.
        val f = healthy().copy(
            savedAgeMs = 10 * 60_000L, savedCount = 84,
            movingThisSession = 0, savedThisSession = 0,
            filteredCount = 984, stationaryFiltered = 11, jumpFiltered = 0, accuracyFiltered = 0,
        )
        assertEquals(Diagnosis.SCREEN_UNCLEAR, Diagnosis.syndromeId(f))
        val verdict = Diagnosis.verdict(f)
        assertFalse(verdict.contains("需要修"))
        assertTrue(verdict.contains("不动"))
    }

    @Test fun poorAccuracyIsNamedSeparatelyFromNotMoving() {
        val f = healthy().copy(
            savedAgeMs = 10 * 60_000L,
            filteredCount = 410, stationaryFiltered = 10, jumpFiltered = 0, accuracyFiltered = 400,
        )
        assertEquals(Diagnosis.SCREEN_WEAK_SIGNAL, Diagnosis.syndromeId(f))
        assertTrue(Diagnosis.verdict(f).contains("精度"))
    }

    @Test fun aStorageFailureTakesPrecedenceOverTiming() {
        val f = healthy().copy(databaseError = "足迹写入失败", savedAgeMs = 10 * 60_000L)
        assertEquals(Diagnosis.SCREEN_STORAGE, Diagnosis.syndromeId(f))
        assertEquals(Health.BAD, Diagnosis.items(f).first { it.title == "写入足迹" }.level)
    }

    @Test fun aLongGapIsReportedAsAGapNotAsLostData() {
        val f = healthy().copy(longestGapMs = 60 * 60_000L)
        assertEquals(Diagnosis.SCREEN_LONG_GAP, Diagnosis.syndromeId(f))
        val verdict = Diagnosis.verdict(f)
        assertTrue(verdict.contains("断档"))
        assertTrue(verdict.contains("不是数据被删"))
    }

    @Test fun theReportOpensWithTheConclusionAndTheSyndromeId() {
        val report = Diagnosis.report(healthy())
        assertTrue(report.startsWith("—— 结论 ——"))
        assertTrue(report.contains(Diagnosis.SCREEN_OK))
    }

    @Test fun everyProblemLineOffersSomethingToDo() {
        val broken = healthy().copy(running = false, savedAgeMs = null, fineLocation = true)
        val problems = Diagnosis.items(broken).filter { it.level != Health.OK }
        assertTrue(problems.isNotEmpty())
        assertTrue("a flagged line must say what to do", problems.all { it.action != null })
    }

    @Test fun aQuietSessionIsNotMistakenForABrokenOne() {
        // Nothing filtered and nothing saved yet, but positioning just arrived: that is a normal
        // first thirty seconds, not a fault.
        val f = healthy().copy(savedAgeMs = null, savedCount = 0, filteredCount = 0,
            stationaryFiltered = 0, jumpFiltered = 0, accuracyFiltered = 0)
        assertEquals(Diagnosis.SCREEN_UNCLEAR, Diagnosis.syndromeId(f))
        assertFalse(Diagnosis.verdict(f).contains("需要修"))
    }
}
