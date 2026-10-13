package cn.walkpaint
import org.junit.Assert.*
import org.junit.Test

class StepZoomTargetTest {
    @Test fun unfinishedCameraFramesDoNotLoseClicksAndReverseUsesDestination() {
        val steps=StepZoomTarget()
        assertEquals(16f,steps.request(17f,-1,3f,20f),0f)
        steps.settled(16.8f) // cancel callback from the interrupted first animation
        assertEquals(15f,steps.request(16.8f,-1,3f,20f),0f)
        assertEquals(14f,steps.request(16.5f,-1,3f,20f),0f)
        assertEquals(13f,steps.request(16.2f,-1,3f,20f),0f)
        assertEquals(14f,steps.request(16f,1,3f,20f),0f)
        assertEquals(15f,steps.request(15.8f,1,3f,20f),0f)
        assertEquals(16f,steps.request(15.8f,1,3f,20f),0f)
        assertEquals(17f,steps.request(15.8f,1,3f,20f),0f)
        steps.settled(17f)
        assertNull(steps.target)
    }
    @Test fun boundariesDoNotStoreInvisibleExtraStepsAndGesturesStartFresh() {
        val steps=StepZoomTarget()
        assertEquals(20f,steps.request(20f,1,3f,20f),0f)
        assertEquals(20f,steps.request(19.8f,1,3f,20f),0f)
        assertEquals(19f,steps.request(19.8f,-1,3f,20f),0f)
        steps.reset()
        assertEquals(8.5f,steps.request(9.5f,-1,3f,20f),0f)
        steps.reset()
        assertEquals(3f,steps.request(3.2f,-1,3f,20f),0f)
        assertEquals(4f,steps.request(3.1f,1,3f,20f),0f)
    }
}
