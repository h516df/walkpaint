package cn.walkpaint
import org.junit.Assert.*
import org.junit.Test
class MapNoticeTest {
    @Test fun normalAndNoFixNeverCoverMap() {
        listOf("PAUSED","STARTING","SAVED","WAITING_MOVEMENT","INVALID_FIX").forEach {
            assertNull(MapNotice.from(RecordingStatus(it,"title","hint")))
        }
    }
    @Test fun neverObtainedFixIsNotAnInterruptedRecording() {
        assertNull(MapNotice.from(RecordingStatus("CALLBACK_INTERRUPTED","title","hint"),false))
    }
    @Test fun interruptionsHaveActionableNotice() {
        listOf("PERMISSION","GPS_DISABLED","SERVICE_STOPPED","SAVE_FAILED","REGISTER_FAILED","CALLBACK_INTERRUPTED").forEach {
            assertFalse(MapNotice.from(RecordingStatus(it,"title","hint")).isNullOrBlank())
        }
    }
    @Test fun exploredLengthUsesRoadCoverageNotGpsDistance() {
        val roads=RoadState(intervals=listOf(PaintedInterval("road",0.0,20.0),PaintedInterval("road",10.0,30.0)))
        assertEquals(30.0,roads.meters,0.001)
        assertEquals("30 米",exploredDistance(roads.meters))
    }
}
