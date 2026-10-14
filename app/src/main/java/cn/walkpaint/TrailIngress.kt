package cn.walkpaint

/** Bounded real observations, held until displacement proves movement. Worker-thread owned. */
internal class TrailIngress {
    data class Batch(val points: List<TrackPoint>, val newSegment: Boolean)
    private val fixes = ArrayList<TrackPoint>()
    private var breakBefore = true
    var rejection = "STATIONARY_OR_SMALL_MOVE"
        private set
    fun reset() { fixes.clear(); breakBefore = true }
    fun offer(point: TrackPoint, moving: Boolean = false, speed: Double? = null): Batch? {
        val previous = fixes.lastOrNull()
        val invalid = Geo.rejectionReason(null, point)
        if (invalid != null) { reset(); rejection = invalid; return null }
        if (previous != null) {
            val gap = point.time - previous.time
            if (gap <= 0) { rejection = "NON_INCREASING_TIME"; return null }
            if (gap > RecordedTrail.MAX_GAP_MS) reset()
            else {
                val reason = Geo.rejectionReason(previous, point, true, speed)
                if (reason != null && reason != "STATIONARY_OR_SMALL_MOVE") {
                    reset(); rejection = reason; return null
                }
            }
        }
        fixes.add(point)
        while (fixes.size > MAX_POINTS || point.time - fixes.first().time > WINDOW_MS) {
            fixes.removeAt(0); breakBefore = true
        }
        val first = fixes.first()
        if(fixes.size<2) { rejection="STATIONARY_OR_SMALL_MOVE";return null }
        rejection = Geo.rejectionReason(first, point, moving, speed) ?: "NONE"
        if (rejection != "NONE") return null
        // Keep original bends and original time anchors. Never synthesize coordinates or times.
        val keep = TrailShape.simplify(fixes).toMutableSet()
        var anchor = first
        fixes.drop(1).forEach { p ->
            if (p.time - anchor.time >= ANCHOR_MS) { keep.add(p); anchor = p }
        }
        val selected = fixes.filter { it in keep }
        val batch = Batch(selected, breakBefore)
        fixes.clear(); fixes.add(point); breakBefore = false
        return batch
    }
    companion object {
        const val MAX_POINTS = 32
        const val WINDOW_MS = 60_000L
        const val ANCHOR_MS = 30_000L
    }
}

internal data class TrailIntegrity(val isolated: Int, val gaps: Int, val shortLines: Int) {
    fun report() = "孤立定位 $isolated 个（保留原记录，不涂成小孔）\n" +
        "保存点时间断档 $gaps 处 · 短轨迹 $shortLines 段\n" +
        "旧记录没有对应回调证据时，断档原因未知；不会补画未记录的路线。"
    companion object {
        fun of(tracks: List<Track>): TrailIntegrity {
            val paths = RecordedTrail.paths(tracks)
            return TrailIntegrity(paths.count { !RecordedTrail.visible(it) },
                tracks.filterNot { it.demo }.sumOf { t -> t.points.zipWithNext().count { (a,b) ->
                    b.time - a.time > RecordedTrail.MAX_GAP_MS } },
                paths.count { RecordedTrail.visible(it) && it.zipWithNext().sumOf { (a,b) -> Geo.meters(a,b) } < 30.0 })
        }
    }
}
