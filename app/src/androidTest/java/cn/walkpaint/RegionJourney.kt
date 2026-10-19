package cn.walkpaint

import android.app.Instrumentation
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import java.io.File
import org.json.JSONObject

internal object RegionJourney {
 fun run(i:Instrumentation):String=MapJourneyFixture.withFixture(i){activity->
  @Suppress("UNCHECKED_CAST")
  val fix=activity.javaClass.getDeclaredField("userFix\$delegate").apply{isAccessible=true}.get(activity) as androidx.compose.runtime.MutableState<android.location.Location?>
  var previous:android.location.Location?=null
  val previousGps=AmbientTracking.lastGps;val previousFix=AmbientTracking.lastFix
  val manager=activity.javaClass.getDeclaredField("locationManager").apply{isAccessible=true}.get(activity) as? android.location.LocationManager
  val listener=activity.javaClass.getDeclaredField("visibleLocationListener").apply{isAccessible=true}.get(activity) as android.location.LocationListener
  i.runOnMainSync {previous=fix.value;manager?.removeUpdates(listener);AmbientTracking.lastGps=null;AmbientTracking.lastFix=null;fix.value=null}
  try {
  fun nodes():List<AccessibilityNodeInfo>{i.uiAutomation.clearCache();val result=mutableListOf<AccessibilityNodeInfo>();fun visit(n:AccessibilityNodeInfo?){if(n==null)return;if(n.isVisibleToUser)result+=n;repeat(n.childCount){visit(n.getChild(it))}};visit(i.uiAutomation.rootInActiveWindow);return result}
  fun waitFor(test:(AccessibilityNodeInfo)->Boolean):AccessibilityNodeInfo{val end=SystemClock.elapsedRealtime()+25000;while(SystemClock.elapsedRealtime()<end){nodes().firstOrNull(test)?.let{return it};Thread.sleep(150)};error("Region UI not ready: "+nodes().mapNotNull{it.text}.take(20))}
  fun tap(n:AccessibilityNodeInfo){var node:AccessibilityNodeInfo?=n;while(node!=null && !node.isClickable)node=node.parent;check(node?.performAction(AccessibilityNodeInfo.ACTION_CLICK)==true);Thread.sleep(200)}
  tap(waitFor{it.text?.toString()=="探索"})
  var percent=waitFor{val text=it.text?.toString();text?.matches(Regex("[0-9]+\\.[0-9]+%"))==true && text.removeSuffix("%").toDouble()>0}.text.toString().removeSuffix("%").toDouble()
  check(percent>0 && percent<=100){"Saved trails did not contribute to exploration"}
  tap(waitFor{it.contentDescription?.toString()=="附近探索"})
  waitFor{it.text?.toString()=="寻光"}
  percent=waitFor{it.text?.toString()=="13.7%"}.text.toString().removeSuffix("%").toDouble()
  waitFor{it.text?.toString()?.startsWith("已点亮 ")==true}
  tap(waitFor{it.text?.toString()=="探索范围与说明"})
  waitFor{it.text?.toString()?.contains("25米网格")==true}
  tap(waitFor{it.text?.toString()=="收起探索说明"})
  i.waitForIdleSync();Thread.sleep(650)
  val screenshot=i.uiAutomation.takeScreenshot()!!
  File(i.targetContext.getExternalFilesDir(null),"region-exploration.png").outputStream().use{screenshot.compress(Bitmap.CompressFormat.PNG,100,it)};screenshot.recycle()
  val fetched=File(i.targetContext.cacheDir,"exploration-district.json").isFile
  i.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
  "REGION_PASS: "+JSONObject().put("nearbyPercent",percent).put("scopeMeters",2000).put("districtCacheAvailable",fetched).put("phoneVerified",false)
  }finally{i.runOnMainSync{AmbientTracking.lastGps=previousGps;AmbientTracking.lastFix=previousFix;fix.value=previous;activity.javaClass.getDeclaredMethod("beginVisibleLocation").apply{isAccessible=true}.invoke(activity)}}
 }
}
