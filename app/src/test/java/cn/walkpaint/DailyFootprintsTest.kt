package cn.walkpaint
import org.junit.Assert.*
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
class DailyFootprintsTest {
    @Test fun reusedSnapshotRebuildsCalendarAfterTimezoneChangesAndCopiesDoNotReuseStaleDays() {
        val original=TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            val t=at("2026-10-09 23:59:55")
            val trip=Track("midnight",t,false,listOf(p(t),p(t+10_000)))
            val state=TrackState(listOf(trip))
            val days=DailyFootprints.days(state)
            assertEquals(2,state.dayCount)
            assertSame(days,DailyFootprints.days(state))
            assertSame(days.first().focusTrack(),days.first().focusTrack())
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"))
            assertEquals(1,state.dayCount)
            assertEquals(listOf("2026-10-10"),DailyFootprints.days(state).map { it.key })
            assertEquals(0,state.copy(tracks=emptyList()).dayCount)
            assertEquals(2,state.realPointCount)
        } finally {TimeZone.setDefault(original)}
    }
    private fun at(s:String)=SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.ROOT).parse(s)!!.time
    private fun p(time:Long,lat:Double=31.0,segment:Int=0)=TrackPoint(lat,121.0,time,5f,segment)
    @Test fun sameDayFragmentsAreOneRowWithoutBridgingTheirRoutes() {
        val t=at("2026-10-09 08:00:00")
        val a=Track("original-a",t,false,listOf(p(t),p(t+10000,31.0001)))
        val b=Track("original-b",t+100000,false,listOf(p(t+100000,32.0),p(t+110000,32.0001)))
        val state=TrackState(listOf(a,b));val day=DailyFootprints.days(state).single()
        assertEquals(4,day.points);assertEquals(2,day.segments)
        assertEquals(state.totalMeters,day.meters,0.001)
        assertEquals(day.meters,Geo.distance(day.focusTrack().points),0.001)
        assertEquals(listOf("original-a","original-b"),day.tracks.map { it.id })
    }
    @Test fun importedCrossMidnightTrackSplitsByPointTimeAndDemoDoesNotCount() {
        val t=at("2026-10-08 23:59:55")
        val track=Track("old",t,false,listOf(p(t),p(t+10000,31.0001),p(t+20000,31.0002)))
        val days=DailyFootprints.days(TrackState(listOf(track,track.copy(id="demo",demo=true))))
        assertEquals(listOf("2026-10-09","2026-10-08"),days.map { it.key })
        assertEquals(2,days.first().points);assertEquals(1,days.last().points)
        assertEquals(0.0,days.last().meters,0.0)
        assertEquals(2,DailyFootprints.select(TrackState(listOf(track)),"2026-10-09").realPointCount)
    }
    @Test fun existingGapSegmentsRemainSeparateInDailyMap() {
        val t=at("2026-10-09 12:00:00")
        val trip=Track("gap",t,false,listOf(p(t),p(t+10000,31.0001),p(t+80000,31.0004,1)))
        val day=DailyFootprints.days(TrackState(listOf(trip))).single()
        assertEquals(2,day.segments);assertEquals(Geo.distance(trip.points),Geo.distance(day.focusTrack().points),0.001)
    }
}
