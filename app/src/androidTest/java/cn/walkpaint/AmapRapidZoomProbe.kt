package cn.walkpaint

import android.app.Instrumentation
import android.content.Intent
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import org.json.JSONObject

/** Exercises the real SDK with the same callback as the visible +/- controls.
 * No fixture data is inserted. Camera and interaction mode are restored on exit.
 */
internal object AmapRapidZoomProbe {
    fun run(i: Instrumentation): String {
        val activity=UiEntry.main(i)
        fun field(obj:Any,name:String)=obj.javaClass.getDeclaredField(name).apply { isAccessible=true }.get(obj)
        fun <T> main(block:()->T):T {
            var result:Result<T>?=null
            i.runOnMainSync { result=runCatching(block) };return result!!.getOrThrow()
        }
        val controller=main { (field(activity,"amapController\$delegate") as Lazy<*>).value as AmapMapController }
        var ready=false
        repeat(60) {
            if(!ready) {
                ready=main { field(controller,"mapLoaded")==true && field(controller,"initialFocusPending")==false }
                if(!ready) Thread.sleep(250)
            }
        }
        check(ready) { "Actual AMap camera not ready" }
        val sdk=main { field(controller,"map") as AMap }
        val original=main { sdk.cameraPosition }
        val follow=main { field(activity,"follow") as MapFollowState }
        val originalMode=main { follow.mode }
        val pause=activity.javaClass.getDeclaredMethod("pauseMapFollowing").apply { isAccessible=true }
        fun shot(name:String) {
            val image=i.uiAutomation.takeScreenshot() ?: error("Actual screenshot unavailable")
            try { java.io.File(i.targetContext.getExternalFilesDir(null),"v045-rapid-$name.png")
                .outputStream().use { check(image.compress(android.graphics.Bitmap.CompressFormat.PNG,90,it)) } }
            finally { image.recycle() }
        }
        try {
            main { pause.invoke(activity);controller.focus(39.9075,116.3913,17.0) }
            Thread.sleep(1800)
            val startingCentre=main { sdk.cameraPosition.target }
            repeat(4) {
                main { pause.invoke(activity);controller.zoomBy(-1) }
                Thread.sleep(45)
            }
            Thread.sleep(1000)
            val out=main { sdk.cameraPosition.zoom }
            shot("out")
            repeat(4) {
                main { pause.invoke(activity);controller.zoomBy(1) }
                Thread.sleep(45)
            }
            Thread.sleep(1000)
            val back=main { sdk.cameraPosition.zoom }
            shot("in")
            val evidence=JSONObject().put("start_zoom",17).put("four_out_zoom",out)
                .put("four_in_zoom",back).put("gap_ms",45).put("actual_sdk",true)
            check(kotlin.math.abs(out-13f)<.1f) { "Lost rapid zoom-out increments: $evidence" }
            check(kotlin.math.abs(back-17f)<.1f) { "Rapid reverse zoom did not restore view: $evidence" }
            for(delta in listOf(-1,-1,1,-1,1,1)) {
                main { pause.invoke(activity);controller.zoomBy(delta) };Thread.sleep(45)
            }
            Thread.sleep(1000)
            val mixed=main { sdk.cameraPosition.zoom }
            shot("mixed")
            evidence.put("rapid_mixed_zoom",mixed)
            check(kotlin.math.abs(mixed-17f)<.1f) { "Direction changes during animation drifted: $evidence" }
            main { controller.cancelFollowingAnimation();sdk.moveCamera(CameraUpdateFactory.newCameraPosition(
                com.amap.api.maps.model.CameraPosition.builder(sdk.cameraPosition).zoom(sdk.maxZoomLevel).build())) }
            Thread.sleep(1000)
            repeat(3) { main { pause.invoke(activity);controller.zoomBy(1) };Thread.sleep(45) }
            main { pause.invoke(activity);controller.zoomBy(-1) }
            Thread.sleep(1000)
            val limitReturn=main { sdk.cameraPosition.zoom }
            val max=main { sdk.maxZoomLevel }
            evidence.put("max_zoom",max).put("one_out_after_max",limitReturn)
            check(kotlin.math.abs(limitReturn-(max-1f))<.1f) { "Zoom limit stored invisible extra steps: $evidence" }
            val centre=main { sdk.cameraPosition.target }
            val centreShift=kotlin.math.hypot((centre.latitude-startingCentre.latitude)*111194.9,
                (centre.longitude-startingCentre.longitude)*111194.9*kotlin.math.cos(Math.toRadians(centre.latitude)))
            evidence.put("centre_shift_metres",centreShift)
            check(centreShift<1) { "Zoom moved the map centre: $evidence" }
            return "AMAP_RAPID_ZOOM_PASS: $evidence"
        } finally { main {
            controller.cancelFollowingAnimation()
            when(originalMode) {
                MapFollowState.Mode.FOLLOWING -> follow.locate()
                MapFollowState.Mode.BROWSING -> { follow.locate();follow.gesture() }
                MapFollowState.Mode.HISTORY -> follow.history()
            }
            sdk.moveCamera(CameraUpdateFactory.newCameraPosition(original))
        } }
    }
}
