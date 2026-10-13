package cn.walkpaint

import android.app.Instrumentation
import android.content.Intent
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.CoordinateConverter
import com.amap.api.maps.model.GroundOverlay
import com.amap.api.maps.model.LatLng
import org.json.JSONObject

/** Independently reads SDK display coordinates/projection and actual native overlay bounds. */
internal object AmapAlignmentProbe {
    fun run(i: Instrumentation): String {
        val activity = i.startActivitySync(Intent(i.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        var controller: AmapMapController? = null
        var map: AMap? = null
        fun field(obj: Any, name: String): Any? = obj.javaClass.getDeclaredField(name).apply { isAccessible=true }.get(obj)
        fun <T> onMain(block: () -> T): T {
            var outcome: Result<T>? = null
            i.runOnMainSync { outcome = runCatching(block) }
            return outcome!!.getOrThrow()
        }
        var ready = false
        for (attempt in 0 until 40) {
            ready = onMain {
                controller = (field(activity, "amapController\$delegate") as Lazy<*>).value as AmapMapController
                map = field(controller!!, "map") as? AMap
                map != null && field(controller!!,"mapLoaded") == true &&
                    field(controller!!,"initialFocusPending") == false &&
                    (controller!!.view()?.width ?: 0) > 0
            }
            if (ready) break
            Thread.sleep(500)
        }
        check(ready) { "SDK load and initial focus did not finish before projection test" }
        val sdk = map!!
        var original: com.amap.api.maps.model.CameraPosition? = null
        var publicDisplay: LatLng? = null
        onMain {
            original = sdk.cameraPosition
            publicDisplay = CoordinateConverter(i.targetContext).from(CoordinateConverter.CoordType.GPS)
                .coord(LatLng(39.9075,116.3913)).convert()
            sdk.moveCamera(CameraUpdateFactory.newLatLngZoom(publicDisplay,17f))
        }
        try {
            Thread.sleep(2500)
            var result: JSONObject? = null
            onMain {
                fun forward(lat: Double, lon: Double) = CoordinateConverter(i.targetContext)
                    .from(CoordinateConverter.CoordType.GPS).coord(LatLng(lat,lon)).convert()
                fun metres(a: LatLng, b: LatLng) = kotlin.math.hypot((a.latitude-b.latitude)*111194.9,
                    (a.longitude-b.longitude)*111194.9*kotlin.math.cos(Math.toRadians(a.latitude)))
                val raw = sdk.cameraPosition.target
                val shiftedMetres = metres(raw, LatLng(39.9075,116.3913))
                check(shiftedMetres > 100) { "Public China point did not exercise coordinate shift" }
                check(metres(raw,publicDisplay!!) < 1) { "Raw SDK camera differs from public display point" }
                val pixel = sdk.projection.toScreenLocation(publicDisplay)
                val view = controller!!.view()!!
                val centrePixelError = kotlin.math.hypot(pixel.x-view.width/2.0,pixel.y-view.height/2.0)
                check(centrePixelError < 4) { "Converted public point is not at the real SDK screen centre" }
                val viewport = controller!!.viewport()!!
                val rawBounds = sdk.projection.visibleRegion.latLngBounds
                val viewportError = maxOf(metres(forward(viewport.latMin,viewport.lonMin),rawBounds.southwest),
                    metres(forward(viewport.latMax,viewport.lonMax),rawBounds.northeast))
                check(viewportError < 1) { "Viewport was converted zero or two times" }
                val frame = field(controller!!,"groundFrame") as PaintFrame
                val overlay = field(controller!!,"ground") as GroundOverlay
                val bounds = overlay.bounds
                val overlayError = maxOf(metres(forward(frame.latMin,frame.lonMin),bounds.southwest),
                    metres(forward(frame.latMax,frame.lonMax),bounds.northeast))
                check(overlayError < 1) { "Actual SDK ground overlay bounds have the wrong datum" }
                result = JSONObject().put("public_fixture","Beijing")
                    .put("sdk_coordinate_shift_metres",shiftedMetres).put("screen_centre_error_pixels",centrePixelError)
                    .put("viewport_display_error_metres",viewportError).put("native_overlay_error_metres",overlayError)
                    .put("raw_sdk_projection_checked",true).put("camera_restored",true)
            }
            return "AMAP_ALIGNMENT_PASS: $result"
        } finally {
            onMain { sdk.moveCamera(CameraUpdateFactory.newCameraPosition(original)) }
        }
    }
}
