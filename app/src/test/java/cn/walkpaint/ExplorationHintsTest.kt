package cn.walkpaint

import org.junit.Assert.*
import org.junit.Test

class ExplorationHintsTest {
    private fun hint(id:String,x:Float,y:Float)=ExplorationHint(ExplorationLandmark(id,"测试景点",0.0,0.0),x,y,false)
    @Test fun zoomLimitsAndViewportEdgesPreventClutter() {
        val points=(0..15).map {hint("$it",50f+(it%4)*80,60f+(it/4)*80)}
        assertEquals(3,ExplorationHints.select(points,500f,500f,1f,12f).size)
        assertEquals(5,ExplorationHints.select(points,500f,500f,1f,14f).size)
        assertEquals(8,ExplorationHints.select(points,500f,500f,1f,17f).size)
        assertTrue(ExplorationHints.select(listOf(hint("left",0f,80f),hint("bottom",80f,490f),hint("invalid",Float.NaN,80f)),500f,500f,1f,17f).isEmpty())
    }
    @Test fun nearbyPixelsCollapseToOneAndSelectionIsStable() {
        val a=hint("a",100f,100f);val b=hint("b",150f,100f);val c=hint("c",180f,100f)
        assertEquals(listOf(a,c),ExplorationHints.select(listOf(a,b,c,a),500f,500f,1f,17f))
    }
    @Test fun smallVisualHas48dpTouchTargetAndNeverMutatesVisit() {
        val point=hint("candidate",100f,100f)
        assertEquals(point.point,ExplorationHints.hit(listOf(point),124f,100f,1f))
        assertNull(ExplorationHints.hit(listOf(point),125f,100f,1f))
        assertNull(ExplorationHints.hit(emptyList(),100f,100f,1f))
        assertNull(point.point.visitedAt)
    }
    @Test fun densityScalesClusteringAndTouchTogether() {
        val a=hint("a",200f,200f);val b=hint("b",300f,200f)
        assertEquals(1,ExplorationHints.select(listOf(a,b),1000f,1000f,2f,17f).size)
        assertNotNull(ExplorationHints.hit(listOf(a),248f,200f,2f))
        assertNull(ExplorationHints.hit(listOf(a),249f,200f,2f))
    }
    @Test fun reliableLocationJitterDoesNotChangeQueryAnchor() {
        assertTrue(NearbyRefreshPolicy.near(RegionPoint(0.0,0.0),RegionPoint(.001,0.0)))
        assertFalse(NearbyRefreshPolicy.near(RegionPoint(0.0,0.0),RegionPoint(.003,0.0)))
    }
}
