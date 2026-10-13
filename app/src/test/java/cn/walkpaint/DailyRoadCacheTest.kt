package cn.walkpaint
import org.junit.Assert.*
import org.junit.Test
class DailyRoadCacheTest {
    private fun state(extra:Boolean=false)=TrackState(listOf(
        Track("a",1000,false,listOf(TrackPoint(51.75,-1.25,1000,5f))),
        Track("b",172801000,false,listOf(TrackPoint(51.75,-1.25,172801000,5f))+
            if(extra) listOf(TrackPoint(51.75,-1.2498,172811000,5f)) else emptyList())))
    private val geometry=listOf(RoadSegment("test",listOf(TrackPoint(51.75,-1.25,0,5f),TrackPoint(51.75,-1.249,0,5f))))
    @Test fun onlyChangedDateIsRecomputed() {
        val cache=DailyRoadCache();val roads=RoadState(geometry);val calls=mutableListOf<String>()
        cache.refresh(state(),roads) { calls+=it;RoadState() };assertEquals(2,calls.size)
        calls.clear();cache.refresh(state(),roads) { calls+=it;RoadState() };assertTrue(calls.isEmpty())
        cache.refresh(state(true),roads) { calls+=it;RoadState() };assertEquals(listOf(DailyFootprints.key(172801000)),calls)
    }
    @Test fun newCatalogRebuildsOldDays() {
        val cache=DailyRoadCache();var reads=0
        cache.refresh(state(),RoadState(geometry)) { reads++;RoadState() }
        cache.refresh(state(),RoadState(geometry.toMutableList())) { reads++;RoadState() }
        assertEquals(4,reads)
    }
    @Test fun selectedOlderDayIsAvailableBeyondRecentWindow() {
        val trips=(0..35).map { i -> val time=1000L+i*86400000L;Track("t$i",time,false,listOf(TrackPoint(51.75,-1.25,time,5f))) }
        val data=TrackState(trips);val cache=DailyRoadCache();val old=DailyFootprints.key(1000)
        val result=cache.refresh(data,RoadState(geometry),old) { RoadState() }
        assertEquals(32,result.size);assertTrue(result.containsKey(old));assertEquals(36,data.realPointCount)
    }
}
