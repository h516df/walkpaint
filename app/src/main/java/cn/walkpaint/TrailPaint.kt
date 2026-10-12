package cn.walkpaint

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader

/** What the map is currently showing, in WGS84. The veil follows this rather than the walk. */
internal data class ViewBounds(
    val latMin: Double, val lonMin: Double, val latMax: Double, val lonMax: Double,
) {
    val latSpan: Double get() = latMax - latMin
    val lonSpan: Double get() = lonMax - lonMin
    val centreLat: Double get() = (latMax + latMin) / 2
    val centreLon: Double get() = (lonMax + lonMin) / 2
}

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
    /**
     * Longest side of the veil image, in pixels.
     *
     * Handing the image to the map costs the UI thread one upload of its pixels, measured as the
     * whole of that cost: at 768 the handover was 32-56 ms, and the picture only needs to carry a
     * corridor a few dozen pixels wide with a soft edge. 512 halves the upload while still giving
     * the fade more than twenty pixels to ramp over.
     */
    const val MAX_PX = 512
    /** Smallest frame, so one short walk still gets a usable image instead of a zero-size one. */
    const val MIN_SPAN_DEG = 0.002
    const val MARGIN = 0.12
    /**
     * Past this the frame is too coarse to read anyway, so it follows where the person is instead
     * of stretching over everything they have ever walked.
     */
    const val MAX_SPAN_METERS = 20000.0
    /** The veil covers the viewport plus this share of it, so a pan does not outrun the image. */
    const val VIEW_MARGIN = 0.35
    /** How far the veil fades out at the frame border, in metres, whatever the frame's size. */
    const val FADE_METRES = 900.0
    /** Width of the ground a walk fully clears, and the band that carries the person's colour. */
    const val CORRIDOR_M = 20.0
    /** The ramp over which the veil comes back outside that band, so there is no hard edge. */
    const val FEATHER_M = 12.0
    /** The veil over ground not yet walked: dark enough to read as fog, not so dark the map dies. */
    const val FOG_COLOR = 0xB01B2430.toInt()
    /** How strongly cleared ground takes the person's own colour. */
    const val TINT_ALPHA = 84
    /** The frame fades out over this share of its shorter side, so it never shows as a rectangle. */
    const val EDGE_FADE = 0.14

    private const val METRES_PER_LAT = 111194.9

    fun frameOf(points: List<TrackPoint>, maxPx: Int = MAX_PX, anchor: TrackPoint? = null): PaintFrame? =
        frameFor(null, points, anchor, maxPx)

    /**
     * The frame the veil is drawn into.
     *
     * With a [viewport] the frame follows the map: the veil covers what the person is looking at,
     * so panning to ground walked days ago still finds it painted, and the ground nobody has
     * walked between here and there stays behind fog. Without one — before the map has reported
     * its projection — it falls back to the walked area around the person.
     */
    fun frameFor(viewport: ViewBounds?, points: List<TrackPoint>,
                 anchor: TrackPoint? = null, maxPx: Int = MAX_PX): PaintFrame? {
        val usable = points.filter { it.lat.isFinite() && it.lon.isFinite() }
        val centreLat: Double
        val centreLon: Double
        var latMin: Double
        var latMax: Double
        var lonMin: Double
        var lonMax: Double
        if (viewport != null && viewport.latSpan > 0.0 && viewport.lonSpan > 0.0) {
            centreLat = viewport.centreLat
            centreLon = viewport.centreLon
            val latPad = viewport.latSpan * VIEW_MARGIN / 2
            val lonPad = viewport.lonSpan * VIEW_MARGIN / 2
            latMin = viewport.latMin - latPad
            latMax = viewport.latMax + latPad
            lonMin = viewport.lonMin - lonPad
            lonMax = viewport.lonMax + lonPad
        } else {
            if (usable.isEmpty()) return null
            latMin = usable.minOf { it.lat }
            latMax = usable.maxOf { it.lat }
            lonMin = usable.minOf { it.lon }
            lonMax = usable.maxOf { it.lon }
            centreLat = anchor?.lat ?: (latMax + latMin) / 2
            centreLon = anchor?.lon ?: (lonMax + lonMin) / 2
        }
        if (latMax - latMin < MIN_SPAN_DEG) {
            latMin = centreLat - MIN_SPAN_DEG / 2
            latMax = centreLat + MIN_SPAN_DEG / 2
        }
        if (lonMax - lonMin < MIN_SPAN_DEG) {
            lonMin = centreLon - MIN_SPAN_DEG / 2
            lonMax = centreLon + MIN_SPAN_DEG / 2
        }
        // The cap only applies to a frame derived from the walked area. When the frame follows the
        // viewport it must cover the whole view at any zoom: capping it would leave the corners of a
        // wide view with no veil at all, which reads as ground that has been explored. A very wide
        // view gets a coarser image instead, which is the honest trade.
        if (viewport == null || viewport.latSpan <= 0.0 || viewport.lonSpan <= 0.0) {
            val capLat = MAX_SPAN_METERS / METRES_PER_LAT
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
    /** The buffer being drawn into next, so the map can keep reading the one it was handed. */
    private var scratch: Bitmap? = null
    private var colour = 0

    private val fog = Paint().apply {
        style = Paint.Style.FILL
        color = TrailPaint.FOG_COLOR
        // Replace rather than blend. The buffer is reused between updates, and blending the veil
        // over what was left in it would make the fog darker with every fix.
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC)
    }
    /**
     * The corridor is painted by *replacing* pixels, layer by layer, from the untouched veil at the
     * outside down to fully revealed at the edge of the walked band, and then with the person's own
     * colour. Replacing is what makes a second pass over the same street land on exactly the same
     * picture: subtracting (CLEAR or DST_OUT) would take more veil away every time and leave the
     * fog thinner on the streets walked most often, which is the opposite of what a person expects.
     */
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC)
    }
    /**
     * A filled eraser, used once per freshly poured veil to fade the frame border. A stroke paint
     * there only drew the outline of the band and left the veil in place.
     */
    private val eraseFill = Paint().apply {
        style = Paint.Style.FILL
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
    }

    /** Bumped only when the veil really changes, so the map is not handed a new image for nothing. */
    var revision = 0
        private set

    /** True when the last update had to repaint the whole frame instead of just the new tail. */
    var lastUpdateRebuilt = false
        private set

    fun image(): Bitmap? = bitmap

    fun frame(): PaintFrame? = frame

    fun update(paths: List<List<TrackPoint>>, color: Int, anchor: TrackPoint? = null,
               viewport: ViewBounds? = null): Bitmap? {
        if (paths.all { it.isEmpty() }) return bitmap
        colour = color
        val current = frame
        // The frame follows the map, so a pan or a zoom is what moves it; the person's own position
        // only matters before the map has reported a projection at all.
        val frameMoved = current == null || viewportMoved(current, viewport) ||
            (viewport == null && anchor != null && !current.contains(anchor.lat, anchor.lon))
        val next = if (!frameMoved && current != null) current
            else TrailPaint.frameFor(viewport, paths.flatten(), anchor) ?: return bitmap
        frame = next
        lastUpdateRebuilt = frameMoved
        if (BuildConfig.DEBUG) {
            android.util.Log.d("WalkPaintFog", "frame px=${next.widthPx}x${next.heightPx} " +
                "latSpan=${next.latSpan} lonSpan=${next.lonSpan} " +
                "perMetre=${pixelsPerMeter(next)} corridorPx=${TrailPaint.CORRIDOR_M * pixelsPerMeter(next)}")
        }
        // Every update redraws the whole frame from the geometry instead of patching it. Patching
        // poured a new segment's faint outer ring back over ground an earlier segment had already
        // cleared, which showed up as dark rings wherever a walk met itself. Drawing everything at
        // once, one ring at a time across all segments, makes the result depend only on the
        // geometry: nothing a later segment draws can put veil back on cleared ground.
        val target = scratch?.takeIf { it.width == next.widthPx && it.height == next.heightPx }
            ?: Bitmap.createBitmap(next.widthPx, next.heightPx, Bitmap.Config.ARGB_8888)
        scratch = bitmap
        bitmap = target
        val canvas = Canvas(target)
        canvas.drawRect(0f, 0f, next.widthPx.toFloat(), next.heightPx.toFloat(), fog)
        strokeAll(canvas, next, segmentsIn(paths, next))
        fadeEdges(canvas, next)
        revision++
        return target
    }

    /**
     * True when the frame no longer covers what the map is showing with room to spare, so the veil
     * has to be redrawn for the new view. A little slack means a pan does not redraw on every step.
     */
    private fun viewportMoved(frame: PaintFrame, viewport: ViewBounds?): Boolean {
        if (viewport == null || viewport.latSpan <= 0.0 || viewport.lonSpan <= 0.0) return false
        val latSlack = frame.latSpan * 0.18
        val lonSlack = frame.lonSpan * 0.18
        if (viewport.centreLat < frame.latMin + latSlack || viewport.centreLat > frame.latMax - latSlack) return true
        if (viewport.centreLon < frame.lonMin + lonSlack || viewport.centreLon > frame.lonMax - lonSlack) return true
        val ratio = viewport.latSpan / frame.latSpan
        return ratio < 0.40 || ratio > 1.05
    }

    /** Wipes the veil back to unexplored, for when the colour or the data changes. */
    fun clear() {
        frame = null
        bitmap = null
        scratch = null
        revision++
    }

    fun release() {
        bitmap = null
        scratch = null
        frame = null
    }

    /**
     * The in-frame segments, already in frame pixels.
     *
     * The frame follows the view, so most of a long history lies outside it: dropping those here is
     * what keeps a redraw proportional to what can be seen rather than to everything ever walked.
     */
    private fun segmentsIn(paths: List<List<TrackPoint>>, target: PaintFrame): List<FloatArray> {
        val out = ArrayList<FloatArray>()
        for (path in paths) {
            if (path.isEmpty()) continue
            if (path.size == 1) {
                // A lone fix is still ground: it gets the corridor as a dot.
                val p = path[0]
                if (p.lat.isFinite() && p.lon.isFinite() && target.contains(p.lat, p.lon)) {
                    val x = target.pixelX(p.lon); val y = target.pixelY(p.lat)
                    out.add(floatArrayOf(x, y, x, y))
                }
                continue
            }
            for (index in 1 until path.size) {
                val a = path[index - 1]
                val b = path[index]
                if (!a.lat.isFinite() || !a.lon.isFinite() || !b.lat.isFinite() || !b.lon.isFinite()) continue
                if (maxOf(a.lat, b.lat) < target.latMin || minOf(a.lat, b.lat) > target.latMax) continue
                if (maxOf(a.lon, b.lon) < target.lonMin || minOf(a.lon, b.lon) > target.lonMax) continue
                out.add(floatArrayOf(target.pixelX(a.lon), target.pixelY(a.lat),
                    target.pixelX(b.lon), target.pixelY(b.lat)))
            }
        }
        return out
    }

    /**
     * Draws the corridor, one ring at a time across every segment.
     *
     * The outer loop is the ring, not the segment, and that ordering is the point: a segment's
     * faint wide ring is laid down before any segment's narrow fully-cleared one, so ground that is
     * already explored can never be veiled again by a later segment passing near it.
     */
    private fun strokeAll(canvas: Canvas, target: PaintFrame, segments: List<FloatArray>) {
        if (segments.isEmpty()) return
        val perMetre = pixelsPerMeter(target)
        val core = (TrailPaint.CORRIDOR_M * perMetre).toFloat().coerceAtLeast(2f)
        val feather = (TrailPaint.FEATHER_M * perMetre).toFloat().coerceAtLeast(1f)
        val veilAlpha = (TrailPaint.FOG_COLOR ushr 24).toFloat()
        paint.color = TrailPaint.FOG_COLOR or (0xFF shl 24)
        ringsFor(feather).forEach { ring ->
            paint.strokeWidth = core + 2f * feather * ring.reach
            paint.alpha = (veilAlpha * ring.alpha).toInt().coerceIn(1, 255)
            for (segment in segments) {
                canvas.drawLine(segment[0], segment[1], segment[2], segment[3], paint)
            }
        }
        // Then the person's own colour on the ground the fade has just cleared.
        paint.color = colour and 0x00FFFFFF
        paint.strokeWidth = core
        paint.alpha = TrailPaint.TINT_ALPHA.coerceIn(1, 255)
        for (segment in segments) {
            canvas.drawLine(segment[0], segment[1], segment[2], segment[3], paint)
        }
    }

    /**
     * The veil is poured over the whole frame and only taken away along the route. The border is
     * faded last, over a fixed distance rather than a share of the frame, so the veil thins out at
     * its edge instead of ending in a rectangle whatever size the frame happens to be.
     */
    private fun fadeEdges(canvas: Canvas, target: PaintFrame) {
        val perMetre = pixelsPerMeter(target)
        // A share of the frame, capped in metres. The frame now follows the viewport, so at street
        // zoom it is a few hundred metres across: a fixed nine hundred metre fade would cover the
        // whole of it and leave the map bare. The cap keeps a twenty kilometre frame from fading
        // over kilometres of map.
        val shortest = minOf(target.widthPx, target.heightPx)
        val fade = minOf(shortest * TrailPaint.EDGE_FADE, TrailPaint.FADE_METRES * perMetre).toInt()
            .coerceIn(1, shortest / 3)
        val opaque = TrailPaint.FOG_COLOR or (0xFF shl 24)
        val transparent = TrailPaint.FOG_COLOR and 0x00FFFFFF
        eraseFill.shader = LinearGradient(0f, 0f, fade.toFloat(), 0f, opaque, transparent, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, fade.toFloat(), target.heightPx.toFloat(), eraseFill)
        eraseFill.shader = LinearGradient((target.widthPx - fade).toFloat(), 0f, target.widthPx.toFloat(), 0f, transparent, opaque, Shader.TileMode.CLAMP)
        canvas.drawRect((target.widthPx - fade).toFloat(), 0f, target.widthPx.toFloat(), target.heightPx.toFloat(), eraseFill)
        eraseFill.shader = LinearGradient(0f, 0f, 0f, fade.toFloat(), opaque, transparent, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, target.widthPx.toFloat(), fade.toFloat(), eraseFill)
        eraseFill.shader = LinearGradient(0f, (target.heightPx - fade).toFloat(), 0f, target.heightPx.toFloat(), transparent, opaque, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, (target.heightPx - fade).toFloat(), target.widthPx.toFloat(), target.heightPx.toFloat(), eraseFill)
        eraseFill.shader = null
    }

    private data class Ring(val reach: Float, val alpha: Float)

    companion object {
        /** Most steps in the fade. One step is under five levels of alpha, and the gap between two
         * of them is smaller than a pixel, so the ramp reads as a gradient rather than as the rings
         * it is made of. */
        private const val FADE_STEPS = 36

        /**
         * Outermost first, each one a step fainter, from the untouched veil down to fully revealed
         * at the edge of the walked band.
         *
         * The count follows the frame: the ramp is a fixed number of metres wide, so on a wide
         * frame it spans only a few pixels and a long list of rings would be work with nothing to
         * show for it.
         */
        private fun ringsFor(featherPx: Float): List<Ring> {
            val steps = (featherPx / 1.5f).toInt().coerceIn(3, FADE_STEPS)
            return (0..steps).map { step ->
                val t = step.toFloat() / steps
                Ring(1f - t, (1f - t).coerceAtLeast(0.004f))
            }
        }

        fun pixelsPerMeter(frame: PaintFrame): Double {
            val metresTall = frame.latSpan * 111194.9
            if (metresTall <= 0.0) return 0.0
            return frame.heightPx / metresTall
        }
    }
}
