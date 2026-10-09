package cn.walkpaint
import org.junit.Assert.*
import org.junit.Test
class HeadingFilterTest {
    @Test fun shortestTurnAcrossNorthNeverSpinsLongWay() {
        assertEquals(20f,HeadingFilter.delta(350f,10f),0f)
        assertEquals(-20f,HeadingFilter.delta(10f,350f),0f)
        val f=HeadingFilter();assertEquals(350f,f.sample(350f,1000)!!,0f)
        val next=f.sample(10f,1500)!!
        assertTrue(next<12f || next>350f)
    }
    @Test fun noisySamplesAndOutputRateAreBounded() {
        val f=HeadingFilter();assertEquals(90f,f.sample(90f,1000)!!,0f)
        assertNull(f.sample(180f,1050));assertNull(f.sample(180f,1100))
        val next=f.sample(180f,1250)!!;assertTrue(next in 90f..135f)
        assertNull(f.sample(next+.1f,1500))
    }
    @Test fun invalidAndUnreliableSamplesResetRatherThanInventNorth() {
        val f=HeadingFilter();f.sample(20f,1000)
        assertNull(f.sample(Float.NaN,2000));assertEquals(270f,f.sample(-90f,3000)!!,0f)
        assertNull(f.sample(50f,4000,false));assertEquals(50f,f.sample(50f,5000)!!,0f)
    }
}
