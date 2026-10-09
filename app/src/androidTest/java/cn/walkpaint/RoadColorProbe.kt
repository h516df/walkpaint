package cn.walkpaint

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.app.ActivityOptions
import android.app.Instrumentation
import android.content.Intent
import android.os.SystemClock
import com.amap.api.maps.AMap
import com.amap.api.maps.model.Polyline
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.CircleLayer
import java.io.FileInputStream
import kotlin.math.abs

/** Actual SDK paint/camera probe at a fixed public location. Never writes road data. */
internal class RoadColorProbe(private val test:Instrumentation) {
    private fun main(block:()->Unit) {
        var failure:Throwable?=null
        test.runOnMainSync { try { block() } catch(e:Throwable) { failure=e } }
        failure?.let { throw it }
    }
    private fun shell(command:String):String=test.uiAutomation.executeShellCommand(command).use {
        FileInputStream(it.fileDescriptor).use { input -> input.readBytes().toString(Charsets.UTF_8) }
    }
    @Volatile private var resumedActivity:MainActivity?=null
    fun run():String {
        val application=test.targetContext.applicationContext as Application
        val observer=object:Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(a:Activity) { if(a is MainActivity) resumedActivity=a }
            override fun onActivityCreated(a:Activity,b:Bundle?) {}
            override fun onActivityStarted(a:Activity) {}
            override fun onActivityPaused(a:Activity) {}
            override fun onActivityStopped(a:Activity) {}
            override fun onActivitySaveInstanceState(a:Activity,b:Bundle) {}
            override fun onActivityDestroyed(a:Activity) {}
        }
        application.registerActivityLifecycleCallbacks(observer)
        try { return runTracked() } finally { application.unregisterActivityLifecycleCallbacks(observer) }
    }
    private fun runTracked():String {
        var activity=test.startActivitySync(Intent(test.targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            ActivityOptions.makeBasic().setLaunchDisplayId(0).toBundle()) as MainActivity
        for(block in shell("am stack list").split("RootTask id=").drop(1)) {
            val task=Regex("""^(\d+)[^\n]*displayId=(\d+)""").find(block) ?: continue
            if(block.contains("cn.walkpaint/cn.walkpaint.MainActivity") && task.groupValues[2]!="0") shell("am display move-stack ${task.groupValues[1]} 0")
        }
        shell("am start --display 0 --activity-reorder-to-front -n cn.walkpaint/.MainActivity")
        fun getter(name:String):Any=MainActivity::class.java.getDeclaredMethod(name).apply { isAccessible=true }.invoke(activity)!!
        val choose=MainActivity::class.java.getDeclaredMethod("chooseRoadColor",Int::class.javaPrimitiveType).apply { isAccessible=true }
        var amap:AMap?=null;var libre:MapLibreMap?=null;var controller:AmapMapController?=null
        var lines:List<Polyline> = emptyList()
        var ready=false
        val deadline=SystemClock.elapsedRealtime()+20_000
        while(!ready && SystemClock.elapsedRealtime()<deadline) {
            main {
                activity=resumedActivity ?: activity
                if(getter("getAmapActive") as Boolean) {
                    controller=getter("getAmapController") as AmapMapController
                    amap=AmapMapController::class.java.getDeclaredField("map").apply { isAccessible=true }.get(controller) as? AMap
                    @Suppress("UNCHECKED_CAST")
                    val painted=AmapMapController::class.java.getDeclaredField("paintedLines").apply { isAccessible=true }.get(controller) as List<Polyline>
                    lines=painted.toList();ready=amap!=null && lines.isNotEmpty() && controller!!.view()!!.width>0
                } else {
                    libre=MainActivity::class.java.getDeclaredField("mapInstance").apply { isAccessible=true }.get(activity) as? MapLibreMap
                    ready=libre?.style?.getLayerAs<LineLayer>("wp-painted-line")!=null && (getter("getRoadState") as RoadState).meters>0
                }
            }
            Thread.sleep(200)
        }
        check(ready) { "confirmed road SDK not ready lifecycle=${activity.lifecycle.currentState} display=${activity.display?.displayId} lines=${lines.size}" }
        fun camera():List<Double> = amap?.cameraPosition?.let { listOf(it.zoom.toDouble(),it.bearing.toDouble(),it.target.latitude,it.target.longitude) }
            ?: libre!!.cameraPosition.let { listOf(it.zoom,it.bearing,it.target!!.latitude,it.target!!.longitude) }
        val original=RoadAppearance(test.targetContext).color
        var before:List<Double> = emptyList()
        val follow=MainActivity::class.java.getDeclaredField("follow").apply { isAccessible=true }.get(activity) as MapFollowState
        try {
            main {
                MainActivity::class.java.getDeclaredMethod("pauseMapFollowing").apply { isAccessible=true }.invoke(activity)
                if(amap!=null) amap!!.moveCamera(com.amap.api.maps.CameraUpdateFactory.newCameraPosition(
                    com.amap.api.maps.model.CameraPosition.builder().target(com.amap.api.maps.model.LatLng(51.7529,-1.25507)).zoom(16.25f).bearing(35f).build()))
                else libre!!.moveCamera(org.maplibre.android.camera.CameraUpdateFactory.newCameraPosition(
                    org.maplibre.android.camera.CameraPosition.Builder().target(org.maplibre.android.geometry.LatLng(51.7529,-1.25507)).zoom(16.25).bearing(35.0).build()))
            }
            val cameraDeadline=SystemClock.elapsedRealtime()+5000
            do { Thread.sleep(150);main { before=camera() } }
            while((abs(before[0]-16.25)>.02 || abs(before[1]-35)>.05) && SystemClock.elapsedRealtime()<cameraDeadline)
            check(abs(before[0]-16.25)<.02 && abs(before[1]-35)<.05) { "public camera test setup not ready" }
            val mode=follow.mode
            var base=0;var blue=0
            main { libre?.style?.let { base=it.getLayerAs<LineLayer>("wp-road-base")!!.lineColorAsInt;blue=it.getLayerAs<CircleLayer>("wp-user-dot")!!.circleColorAsInt } }
            for(color in RoadPaintColor.presets.map { it.argb }+0xFFB56F77.toInt()) {
                main { choose.invoke(activity,color) };Thread.sleep(200)
                main {
                    if(amap!=null) check(lines.all { it.color==color }) { "AMap confirmed line color mismatch" }
                    else libre!!.style!!.let {
                        check(it.getLayerAs<LineLayer>("wp-painted-line")!!.lineColorAsInt==color)
                        check(it.getLayerAs<LineLayer>("wp-road-base")!!.lineColorAsInt==base)
                        check(it.getLayerAs<CircleLayer>("wp-user-dot")!!.circleColorAsInt==blue)
                    }
                    val after=camera()
                    check(after.zip(before).withIndex().all { (i,pair) -> abs(pair.first-pair.second)<if(i<2) .02 else 1e-7 }) { "color changed camera" }
                    check(follow.mode==mode) { "color changed follow mode" }
                    check(RoadAppearance(test.targetContext).color==color) { "chosen color not saved" }
                }
            }
            main { follow.locate();choose.invoke(activity,original);check(follow.following && follow.mode==MapFollowState.Mode.FOLLOWING) }
        } finally { main { choose.invoke(activity,original) } }
        return "ROAD_COLOR_SDK_PASS renderer="+(if(amap!=null) "AMap" else "MapLibre")+" selections=6 camera_and_follow_preserved"
    }
}
