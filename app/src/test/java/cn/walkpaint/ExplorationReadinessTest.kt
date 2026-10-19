package cn.walkpaint

import org.junit.Assert.*
import org.junit.Test

class ExplorationReadinessTest {
    @Test fun firstRunExplainsVeilWithoutClaimingTilesLoaded() {
        assertTrue(ExplorationReadiness.message(false,false,false).contains("初始化"))
        assertTrue(ExplorationReadiness.message(true,false,true).contains("开始记录"))
        assertTrue(ExplorationReadiness.message(true,true,false).contains("等待可靠定位"))
        assertTrue(ExplorationReadiness.message(true,true,true).contains("不代表底图加载成功"))
    }
    @Test fun missingCompassStartsNorthAndTransientNoiseKeepsLastDirection() {
        val heading=HeadingContinuity()
        assertEquals(0f,heading.resolve(null),0f)
        assertEquals(359f,heading.resolve(359f),0f)
        repeat(10){assertEquals(359f,heading.resolve(null),0f)}
        assertEquals(359f,heading.resolve(Float.NaN),0f)
        assertEquals(1f,heading.resolve(361f),0f)
    }
    @Test fun locateZoomSurvivesNotReadyUntilAcceptedOrManualGesture() {
        val request=LocateZoomRequest();request.request()
        request.delivered(false);assertTrue(request.pending)
        request.delivered(true);assertFalse(request.pending)
        request.request();request.cancel();assertFalse(request.pending)
    }
    @Test fun locateEscapesHistoryAndBrowsingButBackgroundDoesNotFollow() {
        val state=MapFollowState();state.visible=true
        state.history();state.locate();assertTrue(state.following)
        state.gesture();assertFalse(state.following)
        state.locate();assertTrue(state.following)
        state.visible=false;assertFalse(state.following)
        state.visible=true;assertTrue(state.following)
    }
}
