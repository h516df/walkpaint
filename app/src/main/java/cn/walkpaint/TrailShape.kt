package cn.walkpaint

/**
 * Makes a recorded path draw as a line rather than as a tangle.
 *
 * Standing in one place, a phone still reports a few metres of drift every two seconds, and drawing
 * every fix faithfully turns that drift into a knot of thread. Simplifying the shape for drawing
 * removes the back-and-forth within a few metres while keeping the route recognisable. The stored
 * points are never touched: only what is handed to the renderer.
 */
internal object TrailShape {
    /** Below this, a detour is drift rather than walking. */
    const val TOLERANCE_M = 4.0

    /**
     * Ground counts as already drawn once a fix is this close to a segment that has been drawn.
     * Two passes over one street carry their own GPS offsets, so the rule has to be a distance in
     * metres rather than a grid: a grid answer depends on where the squares happen to fall, which
     * is exactly how a parallel line used to appear beside the first one.
     */
    const val DEDUPE_RADIUS_M = 8.0

    /** Two accepted points further apart than this were not one continuous stretch of walking. */
    const val LINK_M = 150.0

    /**
     * Collapses repeated passes over the same ground onto a single line, returning runs of ground.
     *
     * Walking a street twice used to draw two lines a few metres apart, because each pass carries
     * its own GPS offset: on the phone that reads as a braid. A fix that lands on ground already
     * drawn is left out of the line instead, so the second pass adds nothing while the list of
     * drawn segments grows underneath it. Points are only ever dropped, never moved.
     */
    fun dedupe(paths: List<List<TrackPoint>>, radiusM: Double = DEDUPE_RADIUS_M): List<List<TrackPoint>> {
        val drawn = DrawnGround(radiusM)
        val runs = mutableListOf<MutableList<TrackPoint>>()
        paths.forEach { path ->
            var run: MutableList<TrackPoint>? = null
            var kept = 0
            var previous: TrackPoint? = null
            for (point in path) {
                if (!point.lat.isFinite() || !point.lon.isFinite()) {
                    previous = null
                    continue
                }
                val last = previous
                previous = point
                if (last == null) continue
                if (drawn.near(point, radiusM)) continue
                val open = run
                if (open == null || Geo.meters(open.last(), point) > LINK_M) {
                    run = mutableListOf(last).also { runs.add(it) }
                }
                run?.add(point)
                drawn.add(last, point)
                kept++
            }
            // A short walk inside already-drawn ground keeps its far end, or the line would stop a
            // handful of metres short of where the person actually got to.
            if (kept > 0) {
                val open = runs.lastOrNull()
                val end = path.lastOrNull()
                if (open != null && end != null && end != open.last() &&
                    Geo.meters(open.last(), end) <= LINK_M) open.add(end)
            }
        }
        return runs.filter { it.size > 1 }
    }

    /**
     * The line the map draws over the veil: the person's own ground, collapsed, clipped to what is
     * on screen, and simplified to well under one bitmap pixel at the current view.
     *
     * The veil's image is capped at a few hundred pixels, so zoomed out the cleared corridor falls
     * under a single pixel and the record of where the person has been would vanish — exactly the
     * screen where that record matters most. This line carries it instead, at a fixed screen width,
     * at every zoom. Handing the whole history to the map on every update was measured as a 2.1 s
     * worst case on a loaded emulator, so only what can be seen is sent, and only to the precision
     * the view can actually show.
     */
    fun network(paths: List<List<TrackPoint>>, frame: PaintFrame?): List<List<TrackPoint>> {
        val runs = dedupe(paths)
        if (frame == null || frame.latSpan <= 0.0 || frame.lonSpan <= 0.0) return runs
        val perMetre = frame.heightPx / (frame.latSpan * 111194.9)
        val tolerance = if (perMetre > 0.0) (0.75 / perMetre).coerceIn(0.0, 60.0) else 0.0
        val latPad = frame.latSpan * 0.05
        val lonPad = frame.lonSpan * 0.05
        return runs.mapNotNull { run ->
            val visible = run.filter {
                it.lat in (frame.latMin - latPad)..(frame.latMax + latPad) &&
                    it.lon in (frame.lonMin - lonPad)..(frame.lonMax + lonPad)
            }
            if (visible.size < 2) null else simplify(visible, tolerance)
        }
    }

    fun simplify(points: List<TrackPoint>, toleranceM: Double = TOLERANCE_M): List<TrackPoint> {
        if (points.size <= 2 || toleranceM <= 0.0) return points
        val keep = BooleanArray(points.size)
        keep[0] = true
        keep[points.size - 1] = true
        val stack = ArrayDeque<Pair<Int, Int>>()
        stack.addLast(0 to points.size - 1)
        while (stack.isNotEmpty()) {
            val (first, last) = stack.removeLast()
            if (last <= first + 1) continue
            var worst = -1.0
            var worstAt = first
            for (index in first + 1 until last) {
                val distance = perpendicularMeters(points[first], points[last], points[index])
                if (distance > worst) { worst = distance; worstAt = index }
            }
            if (worst > toleranceM) {
                keep[worstAt] = true
                stack.addLast(first to worstAt)
                stack.addLast(worstAt to last)
            }
        }
        return points.filterIndexed { index, _ -> keep[index] }
    }

    /** Distance from [point] to the segment [a]..[b], in metres, on a local flat approximation. */
    private fun perpendicularMeters(a: TrackPoint, b: TrackPoint, point: TrackPoint): Double {
        val metresPerLat = 111194.9
        val metresPerLon = metresPerLat * kotlin.math.cos(Math.toRadians(a.lat)).coerceAtLeast(0.05)
        val ax = 0.0; val ay = 0.0
        val bx = (b.lon - a.lon) * metresPerLon; val by = (b.lat - a.lat) * metresPerLat
        val px = (point.lon - a.lon) * metresPerLon; val py = (point.lat - a.lat) * metresPerLat
        val dx = bx - ax; val dy = by - ay
        val lengthSquared = dx * dx + dy * dy
        if (lengthSquared <= 1e-9) return kotlin.math.hypot(px, py)
        val t = ((px - ax) * dx + (py - ay) * dy) / lengthSquared
        val clamped = t.coerceIn(0.0, 1.0)
        return kotlin.math.hypot(px - (ax + clamped * dx), py - (ay + clamped * dy))
    }
}

/**
 * The segments already drawn, kept in a grid of metres so a candidate fix can ask "is this ground
 * already on the line?" without comparing against every segment ever drawn.
 *
 * Coordinates are measured from the first segment, which is accurate enough over the tens of
 * kilometres a person walks, and it is the same flat approximation the rest of the app uses.
 */
internal class DrawnGround(private val radiusM: Double) {
    private val cellM = maxOf(radiusM * 2.0, 1.0)
    private val cells = HashMap<Long, MutableList<DoubleArray>>()
    private var anchorLat = 0.0
    private var anchorLon = 0.0
    private var metresPerLon = 111194.9
    private var anchored = false
    /** A segment longer than this many cells is a jump, not walking, and is not indexed. */
    private val maxCells = 64

    fun near(point: TrackPoint, radius: Double): Boolean {
        if (!anchored) return false
        val (x, y) = metres(point.lat, point.lon)
        val bucket = cells[key(cell(x), cell(y))] ?: return false
        return bucket.any { distanceTo(x, y, it) <= radius }
    }

    fun add(a: TrackPoint, b: TrackPoint) {
        if (!anchored) {
            anchored = true
            anchorLat = a.lat
            anchorLon = a.lon
            metresPerLon = 111194.9 * kotlin.math.cos(Math.toRadians(a.lat)).coerceAtLeast(0.05)
        }
        val (ax, ay) = metres(a.lat, a.lon)
        val (bx, by) = metres(b.lat, b.lon)
        val segment = doubleArrayOf(ax, ay, bx, by)
        val minX = minOf(ax, bx) - radiusM
        val maxX = maxOf(ax, bx) + radiusM
        val minY = minOf(ay, by) - radiusM
        val maxY = maxOf(ay, by) + radiusM
        val firstCol = cell(minX); val lastCol = cell(maxX)
        val firstRow = cell(minY); val lastRow = cell(maxY)
        if ((lastCol - firstCol + 1).toLong() * (lastRow - firstRow + 1).toLong() > maxCells * maxCells) return
        for (col in firstCol..lastCol) {
            for (row in firstRow..lastRow) {
                cells.getOrPut(key(col, row)) { mutableListOf() }.add(segment)
            }
        }
    }

    private fun metres(lat: Double, lon: Double): Pair<Double, Double> =
        (lon - anchorLon) * metresPerLon to (lat - anchorLat) * 111194.9

    private fun cell(metres: Double): Int = kotlin.math.floor(metres / cellM).toInt()

    private fun key(col: Int, row: Int): Long = (row.toLong() shl 32) xor (col.toLong() and 0xFFFFFFFFL)

    private fun distanceTo(x: Double, y: Double, segment: DoubleArray): Double {
        val ax = segment[0].toDouble(); val ay = segment[1].toDouble()
        val bx = segment[2].toDouble(); val by = segment[3].toDouble()
        val dx = bx - ax; val dy = by - ay
        val lengthSquared = dx * dx + dy * dy
        if (lengthSquared <= 1e-9) return kotlin.math.hypot(x - ax, y - ay)
        val t = (((x - ax) * dx + (y - ay) * dy) / lengthSquared).coerceIn(0.0, 1.0)
        return kotlin.math.hypot(x - (ax + t * dx), y - (ay + t * dy))
    }
}

/** One period's worth of a person's own history, in the terms they would say it themselves. */
internal data class Recap(
    val period: String,
    val meters: Double,
    val days: Int,
    val bestDay: String,
    val bestDayMeters: Double,
    val longestStreak: Int,
    val firstDay: String,
)

internal object RecapMaker {
    /** Summarises the days inside one `yyyy-MM` month or one `yyyy` year. */
    fun of(days: List<DailyFootprint>, period: String): Recap? {
        val inside = days.filter { it.key.startsWith(period) }
        if (inside.isEmpty()) return null
        val ordered = inside.sortedBy { it.key }
        val best = ordered.maxByOrNull { it.meters } ?: return null
        return Recap(
            period = period,
            meters = ordered.sumOf { it.meters },
            days = ordered.size,
            bestDay = best.key,
            bestDayMeters = best.meters,
            longestStreak = longestStreak(ordered.map { it.key }),
            firstDay = ordered.first().key,
        )
    }

    /** The longest run of consecutive days anywhere in the list, not just the current one. */
    internal fun longestStreak(keys: List<String>): Int {
        val dates = keys.mapNotNull { runCatching { java.time.LocalDate.parse(it) }.getOrNull() }
            .distinct().sorted()
        if (dates.isEmpty()) return 0
        var best = 1
        var run = 1
        for (index in 1 until dates.size) {
            run = if (dates[index] == dates[index - 1].plusDays(1)) run + 1 else 1
            if (run > best) best = run
        }
        return best
    }
}
