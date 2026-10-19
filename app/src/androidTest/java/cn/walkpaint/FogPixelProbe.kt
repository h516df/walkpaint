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
 * The readable-fog contract: unknown ground keeps a themed translucent veil, the cleared corridor ramps back to
 * it over a narrow band, and the frame's own border has the same veil — the border must never be visible,
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
        val veilAlpha=alpha(TrailPaint.FOG_COLOR)
        fun isVeil(pixel:Int):Boolean = listOf(0,8,16,24).all {shift ->
            kotlin.math.abs((pixel ushr shift and 255)-(TrailPaint.FOG_COLOR ushr shift and 255))<=1
        }

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
        //    veiled at the selected theme strength while geographical context remains visible.
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
        check(isVeil(unknown), "unknown ground lost the selected themed veil")
        check(veilAlpha in 1..200, "default fog hides all geographical context")

        // 2. Outside the painted band the veil must come back to full strength over a narrow ramp:
        //    narrow, because the exploration radius must remain unchanged, but a ramp all
        //    the same, because a hard cut is what a stair-stepped edge looks like on a phone.
        val outward = (0..40).map { step ->
            alpha(bitmap.getPixel(minOf(frame.widthPx - 1, midX + step * 3), midY))
        }
        check(outward.last() == veilAlpha, "the veil never comes back to full strength: $outward")
        val opaqueAt = outward.indexOfFirst { it == veilAlpha }
        check(opaqueAt in 1..30, "the corridor edge is not a narrow ramp (opaque at sample $opaqueAt): $outward")
        check(outward.take(opaqueAt + 1).zipWithNext().all { (a, b) -> b >= a },
            "the veil does not come back steadily outside the paint: $outward")

        // 3. The frame border keeps the theme strength on every side. It is not faded any more: the frame is wider
        //    than the view, so its border must simply never be on screen, and a translucent border
        //    at the wrong origin was what produced a visible rectangular seam when panning.
        val edgeY = frame.heightPx / 2
        val edgeX = frame.widthPx / 2
        check(alpha(bitmap.getPixel(0, edgeY)) == veilAlpha, "the left frame border is transparent")
        check(alpha(bitmap.getPixel(frame.widthPx - 1, edgeY)) == veilAlpha, "the right frame border is transparent")
        check(alpha(bitmap.getPixel(edgeX, 0)) == veilAlpha, "the top frame border is transparent")
        check(alpha(bitmap.getPixel(edgeX, frame.heightPx - 1)) == veilAlpha, "the bottom frame border is transparent")

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
            if (was >= veilAlpha - 8) return@forEach
            val x = index * 2
            val pixel = crossed.getPixel(x, rowY)
            // Tint and veil are now both coloured; compare the chosen veil hue, allowing
            // premultiplied-alpha rounding at a faint feather edge.
            val veilHue = listOf(0,8,16).all { shift ->
                kotlin.math.abs((pixel ushr shift and 255)-(TrailPaint.FOG_COLOR ushr shift and 255))<=8
            }
            val increase = if (veilHue) after[index] - was else 0
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
        check(!TrailPaint.corridorVisible(startupFrame), "startup frame unexpectedly resolves a 100m corridor")
        check(isVeil(startup.getPixel(startupFrame.pixelX(BASE_LON).toInt(), startupFrame.pixelY(BASE_LAT).toInt())),
            "subpixel startup corridor erased the unknown map")
        val wide = TrailPainter().update(listOf(street), PIGMENT, null, wideView)!!
        check(isVeil(wide.getPixel(wide.width / 2, wide.height / 2)),
            "wide viewport widened a subpixel corridor")

        // 10. An empty selection must not inherit the previous selection's cleared corridor.
        val empty = painter.update(emptyList(), PIGMENT, null, view)!!
        check(isVeil(empty.getPixel(midX, midY)),
            "empty paths kept a previously explored corridor")

        // A stored accepted observation reveals only its footprint, without a fabricated journey.
        for(run in listOf(listOf(point(0.0,0.0)),listOf(point(0.0,0.0),point(0.0,0.0)))) {
            val isolated=TrailPainter()
            val image=isolated.update(listOf(run),PIGMENT,run.first(),view)
            val area=isolated.frame()
            check(image!=null && area!=null,"isolated observation did not produce a covering frame")
            if(image!=null && area!=null) {
                val pixel=image.getPixel(area.pixelX(BASE_LON).toInt(),area.pixelY(BASE_LAT).toInt())
                check(alpha(pixel)<veilAlpha && rgb(pixel)!=0,"accepted first fix stayed hidden under the veil")
                for(bearing in 0..7) {
                    val angle=bearing*Math.PI/4
                    val x=area.pixelX(BASE_LON+35.0*kotlin.math.cos(angle)/(111195.0*kotlin.math.cos(Math.toRadians(BASE_LAT)))).toInt()
                    val y=area.pixelY(BASE_LAT+35.0*kotlin.math.sin(angle)/111195.0).toInt()
                    check(alpha(image.getPixel(x,y))<veilAlpha,"35m circle remained foggy at bearing $bearing")
                }
                val outsideX=area.pixelX(BASE_LON+75.0/(111195.0*kotlin.math.cos(Math.toRadians(BASE_LAT)))).toInt().coerceIn(0,image.width-1)
                check(isVeil(image.getPixel(outsideX,area.pixelY(BASE_LAT).toInt())),"first fix revealed ground outside its real radius")
            }
        }
        val themed=TrailPainter()
        themed.update(emptyList(),PIGMENT,null,view)
        val revision=themed.revision
        val dusk=themed.update(emptyList(),PIGMENT,null,view,FogAppearance.color(VisualSkin.DUSK))!!
        check(themed.revision>revision && alpha(dusk.getPixel(0,0))==veilAlpha,"theme-only change did not repaint")
        val restored=themed.update(emptyList(),PIGMENT,null,view)!!
        check(isVeil(restored.getPixel(0,0)),"reused buffer compounded translucent veil")
        return if (failures.isEmpty()) "FOG_PIXEL_PROBE_PASS"
        else "FOG_PIXEL_PROBE_FAIL: " + failures.joinToString(" | ")
    }
}
