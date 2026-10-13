package cn.walkpaint
import org.junit.Assert.*
import org.junit.Test

class HealthCheckLoopTest {
    @Test fun providerQueryFailureDoesNotEndFutureChecks() {
        var attempts=0; var scheduled:Runnable?=null; var failures=0
        val loop=HealthCheckLoop({true}, {
            attempts++
            if(attempts==1) throw SecurityException("provider permission changed")
        }, {failures++}, {scheduled=it})
        loop.run()
        assertEquals(1,failures)
        assertNotNull(scheduled)
        scheduled!!.run()
        assertEquals(2,attempts)
    }
    @Test fun noWorkOrRescheduleAfterStop() {
        var active=true; var checks=0; var schedules=0
        val loop=HealthCheckLoop({active},{checks++;active=false},{},{schedules++})
        loop.run();loop.run()
        assertEquals(1,checks);assertEquals(0,schedules)
    }
}
