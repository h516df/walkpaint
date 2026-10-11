package cn.walkpaint

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader

/** The patch of ground the veil covers, and how many pixels it is drawn into. */
internal data class PaintFrame(
    val latMin: Double, val lonMin: Double, val latMax: Double, val lonMax: Double,
    val widthPx: Int, val heightPx: Int,
) {
    val latSpan: Double get() = latMax - latMin
    val lonSpan: Double get() = lonMax - lonMin

    fun pixelX(lon: Double): Float = ((lon - lonMin) / lonSpan * widthPx).toFloat()
    fun pixelY(lat: Double): Float = ((latMax - lat) / latSpan * heightPx).toFloat()

    fun contains(lat: Double, lon: Double): Boolean =
        lat in latMin..latMax && lon in lonMin..lonMax
}

/**
 * The veil over ground that has not been walked, and the soft band that walking clears in it.
 *
 * The first attempt cut the veil away in hundred-metre blocks, because a block is either lit or
 * not and no amount of GPS wobble can change that. On the phone the blocks read as exactly what
 * they were: a grid of squares stuck over the map. Fog is not made of squares, and a person
 * walking down a street clears a band along the street, not the fields either side of it.
 *
 * So the veil is cleared by stroking the route: a wide round-capped line, repeated at four widths
 * with rising strength, leaves a corridor whose edge fades instead of stepping. Stroking the same
 * street twice is safe by construction rather than by bookkeeping: the middle of the corridor is
 * cleared and repainted, so it lands on the value it already had, and no line is drawn per pass
 * that could braid. What used to grow with every lap was the tint; the clear pass in front of it
 * is what keeps it from accumulating.
 */
internal object TrailPaint {
    /**
     * A 768 px square is 2.25 MB. The corridor is stroked across this image, so its size is the
     * cost of every repaint: a megapixel frame cost a hundred milliseconds to rebuild on a loaded
     * device, and half of that is spent on pixels nobody can see.
     */
    const val MAX_PX = 768
    /** Smallest frame, so one short walk still gets a usable image instead of a zero-size one. */
    const val MIN_SPAN_DEG = 0.002
    const val MARGIN = 0.12
    /**
     * Past this the frame is too coarse to read anyway, so it follows where the person is instead
     * of stretching over everything they have ever walked.
     */
    const val MAX_SPAN_METERS = 8000.0
    /** Half width of the ground a walk fully clears: about a street plus its pavements. */
    const val CORRIDOR_M = 15.0
    /** The ramp over which the veil comes back, so the cleared ground has no hard edge. */
    const val FEATHER_M = 12.0
    /** The veil over ground not yet walked: dark enough to read as fog, not so dark the map dies. */
    const val FOG_COLOR = 0xB01B2430.toInt()
    /** How strongly cleared ground takes the person's own colour. */
    const val TINT_ALPHA = 84
    /** The frame fades out over this share of its shorter side, so it never shows as a rectangle. */
    const val EDGE_FADE = 0.14

    private const val METRES_PER_LAT = 111194.9

    fun frameOf(points: List<TrackPoint>, maxPx: Int = MAX_PX, anchor: TrackPoint? = null): PaintFrame? {
        val usable = points.filter { it.lat.isFinite() && it.lon.isFinite() }
        if (usable.isEmpty()) return null
        var latMin = usable.minOf { it.lat }
        var latMax = usable.maxOf { it.lat }
        var lonMin = usable.minOf { it.lon }
        var lonMax = usable.maxOf { it.lon }
        if (latMax - latMin < MIN_SPAN_DEG) {
            val centre = (latMax + latMin) / 2
            latMin = centre - MIN_SPAN_DEG / 2
            latMax = centre + MIN_SPAN_DEG / 2
        }
        if (lonMax - lonMin < MIN_SPAN_DEG) {
            val centre = (lonMax + lonMin) / 2
            lonMin = centre - MIN_SPAN_DEG / 2
            lonMax = centre + MIN_SPAN_DEG / 2
        }
        val capLat = MAX_SPAN_METERS / METRES_PER_LAT
        val centreLat = anchor?.lat ?: (latMax + latMin) / 2
        val centreLon = anchor?.lon ?: (lonMax + lonMin) / 2
        val metresPerLon = METRES_PER_LAT * kotlin.math.cos(Math.toRadians(centreLat)).coerceAtLeast(0.05)
        val capLon = MAX_SPAN_METERS / metresPerLon
        if (latMax - latMin > capLat) {
            latMin = centreLat - capLat / 2
            latMax = centreLat + capLat / 2
        }
        if (lonMax - lonMin > capLon) {
            lonMin = centreLon - capLon / 2
            lonMax = centreLon + capLon / 2
        }
        val latMargin = (latMax - latMin) * MARGIN
        val lonMargin = (lonMax - lonMin) * MARGIN
        latMin -= latMargin; latMax += latMargin
        lonMin -= lonMargin; lonMax += lonMargin
        val latSpan = latMax - latMin
        val lonSpan = lonMax - lonMin
        // Latitude degrees are longer than longitude ones, so scale by metres before choosing pixels.
        val metresTall = latSpan * METRES_PER_LAT
        val metresWide = lonSpan * METRES_PER_LAT * kotlin.math.cos(Math.toRadians((latMin + latMax) / 2))
        val longest = maxOf(metresTall, metresWide, 1.0)
        val scale = minOf(maxPx / longest, 4.0)
        val heightPx = (metresTall * scale).toInt().coerceIn(64, maxPx)
        val widthPx = (metresWide * scale).toInt().coerceIn(64, maxPx)
        return PaintFrame(latMin, lonMin, latMax, lonMax, widthPx, heightPx)
    }
}

/**
 * Keeps one veil image up to date.
 *
 * Work is proportional to what was just walked, not to what is stored: a fix every two seconds
 * strokes the one segment it added, and a street walked a second time strokes the ground it is
 * standing on, which already looks like that. The image only reports a new [revision] when the
 * veil really changed, so neither renderer re-uploads four megabytes of texture for nothing.
 */
internal class TrailPainter {
    private var frame: PaintFrame? = null
    private var bitmap: Bitmap? = null
    private var colour = 0
    /** Points of each incoming path already stroked, so a fix only ever paints its own new tail. */
    private var paintedPerPath = mutableListOf<Int>()
    /** Where the painted list began, so an import that reorders the history is noticed. */
    private var paintedFrom: TrackPoint? = null

    private val fog = Paint().apply { style = Paint.Style.FILL; color = TrailPaint.FOG_COLOR }
    private val clear = Paint().apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }
    private val tint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    /** Bumped only when the veil really changes, so the map is not handed a new image for nothing. */
    var revision = 0
        private set

    /** True when the last update had to repaint the whole frame instead of just the new tail. */
    var lastUpdateRebuilt = false
        private set

    fun image(): Bitmap? = bitmap

    fun frame(): PaintFrame? = frame

    fun update(paths: List<List<TrackPoint>>, color: Int, anchor: TrackPoint? = null): Bitmap? {
        lastUpdateRebuilt = false
        if (paths.all { it.isEmpty() }) return bitmap
        colour = color
        tint.color = color and 0x00FFFFFF
        val current = frame
        val anchorInside = current != null &&
            (anchor == null || current.contains(anchor.lat, anchor.lon))
        // The remembered counts only describe the list they were built from. A restore, a day
        // filter or a shorter list has to be repainted from scratch rather than patched.
        val stale = paths.size < paintedPerPath.size ||
            paths.withIndex().any { (index, path) ->
                index < paintedPerPath.size && path.size < paintedPerPath[index]
            } || (paintedFrom != null && paths.firstOrNull()?.firstOrNull() != paintedFrom)
        if (!anchorInside || stale) {
            lastUpdateRebuilt = true
            val next = if (anchorInside && current != null) current
            else TrailPaint.frameOf(paths.flatten(), anchor = anchor) ?: return bitmap
            frame = next
            // Left to the collector rather than recycled: a renderer may still be holding the old
            // image in an overlay when this runs.
            val fresh = Bitmap.createBitmap(next.widthPx, next.heightPx, Bitmap.Config.ARGB_8888)
            bitmap = fresh
            val canvas = Canvas(fresh)
            canvas.drawRect(0f, 0f, next.widthPx.toFloat(), next.heightPx.toFloat(), fog)
            paintedPerPath = MutableList(paths.size) { 0 }
            paths.forEachIndexed { index, path -> paintedPerPath[index] = strokeWhole(canvas, next, path) }
            paintedFrom = paths.firstOrNull()?.firstOrNull()
            fadeEdges(canvas, next)
            revision++
            return fresh
        }
        val target = bitmap ?: return null
        val active = frame ?: return null
        val canvas = Canvas(target)
        var painted = false
        paths.forEachIndexed { index, path ->
            if (path.isEmpty()) return@forEachIndexed
            val done = if (index < paintedPerPath.size) paintedPerPath[index] else 0
            when {
                // A lone fix is still ground: it gets the corridor as a dot.
                path.size == 1 && done == 0 -> { stroke(canvas, active, path[0], path[0]); painted = true }
                path.size >= 2 -> {
                    // Paint the whole path the first time, then only the segment it grew by. One
                    // segment of overlap means a reshape by simplification can never leave a gap.
                    val from = if (done == 0) 1 else maxOf(1, done - 1)
                    for (i in from until path.size) {
                        if (!path[i - 1].lat.isFinite() || !path[i].lat.isFinite()) continue
                        stroke(canvas, active, path[i - 1], path[i])
                        painted = true
                    }
                }
            }
            if (paintedPerPath.size <= index) paintedPerPath.add(path.size) else paintedPerPath[index] = path.size
        }
        if (painted) revision++
        return target
    }

    /** Wipes the veil back to unexplored, for when the colour or the data changes. */
    fun clear() {
        frame = null
        paintedPerPath = mutableListOf()
        paintedFrom = null
        revision++
    }

    fun release() {
        bitmap = null
        frame = null
        paintedPerPath = mutableListOf()
        paintedFrom = null
    }

    private fun strokeWhole(canvas: Canvas, target: PaintFrame, path: List<TrackPoint>): Int {
        if (path.isEmpty()) return 0
        if (path.size == 1) {
            if (path[0].lat.isFinite() && path[0].lon.isFinite()) stroke(canvas, target, path[0], path[0])
            return 1
        }
        for (index in 1 until path.size) {
            val a = path[index - 1]
            val b = path[index]
            if (!a.lat.isFinite() || !a.lon.isFinite() || !b.lat.isFinite() || !b.lon.isFinite()) continue
            stroke(canvas, target, a, b)
        }
        return path.size
    }

    /**
     * The veil is poured over the whole frame and only taken away along the route. The border is
     * faded last, so the frame reads as fog that thins out rather than as a rectangle.
     */
    private fun fadeEdges(canvas: Canvas, target: PaintFrame) {
        val fade = (minOf(target.widthPx, target.heightPx) * TrailPaint.EDGE_FADE).toInt()
            .coerceIn(1, minOf(target.widthPx, target.heightPx) / 3)
        val opaque = TrailPaint.FOG_COLOR or (0xFF shl 24)
        val transparent = TrailPaint.FOG_COLOR and 0x00FFFFFF
        clear.shader = LinearGradient(0f, 0f, fade.toFloat(), 0f, opaque, transparent, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, fade.toFloat(), target.heightPx.toFloat(), clear)
        canvas.drawRect((target.widthPx - fade).toFloat(), 0f, target.widthPx.toFloat(), target.heightPx.toFloat(), clear)
        clear.shader = LinearGradient(0f, 0f, 0f, fade.toFloat(), opaque, transparent, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, target.widthPx.toFloat(), fade.toFloat(), clear)
        canvas.drawRect(0f, (target.heightPx - fade).toFloat(), target.widthPx.toFloat(), target.heightPx.toFloat(), clear)
        clear.shader = null
    }

    /**
     * Clears the corridor along one segment: the same round-capped stroke at four widths, each
     * removing more of the veil than the one before. The widest, faintest pass is what leaves the
     * soft edge; the narrowest takes the middle out completely, which is also what stops the tint
     * from piling up when the same street is walked again.
     */
    private fun stroke(canvas: Canvas, target: PaintFrame, a: TrackPoint, b: TrackPoint) {
        val x1 = target.pixelX(a.lon); val y1 = target.pixelY(a.lat)
        val x2 = target.pixelX(b.lon); val y2 = target.pixelY(b.lat)
        val perMetre = pixelsPerMeter(target)
        val core = (TrailPaint.CORRIDOR_M * perMetre).toFloat().coerceAtLeast(2f)
        val feather = (TrailPaint.FEATHER_M * perMetre).toFloat().coerceAtLeast(0.6f)
        RINGS.forEach { ring ->
            val width = core + 2f * feather * ring.reach
            clear.strokeWidth = width
            clear.alpha = (ring.clear * 255f).toInt().coerceIn(1, 255)
            canvas.drawLine(x1, y1, x2, y2, clear)
            tint.strokeWidth = width
            tint.alpha = (TrailPaint.TINT_ALPHA * ring.tint).toInt().coerceIn(1, 255)
            canvas.drawLine(x1, y1, x2, y2, tint)
        }
    }

    private data class Ring(val reach: Float, val clear: Float, val tint: Float)

    companion object {
        /** Outer, faintest first, so each later pass takes more veil and the middle ends up clear. */
        private val RINGS = listOf(
            Ring(1.0f, 0.14f, 0.30f),
            Ring(0.66f, 0.26f, 0.55f),
            Ring(0.33f, 0.44f, 0.80f),
            Ring(0.0f, 1.0f, 1.0f),
        )

        fun pixelsPerMeter(frame: PaintFrame): Double {
            val metresTall = frame.latSpan * 111194.9
            if (metresTall <= 0.0) return 0.0
            return frame.heightPx / metresTall
        }
    }
}
