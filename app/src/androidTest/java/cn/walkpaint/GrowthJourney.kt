package cn.walkpaint
import android.app.Instrumentation
import android.app.Activity
import android.graphics.Bitmap
import android.view.accessibility.AccessibilityNodeInfo
import android.os.SystemClock
import java.io.File
import java.time.LocalDate

/** Real controls, persisted goal and earned-badge identity, with task-owned anonymous state. */
object GrowthJourney {
 fun run(i:Instrumentation,activity:Activity?,title:String,navigation:String,goalLabel:String):String {
  val context=i.targetContext;val prefs=context.getSharedPreferences("growth_v1",0)
  val previous=prefs.all.toMap();val today=LocalDate.now()
  fun nodes():List<AccessibilityNodeInfo>{i.uiAutomation.clearCache();val all=mutableListOf<AccessibilityNodeInfo>();fun walk(n:AccessibilityNodeInfo?){if(n==null)return;if(n.isVisibleToUser)all+=n;repeat(n.childCount){walk(n.getChild(it))}};walk(i.uiAutomation.rootInActiveWindow);return all}
  fun find(text:String):AccessibilityNodeInfo? {val matches=nodes().filter{it.text?.toString()==text || it.contentDescription?.toString()==text};return matches.firstOrNull{it.isClickable} ?: matches.firstOrNull()}
  fun wait(text:String):AccessibilityNodeInfo{val until=SystemClock.uptimeMillis()+18000;while(SystemClock.uptimeMillis()<until){find("Got it")?.performAction(AccessibilityNodeInfo.ACTION_CLICK);find(text)?.let{return it};if(text=="本周小目标" || text=="最近 28 天" || text==goalLabel || text=="从最近任务中隐藏" || text=="查看成长记录" || text=="初来相伴") nodes().firstOrNull{it.isScrollable}?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);Thread.sleep(150)};error("Missing $text: "+nodes().mapNotNull{it.text}.take(25))}
  fun tap(text:String){var n:AccessibilityNodeInfo?=wait(text);while(n!=null && !n.isClickable)n=n.parent;check(n?.performAction(AccessibilityNodeInfo.ACTION_CLICK)==true);Thread.sleep(250)}
  try {
   prefs.edit().putStringSet("days",setOf(today.toString(),today.minusDays(1).toString(),today.minusDays(2).toString())).putInt("goal",7).commit()
   if(find(title)==null){if(find(navigation)==null && find("我的档案")!=null)tap("我的档案") else tap(navigation)}
   tap(title);wait("本周小目标");tap(goalLabel)
   check(prefs.getInt("goal",0)==3){"Weekly goal was not persisted"}
   // Visit metrics refresh when opening the page; awards do not fabricate historical dates.
   val deadline=SystemClock.uptimeMillis()+5000
   while(prefs.getLong("badge_visit_3",0)==0L && SystemClock.uptimeMillis()<deadline)Thread.sleep(100)
   val first=prefs.getLong("badge_visit_3",0);check(first>0){"Three-day achievement did not unlock"}
   val image=i.uiAutomation.takeScreenshot()!!
   File(context.getExternalFilesDir(null),"growth-dashboard.png").outputStream().use{image.compress(Bitmap.CompressFormat.PNG,100,it)};image.recycle()
   tap("返回");tap(title);wait("本周小目标");check(prefs.getInt("goal",0)==3)
   check(prefs.getLong("badge_visit_3",0)==first){"Reopening awarded the badge twice"}
   tap("说明");wait("成长说明");tap("知道了")
   tap("查看成长记录");wait("最近 28 天")
   tap("返回")
   tap("后台记录设置")
   val hidden=prefs.getBoolean("hide_recents",false)
   tap("从最近任务中隐藏")
   check(prefs.getBoolean("hide_recents",false)!=hidden)
   val tasks=context.getSystemService(android.app.ActivityManager::class.java).appTasks
   check(tasks.isNotEmpty())
   check(tasks.any{(it.taskInfo.baseIntent.flags and android.content.Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS!=0)==!hidden}){"Recent task flag was not applied"}
   tap("从最近任务中隐藏")
   check(prefs.getBoolean("hide_recents",false)==hidden)
   return "GROWTH_PASS: real page, goal persistence, earned badge and reopen identity"
  } finally {
   val edit=prefs.edit().clear();previous.forEach{(key,value)->when(value){is String->edit.putString(key,value);is Long->edit.putLong(key,value);is Int->edit.putInt(key,value);is Boolean->edit.putBoolean(key,value);is Float->edit.putFloat(key,value);is Set<*>->edit.putStringSet(key,value.filterIsInstance<String>().toSet())}};edit.commit();GrowthStore(context).applyRecents()
  }
 }
}
