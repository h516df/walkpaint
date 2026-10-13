package cn.walkpaint
import org.junit.Assert.*
import org.junit.Test

class MapHistoryTest {
    @Test fun selectedDayKeepsOnlyItsGroundAndLatestSavedAnchor() {
        val noon=java.time.LocalDate.now().atTime(12,0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val old=TrackPoint(39.9,116.3,noon-86_400_000,5f)
        val current=old.copy(lat=39.91,time=noon)
        val state=TrackState(listOf(Track("cross-day",old.time,false,listOf(old,current))))
        val selected=DailyFootprints.select(state,DailyFootprints.key(old.time))
        assertEquals(listOf(old),selected.realTracks.single().points)
        assertEquals(old,MapHistory.anchor(selected,DailyFootprints.key(old.time)))
        assertNull(MapHistory.anchor(state,null))
        assertNull(MapHistory.anchor(TrackState(),"missing"))
    }
}
