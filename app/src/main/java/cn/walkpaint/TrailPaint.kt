package cn.walkpaint

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode

/** The patch of ground the paint covers, and how many pixels it is drawn into. */
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
 * Paints the recorded tracks into one image instead of drawing every pass as its own line.
 *
 * Walking the same streets again and again is normal, and drawing each pass separately lays dozens
 * of nearly parallel lines a few metres apart over each other: a braid, not a route. Painting into
 * a bitmap fixes that by construction, because a second pass over the same street repaints the same
 * pixels instead of adding a line beside them. It is the approach Strava's heatmap uses, and it
 * happens to be what this app is named after: the ground gets painted, not outlined.
 *
 * The frame is the whole recorded area, so one image covers every trip at once and the map only has
 * to hold a single overlay.
 */
internal object TrailPaint {
    /** A 1200 px square is about 5.5 MB, and still finer than a metre per pixel over 1 km. */
    const val MAX_PX = 1200
    /** Smallest frame, so one short walk still gets a usable image instead of a zero-size one. */
    const val MIN_SPAN_DEG = 0.002
    const val MARGIN = 0.06
    const val STROKE_PX = 11f

    fun frameOf(points: List<TrackPoint>, maxPx: Int = MAX_PX): PaintFrame? {
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
        val latMargin = (latMax - latMin) * MARGIN
        val lonMargin = (lonMax - lonMin) * MARGIN
        latMin -= latMargin; latMax += latMargin
        lonMin -= lonMargin; lonMax += lonMargin
        val latSpan = latMax - latMin
        val lonSpan = lonMax - lonMin
        // Latitude degrees are longer than longitude ones, so scale by metres before choosing pixels.
        val metresTall = latSpan * 111194.9
        val metresWide = lonSpan * 111194.9 * kotlin.math.cos(Math.toRadians((latMin + latMax) / 2))
        val longest = maxOf(metresTall, metresWide, 1.0)
        val scale = minOf(maxPx / longest, 4.0)
        val heightPx = (metresTall * scale).toInt().coerceIn(64, maxPx)
        val widthPx = (metresWide * scale).toInt().coerceIn(64, maxPx)
        return PaintFrame(latMin, lonMin, latMax, lonMax, widthPx, heightPx)
    }
}

/**
 * Keeps one bitmap of painted ground up to date.
 *
 * New tracks are drawn onto the existing image rather than rebuilding it, so a fix arriving every
 * two seconds costs one short line, not a full repaint of everything ever walked.
 */
internal class TrailPainter {
    private var frame: PaintFrame? = null
    private var bitmap: Bitmap? = null
    private var paintedPaths = 0
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = TrailPaint.STROKE_PX
    }

    /** The current image, or null when there is nothing to paint. */
    fun image(): Bitmap? = bitmap

    fun frame(): PaintFrame? = frame

    /**
     * Draws whatever is new and returns the image to show. Returns null when nothing is painted.
     * A track that reaches outside the current frame forces a rebuild, because the frame is what
     * converts coordinates to pixels.
     */
    fun update(points: List<TrackPoint>, paths: List<List<TrackPoint>>, color: Int): Bitmap? {
        if (points.isEmpty() || paths.isEmpty()) return bitmap
        paint.color = color
        val current = frame
        val fits = current != null && points.all { current.contains(it.lat, it.lon) }
        if (!fits) {
            val next = TrailPaint.frameOf(points) ?: return bitmap
            frame = next
            bitmap?.recycle()
            bitmap = Bitmap.createBitmap(next.widthPx, next.heightPx, Bitmap.Config.ARGB_8888)
            paintedPaths = 0
        }
        val target = bitmap ?: return null
        val active = frame ?: return null
        if (paintedPaths >= paths.size) return target
        val canvas = Canvas(target)
        for (index in paintedPaths until paths.size) {
            val path = paths[index]
            if (path.size < 2) continue
            var previousX = active.pixelX(path[0].lon)
            var previousY = active.pixelY(path[0].lat)
            for (point in path.drop(1)) {
                val x = active.pixelX(point.lon)
                val y = active.pixelY(point.lat)
                canvas.drawLine(previousX, previousY, x, y, paint)
                previousX = x; previousY = y
            }
        }
        paintedPaths = paths.size
        return target
    }

    /** Wipes the paint, for when the colour changes or the data is replaced. */
    fun clear() {
        bitmap?.eraseColor(0)
        paintedPaths = 0
    }

    fun release() {
        bitmap?.recycle()
        bitmap = null
        frame = null
        paintedPaths = 0
    }
}

