package cn.walkpaint

import org.junit.Assert.*
import org.junit.Test

class DiagnosisPresentationTest {
    private fun facts()=DiagnosisFacts(enabled=true,running=true,fineLocation=true,backgroundLocation=true,
        phoneLocationOn=true,batteryWhitelisted=true,callbackAgeMs=null,savedAgeMs=300000L,savedCount=8970,
        filteredCount=0,stationaryFiltered=0,jumpFiltered=0,accuracyFiltered=0,longestGapMs=0,
        serviceStartCount=41,databaseError=null,dozeIdle=false)
    @Test fun pausedChoiceDoesNotAdviseRestartOrGoingOutdoors() {
        val p=DiagnosisPresentation.of(facts().copy(enabled=false,running=false))
        assertEquals("自动记录已暂停",p.title)
        assertEquals(listOf("历史足迹"),p.rows.map { it.title })
        assertFalse(p.detail.contains("室外"));assertFalse(p.detail.contains("后台已停止"))
        assertTrue(p.rows.single().detail.contains("8970"))
    }
    @Test fun waitingForGpsDoesNotClaimSubscriptionMeansRecovered() {
        val p=DiagnosisPresentation.of(facts())
        assertEquals("正在等待 GPS 位置",p.title)
        assertTrue(p.detail.contains("不能确定"))
        assertTrue(p.detail.contains("网络蓝点"))
        assertTrue(p.rows.any { it.title=="定位信号" && it.level==Health.BAD })
    }
    @Test fun userViewKeepsStorageFaultAndFullReportIntact() {
        val f=facts().copy(databaseError="磁盘满")
        assertEquals("足迹暂时无法保存",DiagnosisPresentation.of(f).title)
        assertTrue(Diagnosis.report(f).contains(Diagnosis.SCREEN_STORAGE))
    }
}
