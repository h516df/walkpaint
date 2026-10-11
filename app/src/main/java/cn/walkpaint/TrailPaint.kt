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
 * Explored ground, drawn as a grid of blocks rather than as a line along the route.
 *
 * Drawing the route as a line braided when the same streets were walked again, and widening the
 * stroke to cover the braid turned the trail into a smear: every pass carries its own twenty metres
 * of GPS error, so painting each pass adds its own offset band and the union only ever grows.
 *
 * A grid fixes that at the root. A block is either explored or not, so walking somewhere again
 * changes nothing, and because a block is about a hundred and eleven metres across, five times the
 * error, a wobble inside it cannot spill into the next one and blur the edge. It also happens to be
 * the feeling the app is for: the map clears as you go, like a game revealing its map.
 *
 * The image is fog with holes cut in it. Unexplored ground keeps a pale veil, explored blocks are
 * cleared and tinted with the person's own colour, so the lit ground reads as theirs.
 */
internal object TrailPaint {
    /** A 1200 px square is about 5.5 MB, and still finer than a metre per pixel over 1 km. */
    const val MAX_PX = 1200
    /** Smallest frame, so one short walk still gets a usable image instead of a zero-size one. */
    const val MIN_SPAN_DEG = 0.002
    const val MARGIN = 0.06
    /**
     * Past this the frame is too coarse to read anyway, so it follows where the person is instead
     * of stretching over everything they have ever walked.
     */
    const val MAX_SPAN_METERS = 6000.0
    /** About 111 m: big enough that GPS error stays inside one block, small enough to feel local. */
    const val CELL_DEGREES = 0.001
    /** The veil over ground not yet walked. Pale rather than dark, so the map stays legible. */
    const val FOG_COLOR = 0x73F2F4F5.toInt()
    /** How strongly an explored block takes the person's colour. */
    const val CELL_TINT_ALPHA = 120

    fun cellRow(lat: Double): Int = kotlin.math.floor(lat / CELL_DEGREES).toInt()
    fun cellCol(lon: Double): Int = kotlin.math.floor(lon / CELL_DEGREES).toInt()
    fun cellKey(row: Int, col: Int): Long = (row.toLong() shl 32) xor (col.toLong() and 0xFFFFFFFFL)

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
        val capLat = MAX_SPAN_METERS / 111194.9
        val centreLat = anchor?.lat ?: (latMax + latMin) / 2
        val centreLon = anchor?.lon ?: (lonMax + lonMin) / 2
        val metresPerLon = 111194.9 * kotlin.math.cos(Math.toRadians(centreLat)).coerceAtLeast(0.05)
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
 * Keeps one fog image up to date: pale everywhere, with the walked blocks cleared and tinted.
 *
 * Blocks are drawn once and remembered, so a fix arriving every two seconds paints at most the one
 * block it landed in, never the whole history again.
 */
internal class TrailPainter {
    private var frame: PaintFrame? = null
    private var bitmap: Bitmap? = null
    private val painted = HashSet<Long>()
    private var colour = 0
    private val fog = Paint().apply { style = Paint.Style.FILL; color = TrailPaint.FOG_COLOR }
    private val tint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val clear = Paint().apply {
        style = Paint.Style.FILL
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    fun image(): Bitmap? = bitmap

    fun frame(): PaintFrame? = frame

    /** Cells already lit, for anything that needs to count them. */
    fun litCount(): Int = painted.size

    fun update(points: List<TrackPoint>, color: Int, anchor: TrackPoint? = null): Bitmap? {
        if (points.isEmpty()) return bitmap
        val current = frame
        val fresh = if (current == null) points
            else points.filterNot { current.contains(it.lat, it.lon) }
        if (current == null || fresh.isNotEmpty()) {
            val next = TrailPaint.frameOf(points, anchor = anchor) ?: return bitmap
            frame = next
            bitmap?.recycle()
            bitmap = Bitmap.createBitmap(next.widthPx, next.heightPx, Bitmap.Config.ARGB_8888)
            painted.clear()
        }
        val target = bitmap ?: return null
        val active = frame ?: return null
        colour = color
        tint.color = (color and 0x00FFFFFF) or (TrailPaint.CELL_TINT_ALPHA shl 24)
        val canvas = Canvas(target)
        val cellPx = (TrailPaint.CELL_DEGREES * 111194.9 * pixelsPerMeter(active)).toFloat()
        if (painted.isEmpty()) canvas.drawRect(0f, 0f, active.widthPx.toFloat(), active.heightPx.toFloat(), fog)
        points.forEach { point ->
            if (!point.lat.isFinite() || !point.lon.isFinite()) return@forEach
            val row = TrailPaint.cellRow(point.lat)
            val col = TrailPaint.cellCol(point.lon)
            if (!painted.add(TrailPaint.cellKey(row, col))) return@forEach
            val left = active.pixelX(col * TrailPaint.CELL_DEGREES)
            val top = active.pixelY((row + 1) * TrailPaint.CELL_DEGREES)
            canvas.drawRect(left, top, left + cellPx, top + cellPx, clear)
            canvas.drawRect(left, top, left + cellPx, top + cellPx, tint)
        }
        return target
    }

    /** Wipes the fog back to unexplored, for when the colour or the data changes. */
    fun clear() {
        bitmap?.eraseColor(0)
        painted.clear()
    }

    fun release() {
        bitmap?.recycle()
        bitmap = null
        frame = null
        painted.clear()
    }

    companion object {
        fun pixelsPerMeter(frame: PaintFrame): Double {
            val metresTall = frame.latSpan * 111194.9
            if (metresTall <= 0.0) return 0.0
            return frame.heightPx / metresTall
        }
    }
}
