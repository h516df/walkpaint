package cn.walkpaint
import org.junit.Assert.*
import org.junit.Test
class GpsSurveyTest {
    @Test fun visibleSatellitesAreNotFixesAndReportContainsOnlyReducedEvidence() {
        val s=GpsSurvey(1000);s.satellites(2000,52,0,listOf(10.0,30.0),12)
        val report=s.report(63000)
        assertTrue(report.contains("参与定位为 0"));assertTrue(report.contains("平均 20.0"))
        assertFalse(s.active(61000));s.fix(62000,true);assertEquals(0,s.continuous)
    }
    @Test fun probeCannotBePresentedAsContinuousRecovery() {
        val s=GpsSurvey(1000);s.fix(2000,false)
        assertTrue(s.report(3000).contains("不能宣称连续记录恢复"))
        s.fix(4000,true);s.stop(5000,"退出前台，结束短测")
        s.fix(6000,true);assertEquals(1,s.continuous)
        assertTrue(s.report(6000).contains("诊断结束"))
    }
}
