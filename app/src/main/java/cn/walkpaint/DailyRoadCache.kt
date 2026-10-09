package cn.walkpaint

/** UI worker cache: new GPS only recalculates changed dates; road imports invalidate all days. */
internal class DailyRoadCache {
    private var geometry:List<RoadSegment>?=null
    private var counts=emptyMap<String,Int>()
    private var values=emptyMap<String,RoadState>()
    fun refresh(state:TrackState,roads:RoadState,selectedDay:String?=null,readDay:(String)->RoadState):Map<String,RoadState> {
        if(geometry !== roads.roads) { counts=emptyMap();values=emptyMap() }
        val all=DailyFootprints.days(state)
        val recent=all.take(31).map { it.key }.toSet()
        val days=all.filter { it.key in recent || it.key==selectedDay }
        val next=days.associate { day -> day.key to
            (values[day.key]?.takeIf { counts[day.key]==day.points } ?: readDay(day.key)) }
        geometry=roads.roads;counts=days.associate { it.key to it.points };values=next
        return next
    }
}
