package cn.walkpaint
import org.junit.Assert.*
import org.junit.Test
class MapFollowStateTest {
    @Test fun defaultFollowGesturePauseLocateRestoreAndHistoryLock() {
        val f=MapFollowState();assertFalse(f.following);f.visible=true;assertTrue(f.following)
        f.gesture();assertFalse(f.following);assertEquals(MapFollowState.Mode.BROWSING,f.mode)
        f.locate();assertTrue(f.following);f.history();f.gesture();assertEquals(MapFollowState.Mode.HISTORY,f.mode)
        f.visible=false;f.visible=true;assertFalse(f.following);f.locate();assertTrue(f.following)
    }
    @Test fun cameraRejectsStaleAndPoorFixesEvenIfMarkerCanUseThem() {
        val f=MapFollowState()
        assertTrue(f.accepts(MapFixQuality("gps",1000,5f)))
        assertFalse(f.accepts(MapFixQuality("gps",45001,5f)))
        assertFalse(f.accepts(MapFixQuality("gps",1000,80f)))
        assertTrue(f.accepts(MapFixQuality("network",1000,50f)))
        assertFalse(f.accepts(MapFixQuality("network",30001,50f)))
        assertFalse(f.accepts(MapFixQuality("network",1000,150f)))
        assertFalse(f.accepts(MapFixQuality("gps",-1,5f)))
    }
    @Test fun cameraRejectsLargeJumpButAllowsNormalMoveAndLaterReturn() {
        val a=TrackPoint(51.75,-1.25,1000,5f)
        assertTrue(CameraFixPolicy.plausible(a,a.copy(lon=-1.2498,time=3000)))
        assertFalse(CameraFixPolicy.plausible(a,a.copy(lon=-1.2,time=3000)))
        assertFalse(CameraFixPolicy.plausible(a,a.copy(time=0)))
        assertTrue(CameraFixPolicy.plausible(null,a))
    }
}
