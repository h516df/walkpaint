package cn.walkpaint

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode

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
     * The span of the frame drawn before the map has reported a usable view, centred on the person.
     *
     * The veil is opaque, so a frame narrower than the screen would read as ground the person has
     * already explored — the opposite of what the veil means. This is deliberately as wide as any
     * view the app accepts, so the frame's edge can never fall inside the screen. What made the
     * first screen readable was *not* this width, and shrinking it was only ever a candidate fix:
     * at sixty degrees the corridor is far under a pixel, and the stroke used to have a two pixel
     * floor, so "thinner than a pixel" became a two pixel line — over thirty kilometres of ground
     * reported as walked, covering the whole street-level first screen. The floor is gone (see
     * [corridorVisible]); this stays wide because covering the view is its only job.
     */
    const val FALLBACK_SPAN_DEG = 60.0
    /** The veil covers the viewport plus this share of it, so a pan does not outrun the image. */
    const val VIEW_MARGIN = 0.35
    /** Width of the ground a walk fully clears, and the band that carries the person's colour. */
    const val CORRIDOR_M = 30.0
    /** The ramp over which the veil comes back outside that band, so there is no hard edge. */
    const val FEATHER_M = 3.0
    /**
     * The veil over ground not yet walked. Opaque black on purpose: the person asked for unknown
     * ground to be genuinely unknown, and a veil that lets the road network show through is not fog.
     */
    const val FOG_COLOR = 0xFF000000.toInt()
    /** How strongly cleared ground takes the person's own colour. */
    const val TINT_ALPHA = 84
    /**
     * When the corridor is thinner than this in the veil's own image, the veil cannot show it and
     * the map has to draw the walked line itself. Above it the cleared corridor is plainly visible,
     * and handing the map a line as well costs the UI thread a GeoJSON rebuild for nothing.
     */
    const val NETWORK_BELOW_PX = 2.0

    /**
     * Whether this frame can show the corridor at its true width.
     *
     * This is the rule that keeps the veil honest, and it exists because its absence was a visible
     * defect: the stroke used to be widened to a two pixel floor so that *something* would show.
     * On a wide frame that turned a sub-pixel corridor into a two pixel line — and two pixels of a
     * sixty degree frame is over thirty kilometres of ground that had never been walked. Reported
     * as explored, it erased the fog across the whole first screen.
     *
     * The invariant is therefore: the veil image either shows a corridor of the right ground width,
     * or shows no corridor at all. It never shows one wider than the person actually cleared. When
     * it shows none, the map draws the walked route as a line instead, so the record survives.
     */
    fun corridorVisible(frame: PaintFrame): Boolean =
        CORRIDOR_M * TrailPainter.pixelsPerMeter(frame) >= NETWORK_BELOW_PX

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
            // Before the map reports a projection there is no view to follow, so the frame is set
            // wide enough that its edge cannot show while the camera is still settling.
            val anchorLat = anchor?.lat
                ?: usable.takeIf { it.isNotEmpty() }?.let { p -> (p.minOf { it.lat } + p.maxOf { it.lat }) / 2 }
            val anchorLon = anchor?.lon
                ?: usable.takeIf { it.isNotEmpty() }?.let { p -> (p.minOf { it.lon } + p.maxOf { it.lon }) / 2 }
            if (anchorLat == null || anchorLon == null) return null
            centreLat = anchorLat
            centreLon = anchorLon
            latMin = centreLat - FALLBACK_SPAN_DEG / 2
            latMax = centreLat + FALLBACK_SPAN_DEG / 2
            lonMin = centreLon - FALLBACK_SPAN_DEG / 2
            lonMax = centreLon + FALLBACK_SPAN_DEG / 2
        }
        if (latMax - latMin < MIN_SPAN_DEG) {
            latMin = centreLat - MIN_SPAN_DEG / 2
            latMax = centreLat + MIN_SPAN_DEG / 2
        }
        if (lonMax - lonMin < MIN_SPAN_DEG) {
            lonMin = centreLon - MIN_SPAN_DEG / 2
            lonMax = centreLon + MIN_SPAN_DEG / 2
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
    private var paintedPaths:List<List<TrackPoint>>?=null

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
    /** Bumped only when the veil really changes, so the map is not handed a new image for nothing. */
    var revision = 0
        private set

    /** True when the last update had to repaint the whole frame instead of just the new tail. */
    var lastUpdateRebuilt = false
        private set

    /**
     * Whether the last image handed out contains a cleared corridor.
     *
     * Kept because "the paths are empty" is not the same as "the image is empty". Ground that was
     * walked and is now unknown — an emptied or reset dataset, a day selection cleared back to
     * nothing — has to *lose* its corridor, so a skip that only looked at the input being empty
     * would keep serving the old, partly revealed picture. The skip below needs this too.
     */
    private var paintedCorridor = false

    fun image(): Bitmap? = bitmap

    fun frame(): PaintFrame? = frame

    fun update(paths: List<List<TrackPoint>>, color: Int, anchor: TrackPoint? = null,
               viewport: ViewBounds? = null): Bitmap? {
        val walked = paths.any { it.isNotEmpty() }
        val current = frame
        // The frame follows the map, so a pan or a zoom is what moves it; the person's own position
        // only matters before the map has reported a projection at all.
        val frameMoved = current == null || viewportMoved(current, viewport) ||
            (viewport == null && anchor != null && !current.contains(anchor.lat, anchor.lon))
        if(!frameMoved && bitmap!=null && paintedPaths==paths && colour==color) {
            lastUpdateRebuilt=false
            return bitmap
        }
        colour=color
        // Someone who has not walked anywhere yet still has unknown ground in front of them, so the
        // veil is drawn all black rather than not drawn at all. Skipping it entirely is what showed
        // a brand-new person the whole basemap on the first screen. The skip also requires that the
        // image does not currently show a corridor: with no paths, an image that has one is wrong.
        if (!walked && !frameMoved && bitmap != null && !paintedCorridor) return bitmap
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
        val segments = segmentsIn(paths, next)
        // Recorded from the geometry actually stroked, so the skip above can never keep an image
        // whose corridor has since stopped being justified.
        paintedCorridor = segments.isNotEmpty() && TrailPaint.corridorVisible(next)
        strokeAll(canvas, next, segments)
        paintedPaths=paths
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
        paintedCorridor = false
        paintedPaths=null
        revision++
    }

    fun release() {
        bitmap = null
        scratch = null
        frame = null
        paintedCorridor = false
        paintedPaths=null
    }

    /**
     * The in-frame segments, already in frame pixels.
     *
     * The frame follows the view, so most of a long history lies outside it: dropping those here is
     * what keeps a redraw proportional to what can be seen rather than to everything ever walked.
     */
    private fun segmentsIn(paths: List<List<TrackPoint>>, target: PaintFrame): List<FloatArray> {
        val out = ArrayList<FloatArray>()
        // The same stretch of ground can be handed to the painter more than once: a walk out and
        // back, the same trip listed twice, or the day view drawn on top of the cumulative one.
        // Stroking it twice is not idempotent along a stroke's anti-aliased edge — each pass blends
        // the veil and the person's colour again, so the boundary creeps darker — which is what the
        // narrow ramp of 0.39 exposed. Identical ground is stroked once instead, with the endpoints
        // ordered so that A→B and B→A collapse to the same key, quantised to a tenth of a pixel.
        val seen = HashSet<Long>()
        fun quantised(value: Float) = (value * 10f).toLong().coerceIn(0L, 0x1FFFL)
        fun keyOf(x1: Float, y1: Float, x2: Float, y2: Float): Long {
            val ax = quantised(x1); val ay = quantised(y1)
            val bx = quantised(x2); val by = quantised(y2)
            return if (ax < bx || (ax == bx && ay <= by)) {
                (ax shl 39) or (ay shl 26) or (bx shl 13) or by
            } else {
                (bx shl 39) or (by shl 26) or (ax shl 13) or ay
            }
        }
        for (path in paths) {
            if (!RecordedTrail.visible(path)) continue
            for (index in 1 until path.size) {
                val a = path[index - 1]
                val b = path[index]
                if (!a.lat.isFinite() || !a.lon.isFinite() || !b.lat.isFinite() || !b.lon.isFinite()) continue
                if (maxOf(a.lat, b.lat) < target.latMin || minOf(a.lat, b.lat) > target.latMax) continue
                if (maxOf(a.lon, b.lon) < target.lonMin || minOf(a.lon, b.lon) > target.lonMax) continue
                val x1 = target.pixelX(a.lon); val y1 = target.pixelY(a.lat)
                val x2 = target.pixelX(b.lon); val y2 = target.pixelY(b.lat)
                if (!seen.add(keyOf(x1, y1, x2, y2))) continue
                out.add(floatArrayOf(x1, y1, x2, y2))
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
        // No corridor rather than a false one: see corridorVisible. Widening it to keep something
        // visible is what reported thirty kilometres of never-walked ground as explored.
        if (!TrailPaint.corridorVisible(target)) return
        val perMetre = pixelsPerMeter(target)
        // At this point the corridor is at least NETWORK_BELOW_PX pixels wide by definition, so the
        // old two pixel floor would have been a no-op; it is gone from the width and from the
        // feather, which is what used to inflate the reveal on wide frames.
        val core = (TrailPaint.CORRIDOR_M * perMetre).toFloat()
        val feather = (TrailPaint.FEATHER_M * perMetre).toFloat().coerceAtLeast(0.5f)
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
