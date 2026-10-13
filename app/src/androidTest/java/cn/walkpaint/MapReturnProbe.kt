package cn.walkpaint

import android.app.Instrumentation
import android.content.Intent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.runtime.State
import org.json.JSONObject

/** Measures the real bottom-tab journey; no saved rows or settings are changed. */
internal object MapReturnProbe {
    fun run(i: Instrumentation): String = MapJourneyFixture.withFixture(i) {runJourney(i,it)}
    private fun runJourney(i: Instrumentation,activity:MainActivity): String {
        fun field(obj:Any,name:String)=obj.javaClass.getDeclaredField(name).apply {isAccessible=true}.get(obj)
        fun <T> main(block:()->T):T {
            var result:Result<T>?=null;i.runOnMainSync {result=runCatching(block)}
            return result!!.getOrThrow()
        }
        val controller=main {(field(activity,"amapController\$delegate") as Lazy<*>).value as AmapMapController}
        fun ready()=main {field(controller,"mapLoaded")==true && controller.view()!=null &&
            (field(activity,"currentTab\$delegate") as State<*>).value==0}
        fun awaitMap() { val end=android.os.SystemClock.uptimeMillis()+20_000
            while(!ready() && android.os.SystemClock.uptimeMillis()<end) Thread.sleep(50)
            check(ready()) {"AMap did not load"}
        }
        fun tap(label:String) {
            var root=i.uiAutomation.rootInActiveWindow
            val end=android.os.SystemClock.uptimeMillis()+10_000
            while(root==null && android.os.SystemClock.uptimeMillis()<end) {Thread.sleep(100);root=i.uiAutomation.rootInActiveWindow}
            check(root!=null) {"No active window"}
            fun find(n:AccessibilityNodeInfo):AccessibilityNodeInfo? {
                val b=android.graphics.Rect();n.getBoundsInScreen(b)
                if(n.text?.toString()==label && b.top>i.targetContext.resources.displayMetrics.heightPixels*.75) return n
                for(k in 0 until n.childCount) n.getChild(k)?.let { find(it)?.let { return it } }
                return null
            }
            var node=find(root!!) ?: error("Missing bottom tab $label")
            while(!node.isClickable) node=node.parent ?: error("No clickable parent")
            check(node.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        }
        awaitMap()
        val fixtureEnd=android.os.SystemClock.uptimeMillis()+10_000
        while(main {(field(activity,"snapshot\$delegate") as State<*>).value.let {it as TrackState}.realPointCount}!=7959
            && android.os.SystemClock.uptimeMillis()<fixtureEnd) Thread.sleep(100)
        val endpoint=main {(field(activity,"snapshot\$delegate") as State<*>).value.let {it as TrackState}.realTracks.last().points.last()}
        main {
            activity.javaClass.getDeclaredMethod("pauseMapFollowing").apply {isAccessible=true}.invoke(activity)
            controller.focus(endpoint.lat,endpoint.lon,17.0)
        }
        Thread.sleep(1500)
        val first=main {controller.view()}
        val results=org.json.JSONArray()
        for(label in listOf("我的","足迹","我的","足迹")) {
            tap(label);Thread.sleep(350)
            val existsHidden=main {controller.view()===first}
            val start=android.os.SystemClock.uptimeMillis();tap("探索");awaitMap()
            val same=main {controller.view()===first}
            results.put(JSONObject().put("from",label).put("same_map",same)
                .put("kept_while_hidden",existsHidden).put("return_ready_ms",android.os.SystemClock.uptimeMillis()-start))
            Thread.sleep(350)
        }
        val installed=i.targetContext.packageManager.getPackageInfo("cn.walkpaint",0).versionName
        if(installed!="0.45") for(index in 0 until results.length()) {
            check(results.getJSONObject(index).getBoolean("same_map")) {"Map recreated on bottom-tab return"}
            check(results.getJSONObject(index).getBoolean("kept_while_hidden")) {"Map was disposed while hidden"}
        }
        return "MAP_RETURN_RESULT: "+JSONObject().put("journeys",results)
            .put("installed_version",installed).put("fixture_points",7959).put("fixture_segments",140)
            .put("fixture_distance_metres",main {(field(activity,"snapshot\$delegate") as State<*>).value.let {it as TrackState}.totalMeters})
    }
}
