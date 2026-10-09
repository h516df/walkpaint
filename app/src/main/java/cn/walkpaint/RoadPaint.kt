package cn.walkpaint

import kotlin.math.*

/** Caller must supply a stable, dataset-version-qualified segment ID and WGS84 geometry.
 * Tile clipping/zoom geometry hashes are deliberately NOT persistent road identities.
 */
internal data class RoadSegment(val stableId: String, val geometry: List<TrackPoint>, val osmVersion: Int = 1,
    val highway: String = "road") {
    // A road traversed backwards still uses the same increasing distance axis.
    val points: List<TrackPoint> = if (geometry.size > 1 &&
        compareValuesBy(geometry.first(), geometry.last(), { it.lat }, { it.lon }) > 0)
        geometry.reversed() else geometry
    val length: Double = points.zipWithNext().sumOf { (a, b) -> Geo.meters(a, b) }
}

internal data class RoadMatch(val road: RoadSegment, val along: Double, val error: Double)
internal data class PaintedInterval(val roadId: String, val from: Double, val to: Double)

/** Conservative local matcher: no routing service, no straight-line bridge across roads/gaps. */
internal object RoadMatcher {
    private fun valid(p: TrackPoint) = p.lat.isFinite() && p.lon.isFinite() &&
        p.lat in -85.0..85.0 && p.lon in -180.0..180.0

    internal data class Inspection(val match: RoadMatch?, val reason: String, val candidates: Int)

    fun match(fix: TrackPoint, roads: List<RoadSegment>): RoadMatch? = inspect(fix, roads).match

    fun inspect(fix: TrackPoint, roads: List<RoadSegment>): Inspection {
        if (!valid(fix) || !fix.accuracy.isFinite() || fix.accuracy !in 1f..20f)
            return Inspection(null, "无效端点或精度", 0)
        val radius = max(8.0, fix.accuracy.toDouble()).coerceAtMost(20.0)
        val candidates = roads.filter { it.stableId.isNotBlank() && it.points.size >= 2 &&
            it.points.all(::valid) }.distinctBy { it.stableId }.flatMap { road ->
            var offset = 0.0
            val matches = mutableListOf<RoadMatch>()
            road.points.zipWithNext().forEach { (a, b) ->
                // Local equirectangular projection; bounded to nearby road edges.
                val scale = 111_195.0
                val cosLat = cos(Math.toRadians(fix.lat))
                fun x(p: TrackPoint) = (p.lon - fix.lon) * scale * cosLat
                fun y(p: TrackPoint) = (p.lat - fix.lat) * scale
                val ax = x(a); val ay = y(a)
                val dx = x(b) - ax; val dy = y(b) - ay
                val length = Geo.meters(a, b)
                val denominator = dx * dx + dy * dy
                if (denominator > 0) {
                    val t = (-(ax * dx + ay * dy) / denominator).coerceIn(0.0, 1.0)
                    val error = hypot(ax + t * dx, ay + t * dy)
                    if (error <= radius) matches += RoadMatch(road, offset + t * length, error)
                }
                offset += length
            }
            matches
        }.sortedBy { it.error }
        val first = candidates.firstOrNull() ?: return Inspection(null, "附近目录有道路，但端点距道路超出匹配半径", 0)
        // Preserve the 4m ambiguity guard, including self-crossings on one way.
        val rivals = candidates.drop(1).filter { it.error - first.error < 4.0 &&
            (it.road.stableId != first.road.stableId || abs(it.along-first.along) > 10.0) }
        val reason = when {
            rivals.any { it.road.stableId != first.road.stableId } -> "多条道路歧义（平行路或路口）"
            rivals.isNotEmpty() -> "同一道路几何歧义（回折或自交）"
            else -> "端点可唯一匹配"
        }
        return Inspection(first.takeIf { rivals.isEmpty() }, reason, candidates.size)
    }

    fun traversed(a: TrackPoint, b: TrackPoint, roads: List<RoadSegment>): PaintedInterval? {
        val delta = b.time - a.time
        if (a.segment != b.segment || delta !in 1L..45_000L) return null
        val start = match(a, roads) ?: return null
        val end = match(b, roads) ?: return null
        if (start.road.stableId != end.road.stableId) return null
        val distance = abs(end.along - start.along)
        val observed = Geo.meters(a, b)
        if (distance < 3.0 || distance > 120.0 || observed < 3.0 || distance > delta / 1000.0 * 8.0 ||
            distance > observed * 1.8 + 10.0) return null
        val interval = PaintedInterval(start.road.stableId, min(start.along, end.along), max(start.along, end.along))
        // Even same-way endpoints must not bridge an ambiguous junction/self-crossing.
        val line = RoadCoverage(listOf(interval)).highlighted(start.road).single()
        for ((p,q) in line.zipWithNext()) {
            val steps = ceil(Geo.meters(p,q)/5.0).toInt().coerceAtLeast(1)
            for(i in 0..steps) {
                val t = i.toDouble()/steps
                val sample = p.copy(lat=p.lat+(q.lat-p.lat)*t,lon=p.lon+(q.lon-p.lon)*t,
                    accuracy=max(a.accuracy,b.accuracy))
                if(match(sample,roads)?.road?.stableId != start.road.stableId) return null
            }
        }
        return interval
    }
}

/** Pure cumulative coverage. Same stable road + overlapping intervals never count twice. */
internal class RoadCoverage(initial: List<PaintedInterval> = emptyList()) {
    private val coverage = mutableMapOf<String, List<PaintedInterval>>()
    init { initial.forEach(::add) }
    fun add(interval: PaintedInterval) {
        require(interval.roadId.isNotBlank() && interval.from.isFinite() && interval.to.isFinite() &&
            interval.from >= 0 && interval.to > interval.from)
        val sorted = (coverage[interval.roadId].orEmpty() + interval).sortedBy { it.from }
        val merged = mutableListOf<PaintedInterval>()
        sorted.forEach { next ->
            val old = merged.lastOrNull()
            if (old != null && next.from <= old.to) merged[merged.lastIndex] = old.copy(to = max(old.to, next.to))
            else merged += next
        }
        coverage[interval.roadId] = merged
    }
    fun snapshot(): List<PaintedInterval> = coverage.toSortedMap().values.flatten()
    val meters: Double get() = snapshot().sumOf { it.to - it.from }

    fun highlighted(road: RoadSegment): List<List<TrackPoint>> {
        fun at(distance: Double): TrackPoint {
            var offset = 0.0
            road.points.zipWithNext().forEach { (a, b) ->
                val length = Geo.meters(a, b)
                if (length > 0 && distance <= offset + length) {
                    val t = ((distance - offset) / length).coerceIn(0.0, 1.0)
                    return a.copy(lat = a.lat + (b.lat - a.lat) * t, lon = a.lon + (b.lon - a.lon) * t)
                }
                offset += length
            }
            return road.points.last()
        }
        require(road.points.size >= 2 && road.length > 0)
        return coverage[road.stableId].orEmpty().mapNotNull { interval ->
            val from = interval.from.coerceAtMost(road.length)
            val to = interval.to.coerceAtMost(road.length)
            if (from >= to) return@mapNotNull null
            var offset = 0.0
            val middle = mutableListOf<TrackPoint>()
            road.points.zipWithNext().forEach { (a, b) ->
                offset += Geo.meters(a, b)
                if (offset > from && offset < to) middle += b
            }
            listOf(at(from)) + middle + at(to)
        }
    }
}
