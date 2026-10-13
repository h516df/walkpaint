package cn.walkpaint

import android.app.Instrumentation
import android.content.Intent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.runtime.State
import com.amap.api.maps.AMap
import com.amap.api.maps.CoordinateConverter
import com.amap.api.maps.model.LatLng
import org.json.JSONObject

/** Real date -> map path. Reports only alignment error, never saved coordinates. */
internal object HistoryCameraProbe {
    fun run(i: Instrumentation): String {
        val activity=i.startActivitySync(Intent(i.targetContext,MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        fun field(obj:Any,name:String)=obj.javaClass.getDeclaredField(name).apply {isAccessible=true}.get(obj)
        fun <T> main(block:()->T):T {
            var result:Result<T>?=null;i.runOnMainSync {result=runCatching(block)}
            return result!!.getOrThrow()
        }
        fun tap(label:String,bottom:Boolean=false,contains:Boolean=false) {
            repeat(12) {
                val root=i.uiAutomation.rootInActiveWindow
                fun find(node:AccessibilityNodeInfo):AccessibilityNodeInfo? {
                    val bounds=android.graphics.Rect();node.getBoundsInScreen(bounds)
                    val text=node.text?.toString() ?: node.contentDescription?.toString().orEmpty()
                    if((if(contains) text.contains(label) else text==label)
                        && (!bottom || bounds.top>i.targetContext.resources.displayMetrics.heightPixels*.75)) return node
                    for(n in 0 until node.childCount) node.getChild(n)?.let { child -> find(child)?.let { return it } }
                    return null
                }
                var node=root?.let {find(it)}
                if(node!=null) {
                    while(node!=null && !node.isClickable) node=node.parent
                    check(node?.performAction(AccessibilityNodeInfo.ACTION_CLICK)==true) {"Control not clickable: $label"}
                    Thread.sleep(1200);return
                }
                Thread.sleep(500)
            }
            error("Control not visible: $label")
        }
        Thread.sleep(4000)
        tap("足迹",true)
        tap(java.time.LocalDate.now().dayOfMonth.toString())
        tap("在地图查看这天的足迹")
        try {
            Thread.sleep(5000)
            return main {
                val day=(field(activity,"selectedDay\$delegate") as State<*>).value as? String
                check(day!=null) {"Date selection was lost"}
                val state=(field(activity,"mapTrackState\$delegate") as State<*>).value as TrackState
                val point=DailyFootprints.days(state).single {it.key==day}.focusTrack().points.last()
                val controller=(field(activity,"amapController\$delegate") as Lazy<*>).value as AmapMapController
                check(field(controller,"mapLoaded")==true)
                val map=field(controller,"map") as AMap
                val expected=CoordinateConverter(i.targetContext).from(CoordinateConverter.CoordType.GPS)
                    .coord(LatLng(point.lat,point.lon)).convert()
                val actual=map.cameraPosition.target
                val error=kotlin.math.hypot((actual.latitude-expected.latitude)*111194.9,
                    (actual.longitude-expected.longitude)*111194.9*kotlin.math.cos(Math.toRadians(expected.latitude)))
                val result=JSONObject().put("day_selected",true).put("same_day_data",true)
                    .put("centre_error_metres",error).put("zoom",map.cameraPosition.zoom)
                check(error<2) {"History camera restored the previous view: $result"}
                "HISTORY_CAMERA_PASS: $result"
            }
        } finally { tap("返回全部探索",contains=true) }
    }
}
