package cn.walkpaint

import android.app.Instrumentation
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.view.FrameMetrics
import android.view.Window
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.runtime.State
import org.json.JSONArray
import org.json.JSONObject

/** App-window frame timings, not a claim about every native GL frame or a physical phone. */
internal object MapFrameProbe {
    fun run(i:Instrumentation):String=MapJourneyFixture.withFixture(i) {activity ->
        fun field(obj:Any,name:String)=obj.javaClass.getDeclaredField(name).apply {isAccessible=true}.get(obj)
        fun <T> main(body:()->T):T {var result:Result<T>?=null;i.runOnMainSync {result=runCatching(body)};return result!!.getOrThrow()}
        val controller=main {(field(activity,"amapController\$delegate") as Lazy<*>).value as AmapMapController}
        val end=SystemClock.elapsedRealtime()+20_000
        while(!main {field(controller,"mapLoaded")==true} && SystemClock.elapsedRealtime()<end) Thread.sleep(100)
        check(main {field(controller,"mapLoaded")==true}) {"Actual AMap did not load"}
        val firstView=main {controller.view()}
        val thread=HandlerThread("WalkPaintFrameProbe").apply {start()}
        val durations=mutableListOf<Long>();var dropped=0
        val listener=Window.OnFrameMetricsAvailableListener {_,metrics,lost ->
            val total=metrics.getMetric(FrameMetrics.TOTAL_DURATION)
            synchronized(durations) {if(total>=0) durations+=total;dropped+=lost}
        }
        main {activity.window.addOnFrameMetricsAvailableListener(listener,Handler(thread.looper))}
        fun tap(label:String,bottom:Boolean=false) {
            fun find(n:AccessibilityNodeInfo):AccessibilityNodeInfo? {
                val b=android.graphics.Rect();n.getBoundsInScreen(b)
                if((n.text?.toString()==label || n.contentDescription?.toString()==label) &&
                    (!bottom || b.top>i.targetContext.resources.displayMetrics.heightPixels*.75)) return n
                for(k in 0 until n.childCount) n.getChild(k)?.let {find(it)?.let {return it}}
                return null
            }
            val deadline=SystemClock.elapsedRealtime()+10_000
            while(SystemClock.elapsedRealtime()<deadline) {
                var node=i.uiAutomation.rootInActiveWindow?.let {find(it)}
                if(node!=null) {
                    while(node!=null && !node.isClickable) node=node.parent
                    check(node?.performAction(AccessibilityNodeInfo.ACTION_CLICK)==true)
                    Thread.sleep(400);return
                }
                Thread.sleep(100)
            }
            error("Missing control: $label")
        }
        val phases=JSONArray()
        fun measure(name:String,body:()->Unit) {
            synchronized(durations) {durations.clear();dropped=0}
            val start=SystemClock.elapsedRealtime();body();Thread.sleep(250)
            val samples=synchronized(durations) {durations.sorted() to dropped}
            val values=samples.first
            check(values.isNotEmpty()) {"No app-window frames for $name"}
            fun percentile(p:Double)=values[(kotlin.math.ceil(values.size*p).toInt()-1).coerceIn(values.indices)]/1e6
            phases.put(JSONObject().put("phase",name).put("frames",values.size)
                .put("p50_ms",percentile(.5)).put("p95_ms",percentile(.95)).put("p99_ms",percentile(.99))
                .put("over_16_67_ms",values.count {it>16_666_667}).put("dropped_metric_callbacks",samples.second)
                .put("journey_ms",SystemClock.elapsedRealtime()-start))
        }
        try {
            measure("tabs") {
                repeat(2) {tap("我的",true);tap("探索",true);tap("足迹",true);tap("探索",true)}
                check(main {controller.view()===firstView})
            }
            val data=main {(field(activity,"snapshot\$delegate") as State<*>).value as TrackState}
            val point=data.realTracks.last().points.last()
            main {controller.focus(point.lat,point.lon,17.0)};Thread.sleep(1000)
            measure("zoom") {
                for(delta in listOf(-1,-1,-1,-1,1,1,1,1)) {main {controller.zoomBy(delta)};Thread.sleep(80)}
                Thread.sleep(1000)
            }
            measure("day_review") {
                tap("足迹",true);tap(java.time.LocalDate.now().dayOfMonth.toString())
                tap("在地图查看这天的足迹");Thread.sleep(1500)
                check(main {(field(activity,"selectedDay\$delegate") as State<*>).value!=null})
                tap("返回全部探索")
                check(main {(field(activity,"selectedDay\$delegate") as State<*>).value==null})
            }
            "MAP_WINDOW_FRAMES: "+JSONObject().put("scope","Google AVD app window; native GL and phone FPS not certified")
                .put("fixture_points",9000).put("same_map_instance",true).put("phases",phases)
        } finally {
            main {activity.window.removeOnFrameMetricsAvailableListener(listener)}
            thread.quitSafely();thread.join(3000)
        }
    }
}
