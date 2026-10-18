package cn.walkpaint

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.os.SystemClock
import java.time.LocalDate

/** Device-local progress; no accounts, uploads, permissions or background timers. */
class GrowthStore(context:Context) {
    private val app=context.applicationContext
    private val prefs=app.getSharedPreferences("growth_v1",0)
    val firstInstall:Long get()=runCatching{app.packageManager.getPackageInfo(app.packageName,0).firstInstallTime}.getOrDefault(System.currentTimeMillis())
    val days:Set<LocalDate> get()=prefs.getStringSet("days",emptySet()).orEmpty().mapNotNull{runCatching{LocalDate.parse(it)}.getOrNull()}.toSet()
    val seconds get()=prefs.getLong("seconds",0)
    val hidden get()=prefs.getBoolean("hide_recents",false)
    fun visit(today:LocalDate=LocalDate.now()) {prefs.edit().putStringSet("days",(days+today).map{it.toString()}.toSet()).apply()}
    fun addSeconds(value:Long){if(value>0)prefs.edit().putLong("seconds",seconds+value).apply()}
    fun goal(default:Int)=prefs.getInt("goal",default).coerceAtLeast(1)
    fun goal(value:Int,save:Boolean){if(save)prefs.edit().putInt("goal",value).apply()}
    fun earned():Map<String,Long> = prefs.all.filterKeys{it.startsWith("badge_")}.mapNotNull{(k,v)->(v as? Long)?.let{k.removePrefix("badge_") to it}}.toMap()
    fun settle(badges:List<GrowthBadge>):Map<String,Long> {
        val old=earned();val next=GrowthRules.unlocked(old,badges,System.currentTimeMillis())
        if(next!=old){val edit=prefs.edit();next.forEach{(id,at)->if(id !in old)edit.putLong("badge_$id",at)};edit.apply()}
        return next
    }
    fun hideRecents(value:Boolean){prefs.edit().putBoolean("hide_recents",value).apply();applyRecents()}
    fun applyRecents(){runCatching{app.getSystemService(ActivityManager::class.java).appTasks.forEach{it.setExcludeFromRecents(hidden)}}}
}

object GrowthSession {
    private var resumedAt:Long?=null
    private var store:GrowthStore?=null
    private val handler=android.os.Handler(android.os.Looper.getMainLooper())
    private val tick=object:Runnable {override fun run(){checkpoint();if(resumedAt!=null)handler.postDelayed(this,60_000)}}
    fun resume(activity:Activity){store=GrowthStore(activity);store!!.visit();store!!.applyRecents();resumedAt=SystemClock.elapsedRealtime();handler.removeCallbacks(tick);handler.postDelayed(tick,60_000)}
    private fun checkpoint(){resumedAt?.let{val now=SystemClock.elapsedRealtime();store?.addSeconds(((now-it)/1000).coerceAtLeast(0));store?.visit();resumedAt=now}}
    fun pause(){checkpoint();resumedAt=null;handler.removeCallbacks(tick)}
}
