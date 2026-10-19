package cn.walkpaint

import android.app.Instrumentation
import android.app.ActivityOptions
import java.io.FileInputStream
import android.content.Intent
import android.location.Location
import android.os.SystemClock
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory

/** Test-only SDK camera inputs at fixed public Oxford. No recording calls or real-GPS lookup. */
internal class CameraFollowProbe(private val test:Instrumentation) {
    private fun main(block:()->Unit) {
        var failure:Throwable?=null
        test.runOnMainSync { try { block() } catch(e:Throwable) { failure=e } }
        failure?.let { throw it }
    }
    private fun shell(command:String):String = test.uiAutomation.executeShellCommand(command).use {
        FileInputStream(it.fileDescriptor).use { stream -> stream.readBytes().toString(Charsets.UTF_8) }
    }
    fun run() {
        val activity=test.startActivitySync(Intent(test.targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),ActivityOptions.makeBasic().setLaunchDisplayId(0).toBundle()) as MainActivity
        // MuMu overrides launch options with a separate app display. Use the same
        // existing main-display path as the production UI acceptance helper.
        for(block in shell("am stack list").split("RootTask id=").drop(1)) {
            val task=Regex("""^(\d+)[^\n]*displayId=(\d+)""").find(block) ?: continue
            if(block.contains("cn.walkpaint/cn.walkpaint.MainActivity") && task.groupValues[2]!="0")
                shell("am display move-stack ${task.groupValues[1]} 0")
        }
        shell("am start --display 0 --activity-reorder-to-front -n cn.walkpaint/.MainActivity")
        Thread.sleep(1000)
        var controller:AmapMapController?=null
        var sensor:HeadingSensor?=null
        main {
            fun privateGetter(name:String):Any=MainActivity::class.java.getDeclaredMethod(name).apply { isAccessible=true }.invoke(activity)!!
            controller=privateGetter("getAmapController") as AmapMapController
            sensor=privateGetter("getHeadingSensor") as HeadingSensor
        }
        var map:AMap?=null
        val deadline=SystemClock.elapsedRealtime()+15_000
        while(SystemClock.elapsedRealtime()<deadline && map==null) {
            main {
                val field=AmapMapController::class.java.getDeclaredField("map").apply { isAccessible=true }
                val candidate=field.get(controller) as? AMap
                val loaded=AmapMapController::class.java.getDeclaredField("mapLoaded").apply {isAccessible=true}.getBoolean(controller)
                val initial=AmapMapController::class.java.getDeclaredField("initialFocusPending").apply {isAccessible=true}.getBoolean(controller)
                if(loaded && !initial && candidate?.projection!=null && controller!!.view()!!.width>0) map=candidate
            }
            Thread.sleep(200)
        }
        check(map!=null) { "SDK camera not ready" }
        val fix=Location("gps").apply {
            latitude=51.75353;longitude=-1.25507;accuracy=5f
            time=System.currentTimeMillis();elapsedRealtimeNanos=SystemClock.elapsedRealtimeNanos()
        }
        val wasListening=sensor!!.listening
        try {
            var registered=false
            main {
                val update=MainActivity::class.java.getDeclaredMethod("updatePosition",Location::class.java,Boolean::class.javaPrimitiveType,Boolean::class.javaPrimitiveType).apply { isAccessible=true }
                update.invoke(activity,fix,true,false)
                registered=sensor!!.listening
                val loaded=AmapMapController::class.java.getDeclaredField("mapLoaded").apply {isAccessible=true}
                val previous=loaded.getBoolean(controller)
                val last=MainActivity::class.java.getDeclaredField("lastCameraPoint").apply {isAccessible=true}
                val sync=MainActivity::class.java.getDeclaredMethod("syncFollowCamera",Boolean::class.javaPrimitiveType,Boolean::class.javaPrimitiveType).apply {isAccessible=true}
                val follow=MainActivity::class.java.getDeclaredField("follow").apply {isAccessible=true}.get(activity) as MapFollowState
                val visible=follow.visible
                try {
                    follow.visible=true;follow.locate();last.set(activity,null)
                    loaded.setBoolean(controller,false)
                    sync.invoke(activity,true,true)
                    check(last.get(activity)==null) {"Unready camera request was acknowledged"}
                    loaded.setBoolean(controller,true)
                    follow.gesture();sync.invoke(activity,true,false)
                    check(last.get(activity)==null) {"Ready retry overrode manual browsing"}
                    follow.locate();follow.visible=false;sync.invoke(activity,true,false)
                    check(last.get(activity)==null) {"Ready retry moved background map"}
                    follow.visible=true;sync.invoke(activity,true,false)
                    check(last.get(activity)!=null) {"Ready follow retry was not delivered"}
                } finally { loaded.setBoolean(controller,previous);follow.visible=visible }
            }
            shell("input -d 0 keyevent KEYCODE_HOME");Thread.sleep(1500)
            main { check(!sensor!!.listening) { "background retained heading listener lifecycle=${activity.lifecycle.currentState} display=${activity.display?.displayId}" } }
            shell("am start --display 0 --activity-reorder-to-front -n cn.walkpaint/.MainActivity");Thread.sleep(1200)
            main {
                check(sensor!!.listening==registered) { "visible-map listener failed to restore" }
                val pause=MainActivity::class.java.getDeclaredMethod("pauseMapFollowing").apply { isAccessible=true }
                pause.invoke(activity)
                check(!sensor!!.listening) { "browsing retained heading listener" }
                map!!.moveCamera(CameraUpdateFactory.zoomTo(16.25f))
            }
            var zoom=Float.NaN
            val zoomDeadline=SystemClock.elapsedRealtime()+5000
            do {
                Thread.sleep(150)
                main { zoom=map!!.cameraPosition.zoom }
            } while(kotlin.math.abs(zoom-16.25f)>=.02f && SystemClock.elapsedRealtime()<zoomDeadline)
            check(kotlin.math.abs(zoom-16.25f)<.02f) { "initial zoom not ready actual=$zoom" }
            for(bearing in listOf(0f,90f,180f,359f,1f)) {
                main { controller!!.followCamera(fix,bearing,false) }
                var actual=Float.NaN
                val until=SystemClock.elapsedRealtime()+3000
                do {
                    Thread.sleep(150)
                    main { actual=map!!.cameraPosition.bearing;zoom=map!!.cameraPosition.zoom }
                } while((kotlin.math.abs(HeadingFilter.delta(actual,bearing))>=1f || kotlin.math.abs(zoom-16.25f)>=.02f) && SystemClock.elapsedRealtime()<until)
                main {
                    val camera=map!!.cameraPosition
                    check(kotlin.math.abs(HeadingFilter.delta(camera.bearing,bearing))<1f) { "bearing mismatch expected=$bearing actual=${camera.bearing}" }
                    check(kotlin.math.abs(camera.zoom-16.25f)<.02f) { "follow reset user zoom expected=16.25 actual=${camera.zoom}" }
                }
            }
        } finally { main { sensor!!.setActive(wasListening) } }
    }
}
