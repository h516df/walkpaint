package cn.walkpaint

import android.app.Instrumentation
import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.runtime.State
import com.amap.api.maps.AMap
import com.amap.api.maps.CoordinateConverter
import com.amap.api.maps.model.LatLng
import org.json.JSONObject
import java.io.File

/** Checks saved geography during animation. Native route lines over a black wide-view bitmap
 * are known ground, so the bitmap's black pixels alone cannot classify unknown geography.
 * Use the entire fixture's northern boundary, at the largest zoom, and its fixed centre point.
 * Camera/GPU timing cannot move that point. Sampling adds overhead, not a FPS verdict. */
internal object MapSceneProbe {
    fun run(i:Instrumentation):String=MapJourneyFixture.withFixture(i) {activity ->
        fun field(obj:Any,name:String)=obj.javaClass.getDeclaredField(name).apply {isAccessible=true}.get(obj)
        fun <T> main(block:()->T):T {var result:Result<T>?=null;i.runOnMainSync {result=runCatching(block)};return result!!.getOrThrow()}
        val controller=main {(field(activity,"amapController\$delegate") as Lazy<*>).value as AmapMapController}
        val end=android.os.SystemClock.uptimeMillis()+20_000
        while(main {field(controller,"mapLoaded")!=true || (field(activity,"snapshot\$delegate") as State<*>).value.let {it as TrackState}.realPointCount!=9000}
            && android.os.SystemClock.uptimeMillis()<end) Thread.sleep(100)
        val sdk=main {field(controller,"map") as AMap}
        val original=main {sdk.cameraPosition}
        val pause=activity.javaClass.getDeclaredMethod("pauseMapFollowing").apply {isAccessible=true}
        val center=main {(field(activity,"snapshot\$delegate") as State<*>).value.let {it as TrackState}.realTracks.last().points[28]}
        main {pause.invoke(activity);controller.focus(center.lat,center.lon,17.0)}
        val expected=main {
            val p=CoordinateConverter(i.targetContext).from(CoordinateConverter.CoordType.GPS)
                .coord(LatLng(center.lat,center.lon)).convert()
            sdk.stopAnimation()
            sdk.moveCamera(com.amap.api.maps.CameraUpdateFactory.newCameraPosition(
                com.amap.api.maps.model.CameraPosition.builder().target(p).zoom(17f).bearing(0f).tilt(0f).build()))
            p
        }
        val cameraDeadline=android.os.SystemClock.uptimeMillis()+15_000
        while(main {sdk.cameraPosition.let {kotlin.math.abs(it.target.latitude-expected.latitude)>0.000001 ||
            kotlin.math.abs(it.target.longitude-expected.longitude)>0.000001 || kotlin.math.abs(it.zoom-17f)>.01f || it.bearing!=0f}}
            && android.os.SystemClock.uptimeMillis()<cameraDeadline) Thread.sleep(100)
        val fixed=main {
            fun forward(lat:Double,lon:Double)=CoordinateConverter(i.targetContext)
                .from(CoordinateConverter.CoordType.GPS).coord(LatLng(lat,lon)).convert()
            val state=(field(activity,"snapshot\$delegate") as State<*>).value as TrackState
            val north=state.realTracks.flatMap {it.points}.maxOf {it.lat}+50.0/111194.9
            val origin=IntArray(2);controller.view()!!.getLocationOnScreen(origin)
            val core=sdk.projection.toScreenLocation(forward(center.lat,center.lon))
            val border=sdk.projection.toScreenLocation(forward(north,center.lon))
            Triple(android.graphics.Point(core.x+origin[0],core.y+origin[1]),border.y+origin[1]-30,expected)
        }
        val samples=org.json.JSONArray()
        var lowest=1.0
        var shots=0
        fun sample() {
            val screenshot=i.uiAutomation.takeScreenshot() ?: error("No process screenshot")
            try {
                shots++
                File(i.targetContext.getExternalFilesDir(null),"v046-scene-$shots.png").outputStream().use {
                    check(screenshot.compress(Bitmap.CompressFormat.PNG,90,it))
                }
                val report=main {
                    var expectedUnknown=0;var opaqueUnknown=0
                    for(y in (screenshot.height*.16).toInt() until minOf(fixed.second,(screenshot.height*.77).toInt()) step 22)
                        for(x in (screenshot.width*.06).toInt() until (screenshot.width*.78).toInt() step 22) {
                            val p=screenshot.getPixel(x,y);expectedUnknown++
                            if(Color.red(p)<18 && Color.green(p)<18 && Color.blue(p)<18) opaqueUnknown++
                        }
                    check(expectedUnknown>200) {"No substantial provably unknown region"}
                    val fraction=opaqueUnknown.toDouble()/expectedUnknown
                    check(fraction>.98) {"Unknown geography flashed through: $opaqueUnknown/$expectedUnknown"}
                    var revealed=0
                    for(dx in -12..12) for(dy in -12..12) {
                        val p=screenshot.getPixel(fixed.first.x+dx,fixed.first.y+dy)
                        if(Color.red(p)>=18 || Color.green(p)>=18 || Color.blue(p)>=18) revealed++
                    }
                    check(revealed>=10) {"Saved point at the fixed camera centre flashed black: $revealed/625"}
                    val pose=sdk.cameraPosition
                    val shift=kotlin.math.hypot((pose.target.latitude-fixed.third.latitude)*111194.9,
                        (pose.target.longitude-fixed.third.longitude)*111194.9*kotlin.math.cos(Math.toRadians(fixed.third.latitude)))
                    check(shift<1 && pose.bearing==0f && pose.tilt==0f) {"Zoom changed fixed geography: shift=$shift bearing=${pose.bearing} tilt=${pose.tilt}"}
                    check((field(activity,"fogBitmap") as Bitmap).isMutable==false) {"SDK can read the painter's reusable buffer"}
                    lowest=minOf(lowest,fraction)
                    JSONObject().put("zoom",pose.zoom).put("unknown_sites",expectedUnknown)
                        .put("unknown_black_fraction",fraction).put("visible_core_pixels",revealed)
                }
                samples.put(report)
            } finally {screenshot.recycle()}
        }
        try {
            // Fixture construction and the SDK texture upload are asynchronous. Begin gestures
            // only after a real screenshot proves the anonymous street scene is being displayed.
            val sceneDeadline=android.os.SystemClock.uptimeMillis()+15_000
            var sceneReady=false
            while(!sceneReady) {
                try {sample();sceneReady=true}
                catch(e:IllegalStateException) {
                    check(android.os.SystemClock.uptimeMillis()<sceneDeadline) {"Street scene never rendered: ${e.message}"}
                    Thread.sleep(250)
                }
            }
            for(delta in listOf(-1,-1,-1,-1,1,1,1,1)) {
                main {controller.zoomBy(delta)}
                repeat(3) {Thread.sleep(80);sample()}
                Thread.sleep(220)
            }
            "MAP_SCENE_PASS: "+JSONObject().put("samples",samples).put("minimum_unknown_black",lowest)
                .put("fixture_points",9000).put("sampling_overhead",true)
        } finally {main {sdk.moveCamera(com.amap.api.maps.CameraUpdateFactory.newCameraPosition(original))}}
    }
}
