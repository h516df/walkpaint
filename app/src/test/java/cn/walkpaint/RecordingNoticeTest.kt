package cn.walkpaint
import org.junit.Assert.*
import org.junit.Test
class RecordingNoticeTest {
    private fun notice(code:String,age:Long?=0,saved:Int=0)=RecordingNotice.of(
        RecordingStatus(code,code,"weak"),age,8f,saved)
    @Test fun criticalTransitionsAreImmediateInsideThirtySeconds() {
        val g=RecordingNoticeGate();val first=notice("STARTING")
        assertEquals(0L,g.delay(first,1000));g.published(first,1000)
        assertEquals(0L,g.delay(notice("GPS_DISABLED"),1001))
        val healthy=notice("SAVED");g.published(healthy,2000)
        assertEquals(0L,g.delay(notice("CALLBACK_INTERRUPTED",46000),2001))
    }
    @Test fun sameStateRefreshHasAFiniteDeadlineAndIdenticalContentIsSkipped() {
        val g=RecordingNoticeGate();val first=notice("SAVED",0,2);g.published(first,1000)
        assertNull(g.delay(first,1001))
        val next=notice("WAITING_MOVEMENT",1000,3)
        assertEquals(29000L,g.delay(next,2000));assertEquals(0L,g.delay(next,31000))
    }
    @Test fun noFixAndWeakFixNeverAdvertiseAccuracyAsHealthy() {
        assertTrue(notice("STARTING",null).detail.contains("等待首个 GPS"))
        assertFalse(notice("INVALID_FIX").detail.contains("精度 8"))
        assertTrue(notice("CALLBACK_INTERRUPTED",34038000).detail.contains("567分钟前"))
        assertTrue(notice("SAVED",0,7).detail.contains("已保存 7 点"))
    }
}
