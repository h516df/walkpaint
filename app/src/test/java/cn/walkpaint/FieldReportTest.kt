package cn.walkpaint

import org.junit.Assert.assertTrue
import org.junit.Test

class FieldReportTest {

    private fun facts(now: Long) = FieldFacts(
        now = now,
        version = "0.39（code41）",
        device = "vivo test",
        android = "36",
        engine = "高德联网地图",
        permissions = "精确定位=true 后台定位=true",
        recording = "本次服务：GPS回调 10 / 已入库 6 / 过滤 4",
        camera = "缩放=17.00 中心=31.23000,121.47000 跟随=true",
        veil = "雾帧=512x512 覆盖纬度0.0053° 经度0.0044°",
        storage = "数据库点数=5023 行程=7 记录里程=1310米",
        diagnosis = "一切正常，不用管它。",
    )

    @Test fun theExportCarriesTheRawFixesRatherThanASummaryOfThem() {
        FieldRecorder.clear()
        FieldRecorder.fix(1_000L, 31.2, 121.4, 8f, "gps")
        FieldRecorder.stored()
        FieldRecorder.fix(3_000L, 31.2001, 121.4001, 42f, "gps")
        FieldRecorder.reject(3_000L, "UNRELIABLE_ACCURACY", 42f)
        val text = FieldReport.build(facts(5_000L), FieldRecorder.snapshot(5_000L))
        assertTrue("the raw coordinate is missing", text.contains("31.2"))
        assertTrue("a stored fix is not marked", text.contains("已入库"))
        assertTrue("a fix that never reached the database is not marked", text.contains("未入库"))
        assertTrue("the drop reason is missing", text.contains("UNRELIABLE_ACCURACY"))
        assertTrue(text.contains("高德联网地图"))
        assertTrue(text.contains("含你的行走坐标"))
    }

    @Test fun anEmptyWindowSaysSoInsteadOfLookingBroken() {
        FieldRecorder.clear()
        val text = FieldReport.build(facts(9_000L), FieldRecorder.snapshot(9_000L))
        assertTrue(text.contains("没有收到任何定位回调"))
    }

    @Test fun whatHasAgedOutOfTheWindowIsReported() {
        FieldRecorder.clear()
        FieldRecorder.fix(1_000L, 31.0, 121.0, 8f, "gps")
        val now = 1_000L + FieldRecorder.WINDOW_MS + 1
        FieldRecorder.fix(now, 31.1, 121.1, 8f, "gps")
        val text = FieldReport.build(facts(now), FieldRecorder.snapshot(now))
        assertTrue("aged-out fixes must not be listed", !text.contains("31.0,"))
        assertTrue("the export must say the window is not the whole story", text.contains("滚出窗口"))
    }
}
