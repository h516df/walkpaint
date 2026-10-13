package cn.walkpaint

import android.app.Instrumentation
import android.graphics.Bitmap

/**
 * Pixel-level checks on the veil image itself, run through the existing acceptance entry.
 *
 * The JVM tests around the veil only pin the constants, which proves nothing about what gets
 * drawn: a stroke that erases nothing, a border that fades instead of covering, or a tint that
 * piles up on the second pass would all pass those tests. These assertions read the rendered
 * bitmap and look at the pixels the person would actually see.
 *
 * v0.39 restated the contract: unknown ground is opaque black, the cleared corridor ramps back to
 * it over a narrow band, and the frame's own border is opaque — the border must never be visible,
 * which is now achieved by always covering the view rather than by fading out.
 */
internal object FogPixelProbe {
    private const val BASE_LAT = 48.8566
    private const val BASE_LON = 2.3522
    private const val PIGMENT = 0xFF579B8C.toInt()

    fun run(instrumentation: Instrumentation): String {
        val failures = mutableListOf<String>()
        fun check(ok: Boolean, message: String) {
            if (!ok) failures += message
        }

        fun alpha(colour: Int): Int = colour ushr 24
        fun rgb(colour: Int): Int = colour and 0x00FFFFFF

        fun point(north: Double, east: Double): TrackPoint = TrackPoint(
            BASE_LAT + north / 111194.9,
            BASE_LON + east / (111194.9 * kotlin.math.cos(Math.toRadians(BASE_LAT))),
            System.currentTimeMillis(), 5f)

        val street = listOf(point(-200.0, 0.0), point(200.0, 0.0))
        val view = ViewBounds(BASE_LAT - 0.0018, BASE_LON - 0.0026, BASE_LAT + 0.0018, BASE_LON + 0.0026)
        val painter = TrailPainter()
        val bitmap = painter.update(listOf(street), PIGMENT, null, view)
        if (bitmap == null) return "FOG_PIXEL_PROBE_FAIL: no image was produced"
        val frame = painter.frame() ?: return "FOG_PIXEL_PROBE_FAIL: no frame was produced"

        val midY = frame.pixelY(BASE_LAT).toInt().coerceIn(0, frame.heightPx - 1)
        val midX = frame.pixelX(BASE_LON).toInt().coerceIn(0, frame.widthPx - 1)

        // 1. The walked corridor keeps only the person's own tint, and ground away from it is
        //    veiled at full strength in black — the map underneath must not show through.
        val centre = bitmap.getPixel(midX, midY)
        val initialRevision=painter.revision
        val unchangedImage=painter.update(listOf(street),PIGMENT,null,view)
        check(unchangedImage===bitmap && painter.revision==initialRevision,
            "unchanged geometry and viewport re-upload the fog")
        val centreAlpha = alpha(centre)
        check(centreAlpha <= 110, "the walked corridor is still veiled: alpha=$centreAlpha")
        check((centre shr 8 and 0xFF) > (centre shr 16 and 0xFF),
            "the walked corridor is not tinted with the person's colour: ${Integer.toHexString(centre)}")
        val offX = (frame.widthPx * 0.30).toInt().coerceIn(0, frame.widthPx - 1)
        val offY = (frame.heightPx * 0.30).toInt().coerceIn(0, frame.heightPx - 1)
        val unknown = bitmap.getPixel(offX, offY)
        check(alpha(unknown) == 0xFF, "ground nobody walked is not fully veiled: alpha=${alpha(unknown)}")
        check(rgb(unknown) == 0, "the veil is not black: ${Integer.toHexString(unknown)}")

        // 2. Outside the painted band the veil must come back to full strength over a narrow ramp:
        //    narrow, because the person asked for unknown ground to be unreadable, but a ramp all
        //    the same, because a hard cut is what a stair-stepped edge looks like on a phone.
        val outward = (0..40).map { step ->
            alpha(bitmap.getPixel(minOf(frame.widthPx - 1, midX + step * 3), midY))
        }
        check(outward.last() == 0xFF, "the veil never comes back to full strength: $outward")
        val opaqueAt = outward.indexOfFirst { it == 0xFF }
        check(opaqueAt in 1..10, "the corridor edge is not a narrow ramp (opaque at sample $opaqueAt): $outward")
        check(outward.take(opaqueAt + 1).zipWithNext().all { (a, b) -> b >= a },
            "the veil does not come back steadily outside the paint: $outward")

        // 3. The frame border is opaque on every side. It is not faded any more: the frame is wider
        //    than the view, so its border must simply never be on screen, and a translucent border
        //    at the wrong origin was what produced a visible rectangular seam when panning.
        val edgeY = frame.heightPx / 2
        val edgeX = frame.widthPx / 2
        check(alpha(bitmap.getPixel(0, edgeY)) == 0xFF, "the left frame border is transparent")
        check(alpha(bitmap.getPixel(frame.widthPx - 1, edgeY)) == 0xFF, "the right frame border is transparent")
        check(alpha(bitmap.getPixel(edgeX, 0)) == 0xFF, "the top frame border is transparent")
        check(alpha(bitmap.getPixel(edgeX, frame.heightPx - 1)) == 0xFF, "the bottom frame border is transparent")

        // 4. Before the map reports a view the frame is drawn around the person; it must be wide
        //    enough that its border cannot fall inside any view the app accepts.
        val fallback = TrailPaint.frameFor(null, listOf(street.first()), street.first())
        if (fallback == null) {
            check(false, "no frame was produced without a viewport")
        } else {
            check(fallback.latSpan >= 10.0, "the fallback frame is too narrow: ${fallback.latSpan}")
            check(fallback.contains(street.first().lat, street.first().lon),
                "the fallback frame does not contain the person")
        }

        // 5. Walking the same street again (a second pass over the same ground) must leave the paint
        //    as it was: no darker band, no second corridor, no growing edge.
        val samples = (0..12).map { step -> minOf(frame.widthPx - 1, midX + step * 4) to midY }
        val firstPass = samples.map { (x, y) -> bitmap.getPixel(x, y) }
        val repeated = painter.update(listOf(street, street.reversed()), PIGMENT, null, view)!!
        val secondPass = samples.map { (x, y) -> repeated.getPixel(x, y) }
        val worst = firstPass.indices.maxOf { index ->
            val a = firstPass[index]; val b = secondPass[index]
            maxOf(
                kotlin.math.abs(alpha(a) - alpha(b)),
                kotlin.math.abs((a and 0xFF) - (b and 0xFF)),
                kotlin.math.abs(((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)),
                kotlin.math.abs(((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)),
            )
        }
        check(worst <= 8, "walking the street again changed the paint by $worst/255")

        // 6. Ground already cleared must not be veiled again by a later segment. This is the defect
        //    that made dark rings appear wherever a walk met itself: a new segment's faint outer ring
        //    was poured back over ground an earlier segment had cleared. Parallel offset passes, a
        //    crossing street and a new tail are all in the fixture, so all three are checked.
        val rowY = frame.pixelY(BASE_LAT).toInt().coerceIn(0, frame.heightPx - 1)
        fun rowAlphas(image: Bitmap): List<Int> = (0 until frame.widthPx step 2).map { x ->
            alpha(image.getPixel(x, rowY))
        }
        val before = rowAlphas(bitmap)
        val crossed = painter.update(listOf(street,
            listOf(point(-200.0, 8.0), point(200.0, 8.0)),      // a second pass, eight metres east
            listOf(point(0.0, -200.0), point(0.0, 200.0)),      // a street crossing the first
            listOf(point(200.0, 0.0), point(400.0, 0.0))),      // a new tail carrying on
            PIGMENT, null, view) ?: return "FOG_PIXEL_PROBE_FAIL: no image for the crossing case"
        val after = rowAlphas(crossed)
        var worstRevelled = 0
        var worstWhere = ""
        before.indices.forEach { index ->
            val was = before[index]
            if (was > 0xB0 - 8) return@forEach
            val x = index * 2
            val pixel = crossed.getPixel(x, rowY)
            // Being painted over is not being veiled again. The veil is pure black at every
            // strength; the person's colour never is.
            val isVeil = rgb(pixel) == 0
            val increase = if (isVeil) after[index] - was else 0
            if (increase > worstRevelled) {
                worstRevelled = increase
                worstWhere = "x=$x was=$was now=${after[index]} colour=${Integer.toHexString(pixel)}"
            }
        }
        check(worstRevelled <= 8,
            "a later segment poured veil back over cleared ground (worst +$worstRevelled/255 at $worstWhere)")

        // 7. A frame for a viewport centred on far-away ground still contains that ground: the veil
        //    follows the map, so old footprints cannot fall outside the image.
        val far = point(12_000.0, 0.0)
        val farView = ViewBounds(far.lat - 0.0018, far.lon - 0.0026, far.lat + 0.0018, far.lon + 0.0026)
        val farFrame = TrailPaint.frameFor(farView, listOf(street.first(), far))!!
        check(farFrame.contains(far.lat, far.lon),
            "ground twelve kilometres away fell outside a frame centred on it")

        // 8. A very wide view must still be fully covered: an uncovered corner is indistinguishable
        //    from ground the person has already explored, which is the whole point of the veil.
        val wideView = ViewBounds(BASE_LAT - 0.15, BASE_LON - 0.20, BASE_LAT + 0.15, BASE_LON + 0.20)
        val wideFrame = TrailPaint.frameFor(wideView, listOf(street.first()))!!
        check(wideFrame.contains(wideView.latMin, wideView.lonMin) &&
            wideFrame.contains(wideView.latMax, wideView.lonMax),
            "a view thirty kilometres across is not fully covered by the veil frame")

        // 9. A subpixel corridor must never become a kilometre-wide revealed stripe.
        val startupPainter = TrailPainter()
        val startup = startupPainter.update(listOf(street), PIGMENT, street.first(), null)!!
        val startupFrame = startupPainter.frame()!!
        check(!TrailPaint.corridorVisible(startupFrame), "startup frame unexpectedly resolves a 30m corridor")
        check(startup.getPixel(startupFrame.pixelX(BASE_LON).toInt(), startupFrame.pixelY(BASE_LAT).toInt()) == 0xFF000000.toInt(),
            "subpixel startup corridor erased the unknown map")
        val wide = TrailPainter().update(listOf(street), PIGMENT, null, wideView)!!
        check(wide.getPixel(wide.width / 2, wide.height / 2) == 0xFF000000.toInt(),
            "wide viewport widened a subpixel corridor")

        // 10. An empty selection must not inherit the previous selection's cleared corridor.
        val empty = painter.update(emptyList(), PIGMENT, null, view)!!
        check(empty.getPixel(midX, midY) == 0xFF000000.toInt(),
            "empty paths kept a previously explored corridor")

        return if (failures.isEmpty()) "FOG_PIXEL_PROBE_PASS"
        else "FOG_PIXEL_PROBE_FAIL: " + failures.joinToString(" | ")
    }
}
