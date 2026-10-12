package cn.walkpaint

import android.app.Instrumentation
import android.graphics.Bitmap

/**
 * Pixel-level checks on the veil image itself, run through the existing acceptance entry.
 *
 * The JVM tests around the veil only pin the constants, which proves nothing about what gets
 * drawn: a stroke that erases nothing, a border that ends in a right angle, or a tint that piles
 * up on the second pass would all pass those tests. These assertions read the rendered bitmap and
 * look at the pixels the person would actually see.
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

        // 1. The walked corridor keeps only the person's own tint (the veil is gone), and ground
        //    away from it is still behind the veil.
        val centre = bitmap.getPixel(midX, midY)
        val centreAlpha = alpha(centre)
        check(centreAlpha <= 110, "the walked corridor is still veiled: alpha=$centreAlpha")
        check((centre shr 8 and 0xFF) > (centre shr 16 and 0xFF),
            "the walked corridor is not tinted with the person's colour: ${Integer.toHexString(centre)}")
        val offX = (frame.widthPx * 0.30).toInt().coerceIn(0, frame.widthPx - 1)
        val offY = (frame.heightPx * 0.30).toInt().coerceIn(0, frame.heightPx - 1)
        val offAlpha = alpha(bitmap.getPixel(offX, offY))
        check(offAlpha > 0x60, "ground nobody walked is not veiled: alpha=$offAlpha")

        // 2. Outside the painted band the veil has to come back gradually and reach full strength:
        //    the paint's own edge is a crisp edge (that is what paint looks like), but the fog must
        //    ramp rather than cut, and no single step may jump most of the way.
        val outward = (0..40).map { step ->
            alpha(bitmap.getPixel(minOf(frame.widthPx - 1, midX + step * 3), midY))
        }
        check(outward.distinct().size >= 4, "the corridor edge steps instead of fading: $outward")
        check(outward.max() >= 0xA0, "the veil never comes back to full strength: $outward")
        // The paint's own edge is a crisp edge — that is what paint looks like — so the ramp and
        // the no-cut checks start where the painted band ends.
        val paintEnd = outward.indexOfFirst { it < centreAlpha - 4 }
        check(paintEnd >= 0, "the painted band never ends: $outward")
        val fade = outward.drop(paintEnd)
        check(fade.size >= 3, "there is no fade outside the painted band: $outward")
        check(fade.zipWithNext().all { (a, b) -> kotlin.math.abs(b - a) <= 64 },
            "the veil ends in a cut rather than a ramp: $outward")
        check(fade.zipWithNext().all { (a, b) -> b >= a - 2 },
            "the veil does not come back steadily outside the paint: $outward")

        // 3. The frame border fades out over a real distance rather than ending in a right angle,
        //    and the veil comes back steadily as you move inward.
        val fadeWidth = minOf(256, frame.widthPx / 3)
        val border = (0..8).map { step ->
            alpha(bitmap.getPixel(minOf(frame.widthPx - 1, step * fadeWidth / 8), frame.heightPx / 2))
        }
        check(border.first() <= 8, "the frame border is not faded: $border")
        check(border.last() >= 0xA0, "the veil never returns inside the border fade: $border")
        check(border.distinct().size >= 4, "the border is a cut, not a fade: $border")
        check(border.zipWithNext().all { (a, b) -> b >= a - 2 },
            "the veil does not come back steadily from the border: $border")

        // 4. Walking the same street again (a second pass over the same ground) must leave the paint
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

        // 5. Ground already cleared must not be veiled again by a later segment. This is the defect
        //    that made dark rings appear wherever a walk met itself: a new segment's faint outer ring
        //    was poured back over ground an earlier segment had cleared. Parallel offset passes, a
        //    crossing street and a new tail are all in the fixture, so all three are checked.
        val rowY = frame.pixelY(BASE_LAT).toInt().coerceIn(0, frame.heightPx - 1)
        fun rowAlphas(image: Bitmap): List<Int> = (0 until frame.widthPx step 2).map { x ->
            alpha(image.getPixel(x, rowY))
        }
        val before = rowAlphas(bitmap)
        val beforeColours = (0 until frame.widthPx step 2).map { x -> bitmap.getPixel(x, rowY) }
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
            // Being painted over is not being veiled again: the veil is navy (blue above green),
            // the person's colour is the other way round.
            val veiledAgain = (pixel and 0xFF) > (pixel shr 8 and 0xFF)
            val increase = if (veiledAgain) after[index] - was else 0
            if (increase > worstRevelled) {
                worstRevelled = increase
                worstWhere = "x=$x was=$was now=${after[index]} " +
                    "colour=${Integer.toHexString(pixel)} before=${Integer.toHexString(beforeColours[index])}"
            }
        }
        check(worstRevelled <= 8,
            "a later segment poured veil back over cleared ground (worst +$worstRevelled/255 at $worstWhere)")

        // 6. A frame for a viewport centred on far-away ground still contains that ground: the veil
        //    follows the map, so old footprints cannot fall outside the image.
        val far = point(12_000.0, 0.0)
        val farView = ViewBounds(far.lat - 0.0018, far.lon - 0.0026, far.lat + 0.0018, far.lon + 0.0026)
        val farFrame = TrailPaint.frameFor(farView, listOf(street.first(), far))!!
        check(farFrame.contains(far.lat, far.lon),
            "ground twelve kilometres away fell outside a frame centred on it")

        // 7. A view wider than the old span cap must still be fully covered: capping the frame would
        //    leave its corners unveiled, which reads as ground that has been explored.
        val wideView = ViewBounds(BASE_LAT - 0.15, BASE_LON - 0.20, BASE_LAT + 0.15, BASE_LON + 0.20)
        val wideFrame = TrailPaint.frameFor(wideView, listOf(street.first()))!!
        check(wideFrame.contains(wideView.latMin, wideView.lonMin) &&
            wideFrame.contains(wideView.latMax, wideView.lonMax),
            "a view thirty kilometres across is not fully covered by the veil frame")

        // Check every edge, rather than only the left one. A gradient at the wrong origin leaves
        // the right/bottom edges opaque and produces a visible rectangular seam while panning.
        val edgeImage=TrailPainter().update(listOf(street),PIGMENT,null,view)!!
        val edgeY=edgeImage.height/3
        check(alpha(edgeImage.getPixel(edgeImage.width-1,edgeY))<=8,"right fog border is not faded")
        check(alpha(edgeImage.getPixel(edgeImage.width/3,edgeImage.height-1))<=8,"bottom fog border is not faded")

        return if (failures.isEmpty()) "FOG_PIXEL_PROBE_PASS"
        else "FOG_PIXEL_PROBE_FAIL: " + failures.joinToString(" | ")
    }
}
