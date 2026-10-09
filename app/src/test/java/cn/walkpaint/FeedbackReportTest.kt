package cn.walkpaint
import org.junit.Assert.*
import org.junit.Test

class FeedbackReportTest {
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
}
