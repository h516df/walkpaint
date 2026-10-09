package cn.walkpaint

/** Read-only explanation against the CURRENT catalog; never changes accepted matches or thresholds. */
internal object RoadMatchDiagnostics {
    fun reason(a:TrackPoint,b:TrackPoint,catalog:RoadCatalog):String {
        if(a.segment!=b.segment || b.time-a.time !in 1L..45000L) return "断点 / 时间间隔超限"
        if(listOf(a,b).any { !it.accuracy.isFinite() || it.accuracy !in 1f..20f }) return "道路匹配精度不足（需 1–20 米）"
        val startRoads=catalog.near(a);val endRoads=catalog.near(b)
        if(startRoads.isEmpty() || endRoads.isEmpty()) return "当前道路目录无附近候选"
        val start=RoadMatcher.match(a,startRoads);val end=RoadMatcher.match(b,endRoads)
        if(start==null || end==null) return "端点偏离道路 / 无法唯一匹配"
        if(start.road.stableId!=end.road.stableId) return "两端落在不同道路"
        if(catalog.traversed(a,b)==null) return "区间或路口不确定（保守拒绝）"
        return "当前道路包可确认"
    }
    fun report(state:TrackState,roads:RoadState):String {
        if(roads.error!=null) return "道路复算不可用：${roads.error}"
        val catalog=RoadCatalog(roads.roads)
        val pairs=state.realTracks.asSequence().flatMap { it.points.zipWithNext().asSequence() }
            .sortedByDescending { it.second.time }.take(2000).toList()
        val reasons=pairs.groupingBy { reason(it.first,it.second,catalog) }.eachCount()
        val gaps=pairs.count { it.first.segment!=it.second.segment || it.second.time-it.first.time !in 1L..45000L }
        val poor=pairs.count { listOf(it.first,it.second).any { p->!p.accuracy.isFinite() || p.accuracy !in 1f..20f } }
        val noCandidates=pairs.count { catalog.near(it.first).isEmpty() || catalog.near(it.second).isEmpty() }
        return "道路匹配只读诊断\n最近 ${pairs.size} 段，按当前道路包复算；不改原记录或确认结果。\n"+
            reasons.entries.joinToString("\n") { "${it.key}：${it.value} 段" }+
            "\n观测（可重叠）：断档 $gaps 段 / 精度不满足 $poor 段 / 无候选 $noCandidates 段。\n"+
            "当前可确认不等于历史已经涂色；新增道路或数据变化会影响复算。没有坐标、道路名或真实路线。"
    }
}
