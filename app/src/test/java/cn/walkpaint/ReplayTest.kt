package cn.walkpaint
import org.junit.Assert.*
import org.junit.Test
class ReplayTest {
    private fun trip(vararg times:Long)=times.map { TrackPoint(31.0,121.0,it,5f,0) }
    private fun times(paths:List<List<TrackPoint>>)=paths.map { path -> path.map { it.time } }
    private val twoTrips=listOf(trip(0,1,2),trip(3,4,5))

    @Test fun theLineExistsFromTheFirstFrameAndGrowsWithTheProgress() {
        // A single point cannot be drawn, so the very first frame already has two of them.
        assertEquals(listOf(listOf(0L,1L)),times(replaySlices(twoTrips,0f)))
        assertEquals(listOf(listOf(0L,1L)),times(replaySlices(twoTrips,0.1f)))
        assertEquals(listOf(listOf(0L,1L,2L)),times(replaySlices(twoTrips,0.5f)))
        assertEquals(listOf(listOf(0L,1L,2L),listOf(3L)),times(replaySlices(twoTrips,0.6f)))
        assertEquals(listOf(listOf(0L,1L,2L),listOf(3L,4L,5L)),times(replaySlices(twoTrips,1f)))
    }

    @Test fun eachFrameAddsPointsWithoutGoingBackwardsOrReorderingThem() {
        var previous=0
        var progress=0f
        while (progress<=1f) {
            val drawn=replaySlices(twoTrips,progress)
            val points=drawn.sumOf { it.size }
            assertTrue("progress $progress drew $points after $previous",points>=previous)
            assertTrue("progress $progress drew $points of 6",points<=6)
            // Trips are handed over in time order and every frame is a prefix, so a frame can only
            // ever add points at the end.
            assertEquals("progress $progress changed the point order",
                drawn.flatten().map { it.time },drawn.flatten().map { it.time }.sorted())
            previous=points
            progress+=0.05f
        }
        assertEquals(6,previous)
    }

    @Test fun progressOutsideTheRangeAndDaysWithoutALineDrawWhatTheBarShows() {
        assertEquals(times(replaySlices(twoTrips,1f)),times(replaySlices(twoTrips,4f)))
        assertEquals(times(replaySlices(twoTrips,0f)),times(replaySlices(twoTrips,-3f)))
        assertTrue(replaySlices(emptyList(),0.5f).isEmpty())
        assertTrue(replaySlices(listOf(trip(0)),0.5f).isEmpty())
        assertTrue(replaySlices(listOf(emptyList()),1f).isEmpty())
    }

    @Test fun aLongTripIsCutExactlyWhereTheProgressAsksFor() {
        val long=listOf(trip(0,1,2,3,4,5,6,7,8,9))
        assertEquals(listOf(2),replaySlices(long,0.2f).map { it.size })
        assertEquals(listOf(5),replaySlices(long,0.5f).map { it.size })
        assertEquals(listOf(2),replaySlices(long,0f).map { it.size })
        assertEquals(listOf(10),replaySlices(long,1f).map { it.size })
    }
}
