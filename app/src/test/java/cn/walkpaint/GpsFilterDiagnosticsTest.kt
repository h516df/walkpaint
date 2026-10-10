package cn.walkpaint

import org.junit.Assert.*
import org.junit.Test

class GpsFilterDiagnosticsTest {
    private val now = 10_000_000L

    @Test fun sessionPartitionMatchesFilteredCountAndNeverUsesValidMinusSaved() {
        val b = DiagnosticBuffer()
        b.record("start", "USER_OR_BOOT_START", 1)
        repeat(1088) { b.record("valid_gps", "VALID_GPS_RECEIVED", 2) }
        repeat(63) { b.record("saved", "SQLITE_SAVED", 3) }
        repeat(1000) { b.record("filtered", "STATIONARY_OR_SMALL_MOVE", 4) }
        repeat(20) { b.record("filtered", "JUMP_OR_TOO_FAST", 5) }
        repeat(3) { b.record("filtered", "MISSING_ACCURACY", 6) }
        b.record("filtered", "STALE_FIX", 7)
        b.record("filtered", "INVALID_COORDINATE", 8)
        val text = GpsFilterDiagnostics.session(b.snapshot())
        assertTrue(text.contains("静止/小位移 1000"))
        assertTrue(text.contains("JUMP_OR_TOO_FAST 20"))
        assertTrue(text.contains("精度 3"))
        assertTrue(text.contains("时间 1"))
        assertTrue(text.contains("其他 1"))
        assertTrue(text.contains("已分类 1025 / 过滤总数 1025"))
        assertEquals(1088L, b.long("session_valid_gps_count"))
        assertEquals(63L, b.long("session_saved_count"))
    }

    @Test fun upgradeDoesNotBackfillOldFiltersAndRestartResetsEveryCategory() {
        val b = DiagnosticBuffer(mapOf("session_filtered_count" to 1025L))
        assertTrue(GpsFilterDiagnostics.session(b.snapshot()).contains("不可回溯 1025"))
        b.record("filtered", "JUMP_OR_TOO_FAST", 123)
        val restored = DiagnosticBuffer(b.snapshot())
        assertTrue(GpsFilterDiagnostics.session(restored.snapshot()).contains("已分类 1 / 过滤总数 1026"))
        assertTrue(GpsFilterDiagnostics.session(restored.snapshot()).contains("不可回溯 1025"))
        restored.record("start", "STICKY_RESTART", 456)
        assertTrue(GpsFilterDiagnostics.session(restored.snapshot()).contains("已分类 0 / 过滤总数 0"))
        GpsFilterCategory.entries.forEach { assertEquals(0L, restored.long(it.sessionKey)) }
    }

    @Test fun whitelistClassifiesAllKnownReasonsWithoutKeepingUnknownPayloads() {
        val b = DiagnosticBuffer()
        val privateReason = "lat=51.75 lon=-1.25 speed=88 road=SECRET_ROAD id=SECRET_ID token=SECRET_KEY"
        assertFalse(b.record("filtered", privateReason, 1))
        assertEquals("OTHER_FILTER", b.snapshot()["filter_reason"])
        assertEquals("OTHER_FILTER", b.snapshot()["last_event"])
        assertFalse(b.snapshot().toString().contains("SECRET"))
        assertEquals(GpsFilterCategory.OTHER, GpsFilterCategory.fromReason("JUMP_OR_TOO_FAST " + privateReason))
        val expected = mapOf(
            "STATIONARY_OR_SMALL_MOVE" to GpsFilterCategory.STATIONARY,
            "JUMP_OR_TOO_FAST" to GpsFilterCategory.JUMP,
            "MISSING_ACCURACY" to GpsFilterCategory.ACCURACY,
            "UNRELIABLE_ACCURACY" to GpsFilterCategory.ACCURACY,
            "STALE_FIX" to GpsFilterCategory.TIME,
            "NON_INCREASING_TIME" to GpsFilterCategory.TIME,
            "INVALID_COORDINATE" to GpsFilterCategory.OTHER)
        expected.forEach { (code, category) ->
            assertEquals(category, GpsFilterCategory.fromReason(code))
            assertEquals(category.problemCode, ProblemCode.fromRecording("filtered", code, null))
        }
        val hostile = b.snapshot() + mapOf("session_filter_jump_count" to privateReason, "latitude" to 51.75)
        assertFalse(GpsFilterDiagnostics.session(hostile).contains("SECRET"))
        assertFalse(GpsFilterDiagnostics.session(hostile).contains("51.75"))
    }

    @Test fun windowsSeparateWholeBucketsFromBoundaryBucketsWithoutProrating() {
        val start = now - 30 * 60_000L
        val entries = listOf(
            ProblemEntry(ProblemCode.GPS_STATIONARY, start + 1, start + 100, 40),
            ProblemEntry(ProblemCode.GPS_JUMP, start - 100, start + 100, 20),
            ProblemEntry(ProblemCode.GPS_ACCURACY, start - 1000, start - 1, 3),
            ProblemEntry(ProblemCode.GPS_STALE, start, start, 2),
            ProblemEntry(ProblemCode.GPS_FILTERED, now, now, 1),
            ProblemEntry(ProblemCode.GPS_GAP, now, now, 999),
            ProblemEntry(ProblemCode.RECORDING_FAILED, now, now, 999))
        val window = GpsFilterDiagnostics.window(entries, now, 30 * 60_000L)
        assertEquals(40L, window.whole[GpsFilterCategory.STATIONARY])
        assertEquals(0L, window.whole[GpsFilterCategory.JUMP])
        assertEquals(20L, window.boundary[GpsFilterCategory.JUMP])
        assertEquals(0L, window.whole[GpsFilterCategory.ACCURACY])
        assertEquals(2L, window.whole[GpsFilterCategory.TIME])
        assertEquals(1L, window.whole[GpsFilterCategory.OTHER])
        assertEquals(43L, window.whole.values.sum())
        val twoHours = GpsFilterDiagnostics.window(entries, now, 2 * 60 * 60_000L)
        assertEquals(66L, twoHours.whole.values.sum())
        assertEquals(0L, twoHours.boundary.values.sum())
        val text = GpsFilterDiagnostics.recent(entries, false, true, now)
        assertTrue(text.contains("最近30分钟")); assertTrue(text.contains("最近2小时"))
        assertTrue(text.contains("不分摊、不计入整桶次数"))
        assertTrue(text.contains("已关闭收集")); assertTrue(text.contains("保存失败"))
        assertTrue(text.contains("不可回溯"))
    }

    @Test fun historyBucketsRoundTripAndReportUsesAllRetainedBucketsNotOnlyLatestThirty() {
        val history = ProblemHistory(now = now - 2 * 60 * 60_000L)
        repeat(40) { i -> history.record(ProblemCode.GPS_JUMP, now - 100_000 + i * 1000L, now) }
        repeat(40) { i -> history.record(ProblemCode.RECORDING_FAILED, now - 1000 + i, now) }
        val restored = ProblemHistory(history.encode(), now = now).snapshot(now)
        assertEquals(40L, GpsFilterDiagnostics.window(restored, now, 30 * 60_000L).whole[GpsFilterCategory.JUMP])
        assertEquals(2, restored.size)
    }

    @Test fun invalidFutureExpiredAndEmptyHistoryCannotPretendCompleteCoverage() {
        val entries = listOf(
            ProblemEntry(ProblemCode.GPS_JUMP, now + 1, now + 1, 1),
            ProblemEntry(ProblemCode.GPS_JUMP, now, now - 1, 1),
            ProblemEntry(ProblemCode.GPS_JUMP, now, now, -1),
            ProblemEntry(ProblemCode.GPS_JUMP, 1, 1, 1))
        assertEquals(0L, GpsFilterDiagnostics.window(entries, now, 30 * 60_000L).whole.values.sum())
        val text = GpsFilterDiagnostics.recent(emptyList(), true, false, now)
        assertTrue(text.contains("无保留过滤桶"))
        assertTrue(text.contains("不代表没有过滤"))
        assertTrue(text.contains("不能判断行车时段"))
    }
}
