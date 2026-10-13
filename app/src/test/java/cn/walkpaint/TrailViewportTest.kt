package cn.walkpaint

import org.junit.Assert.*
import org.junit.Test

class TrailViewportTest {
    private val frame=PaintFrame(31.0,121.0,31.0004,121.0007,400,300)
    private fun p(lat:Double,lon:Double)=TrackPoint(lat,lon,1000,5f)

    @Test fun leavingTheViewAndReturningDoesNotCreateAnUnwalkedShortcut() {
        val recorded=listOf(p(31.0001,121.0001),p(31.0003,121.0001),p(31.0007,121.0001),
            p(31.0007,121.0006),p(31.0003,121.0006),p(31.0001,121.0006))
        val original=recorded.toList()
        val visible=TrailShape.network(listOf(recorded),frame)
        assertEquals(2,visible.size)
        // Only the two real vertical sides cross this viewport. Their distant connecting leg is
        // above it; connecting the two inside sides invents a walk through the unexplored middle.
        visible.forEach { line ->assertTrue(line.size>=2)
            line.forEach { assertEquals(line.first().lon,it.lon,1e-9) }
        }
        assertEquals(original,recorded)
    }

    @Test fun aRealSegmentCrossingTheViewIsKeptWhenBothEndpointsAreOutside() {
        val recorded=listOf(p(31.0002,120.9999),p(31.0002,121.0008))
        val visible=TrailShape.network(listOf(recorded),frame)
        assertEquals(1,visible.size)
        assertEquals(2,visible.single().size)
        assertTrue(visible.single().first().lon>recorded.first().lon)
        assertTrue(visible.single().last().lon<recorded.last().lon)
        visible.single().forEach { assertEquals(31.0002,it.lat,1e-9) }
    }

    @Test fun fullyInvisibleAndEmptyPathsStayAbsentAndInsidePointsRemainUnchanged() {
        assertTrue(TrailShape.network(emptyList(),frame).isEmpty())
        assertTrue(TrailShape.network(listOf(listOf(p(31.001,121.0001),p(31.0012,121.0005))),frame).isEmpty())
        val inside=listOf(p(31.0001,121.0001),p(31.0003,121.0001))
        assertEquals(listOf(inside),TrailShape.network(listOf(inside),frame))
    }
}
