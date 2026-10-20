package cn.walkpaint

import org.junit.Assert.*
import org.junit.Test

class NearbyCandidatesTest {
    private val center=RegionPoint(0.0,0.0)
    private fun point(id:String,meters:Double=0.0)=ExplorationLandmark(id,"公开测试地点",meters/111195.0,0.0)
    @Test fun discoveryNeverCreatesVisitsAndDoesNotMutateInput() {
        val saved=point("one").copy(visitedAt=1234)
        val result=NearbyCandidates.select(center,listOf(saved to "公园"))
        assertNull(result.single().point.visitedAt)
        assertEquals(1234L,saved.visitedAt)
    }
    @Test fun invalidUnnamedDeletedAndDistantEntriesAreExcluded() {
        val data=listOf(point("valid"),point("far",3000.0),point("blank").copy(name=""),
            point("invalid").copy(lat=Double.NaN),point("deleted").copy(deleted=true))
        assertEquals(listOf("valid"),NearbyCandidates.select(center,data.map {it to ""}).map {it.point.id})
    }
    @Test fun sortedByActualDistanceDeduplicatedAndBounded() {
        val data=(30 downTo 1).map {point("$it",it*20.0) to "景点"}
        val result=NearbyCandidates.select(center,data+data)
        assertEquals(20,result.size)
        assertEquals("1",result.first().point.id)
        assertEquals("20",result.last().point.id)
        assertTrue(result.zipWithNext().all {(a,b)->a.meters<=b.meters})
    }
    @Test fun emptyServiceResultsRemainEmpty() {
        assertTrue(NearbyCandidates.select(center,emptyList()).isEmpty())
    }
    @Test fun authorizationQuotaAndNetworkFailuresAreNotEmptyStates() {
        assertTrue(NearbyCandidates.failure(1012).contains("权限"))
        assertTrue(NearbyCandidates.failure(1004).contains("访问量"))
        assertTrue(NearbyCandidates.failure(null).contains("重试"))
    }
}
