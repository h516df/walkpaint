package cn.walkpaint
import org.junit.Assert.*
import org.junit.Test
class ExplorationFogTest {
    private fun p(lat:Double,t:Long,segment:Int=0)=TrackPoint(lat,2.35,t,5f,segment)
    private fun state(points:List<TrackPoint>,demo:Boolean=false)=TrackState(listOf(Track("public",1,demo,points)))
    @Test fun emptyAndDemoRevealNothing() {
        assertTrue(ExplorationFog.paths(TrackState()).isEmpty())
        assertTrue(ExplorationFog.paths(state(listOf(p(48.85,1000)),true)).isEmpty())
    }
    @Test fun timeGapNeverClearsTheLandBetweenEndpoints() {
        val paths=ExplorationFog.paths(state(listOf(p(48.85,1000),p(48.851,61000))))
        assertEquals(2,paths.size);assertTrue(paths.all { it.size==1 })
    }
    @Test fun differentSegmentsNeverConnect() {
        assertEquals(2,ExplorationFog.paths(state(listOf(p(48.85,1000),p(48.8501,11000,1)))).size)
    }
    @Test fun continuousWalkingAndDrivingHaveConnectedRevealPaths() {
        for (step in listOf(0.0001,0.002)) {
            assertEquals(1,ExplorationFog.paths(state(listOf(p(48.85,1000),p(48.85+step,11000)))).size)
        }
    }
    @Test fun renderingDoesNotMutateStoredPoints() {
        val points=listOf(p(48.85,1000),p(48.8501,11000));val original=state(points)
        ExplorationFog.paths(original)
        assertEquals(points,original.tracks.single().points)
    }
}
