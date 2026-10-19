package cn.walkpaint

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

internal fun walkGrowth(state:TrackState,today:LocalDate=LocalDate.now()):GrowthStats {
    val from=GrowthRules.weekStart(today).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val until=today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val week=state.realTracks.sumOf{track->Geo.distance(track.points.filter{it.time in from until until})}.toLong()/1000
    return GrowthStats("山河行记","到过的地方，留下的日子",listOf(
        GrowthMetric("distance","累计记录",state.totalMeters.toLong(),listOf(1000,10000,100000,1000000),listOf("初行一公里","十里之外","百公里旅人","千公里行者"),"米"),
        GrowthMetric("exploring_days","探索天数",state.dayCount.toLong(),listOf(3,7,30,100),listOf("出发三日","一周探索者","月度漫游者","百日足迹"),"天"),
        GrowthMetric("trail","足迹篇章",state.realTracks.size.toLong(),listOf(1,10,50),listOf("第一道光","十段故事","足迹成册"),"段")
    ),week,"公里",listOf(3,10,30))
}
