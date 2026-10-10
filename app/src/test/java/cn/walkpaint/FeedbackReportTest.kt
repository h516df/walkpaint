package cn.walkpaint
import org.junit.Assert.*
import org.junit.Test

class FeedbackReportTest {
    @Test fun directTrailsWithNoRoadPackageDoNotReportPreparationAsTheProblem() {
        val text=FeedbackReport.build(facts().copy(directTrail=true,trailLines=2,trailMetres=500),"道路涂色","",180_000)
        assertTrue(text.contains("已保存的轨迹直接在地图上涂色"))
        assertTrue(text.contains("连续轨迹 2 段")); assertTrue(text.contains("累计记录 500 米"))
        assertFalse(text.contains("尚未准备")); assertFalse(text.contains("累计涂色为空"))
    }
    private fun facts()=FeedbackFacts("0.13",14,36,"Google","test",false,false,false,false,false,2,6,0,1,0,0,1,false,
        mapOf("session_gps_count" to 5L,"latitude" to 51.75,"longitude" to -1.25,"road_name" to "SECRET_ROAD", "failure_reason" to "https://private/path?token=SECRET_KEY", "saved_at" to 120_000L,"logcat" to "SECRET_LOG"),emptyList(),true)
    @Test fun reportProjectsNumericWhitelistedFieldsOnly() {
        val text=FeedbackReport.build(facts(),"定位","无法连续记录",180_000)
        listOf("51.75","-1.25","SECRET_ROAD","SECRET_KEY","SECRET_LOG","https://private").forEach { assertFalse(text.contains(it)) }
        assertTrue(text.contains("回调 5"));assertTrue(text.contains("1 分钟前"));assertTrue(text.contains("无法连续记录"))
    }
    @Test fun authoredDescriptionIsVisibleBoundedAndUnknownCategoryIgnored() {
        val text=FeedbackReport.build(facts(),"token=SECRET","我填写了坐标，请自行检查"+"x".repeat(1500),180_000)
        assertTrue(text.contains("我填写了坐标，请自行检查"));assertFalse(text.contains("token=SECRET"))
        assertFalse(text.contains("x".repeat(1001)));assertTrue(text.contains("问题类型：其他"))
    }
    @Test fun confirmedButUnpaintedIsExplicitAndNotSilentlyMarkedHealthy() {
        val text=FeedbackReport.build(facts().copy(currentConfirmable=2),"道路涂色","",180_000)
        assertTrue(text.contains("当前复算可确认，但累计涂色为空"))
    }
    @Test fun previewIncludesIndependentSessionAndBothWindowsEvenBeyondThirtyVisibleClues() {
        val now = 10_000_000L
        val b = DiagnosticBuffer()
        b.record("start", "USER_OR_BOOT_START", now - 1000)
        b.record("filtered", "JUMP_OR_TOO_FAST", now)
        val recent = List(40) { ProblemEntry(ProblemCode.RECORDING_FAILED, now, now, 1) } +
            ProblemEntry(ProblemCode.GPS_JUMP, now - 100, now - 1, 42)
        val text = FeedbackReport.build(facts().copy(diagnostics=b.snapshot(),history=recent), "道路涂色", "", now)
        assertTrue(text.contains("本次服务GPS过滤分类"))
        assertTrue(text.contains("已分类 1 / 过滤总数 1"))
        assertTrue(text.contains("最近30分钟")); assertTrue(text.contains("最近2小时"))
        assertEquals(2, Regex("JUMP_OR_TOO_FAST 42").findAll(text).count())
        assertFalse(text.contains("位置跳变未采用")) // This clue is outside the separate 30-row presentation cap.
        assertTrue(text.contains("不能判断行车时段"))
    }
    @Test fun automaticallyCollectedFilterPayloadsCannotEnterPreviewOrPersistedHistory() {
        val payload = "51.75 -1.25 speed=88 SECRET_ROAD SECRET_ID SECRET_KEY raw-track"
        val b = DiagnosticBuffer()
        b.record("filtered", payload, 1)
        val h = ProblemHistory(now=1)
        h.record(ProblemCode.fromRecording("filtered", payload, null)!!, 1)
        val injected = b.snapshot() + mapOf("session_filter_time_count" to payload, "road_id" to "SECRET_ID")
        val text = FeedbackReport.build(facts().copy(diagnostics=injected,history=h.snapshot(1)), "定位", "", 1)
        listOf("51.75", "-1.25", "speed=88", "SECRET_ROAD", "SECRET_ID", "SECRET_KEY", "raw-track").forEach {
            assertFalse(text.contains(it)); assertFalse(h.encode().contains(it)); assertFalse(b.snapshot().toString().contains(it))
        }
        assertTrue(text.contains("其他 1"))
    }
    @Test fun oldSessionAndDisabledEmptyHistoryStayExplicitlyUnrecoverable() {
        val text = FeedbackReport.build(facts().copy(
            diagnostics=mapOf("session_filtered_count" to 1025L),collected=false,historySaveFailed=true), "定位", "", 180_000)
        assertTrue(text.contains("不可回溯 1025"))
        assertTrue(text.contains("已关闭收集")); assertTrue(text.contains("保存失败"))
        assertTrue(text.contains("无保留过滤桶，不代表没有过滤"))
    }
}
