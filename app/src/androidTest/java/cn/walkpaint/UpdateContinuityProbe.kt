package cn.walkpaint

import android.app.Instrumentation
import android.content.Intent

/** Dedicated developer AVD only; no network request, credential, APK or real update is staged. */
internal object UpdateContinuityProbe {
    fun run(i:Instrumentation):String {
        check(BuildConfig.APPLICATION_ID=="cn.walkpaint.dev") {"Developer test package only"}
        fun main(block:()->Unit) {
            var failure:Throwable?=null
            i.runOnMainSync {try {block()} catch(t:Throwable){failure=t}}
            failure?.let {throw it}
        }
        val intent=Intent(i.targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val activity=i.startActivitySync(intent) as MainActivity
        fun updater(a:MainActivity)=MainActivity::class.java.getDeclaredMethod("getPrivateUpdater")
            .apply {isAccessible=true}.invoke(a) as PrivateUpdater
        var before:PrivateUpdater?=null
        main {before=updater(activity);check(!before!!.snapshot().busy);before!!.notice("本地生命周期合成检查")}
        val monitor=i.addMonitor(MainActivity::class.java.name,null,false)
        try {
            main {activity.recreate()}
            val recreated=monitor.waitForActivityWithTimeout(60000) as? MainActivity ?: error("Activity recreation timed out")
            check(recreated!==activity)
            main {
                check(updater(recreated)===before) {"Updater was replaced across recreation"}
                check(before!!.snapshot().message=="本地生命周期合成检查")
                before!!.notice("重建后观察者已更新")
                val state=MainActivity::class.java.getDeclaredMethod("getPrivateUpdateState").apply {isAccessible=true}
                    .invoke(recreated) as PrivateUpdateState
                check(state.message=="重建后观察者已更新")
                recreated.finish()
            }
            return "UPDATE_CONTINUITY_PASS: same updater and rebound state across Activity recreation; no network/download/install claimed"
        } finally {i.removeMonitor(monitor)}
    }
}
