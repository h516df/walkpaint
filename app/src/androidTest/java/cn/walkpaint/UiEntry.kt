package cn.walkpaint

import android.app.Activity
import android.app.Application
import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Bounded startup also works after singleTop returns from a system settings activity. */
internal object UiEntry {
    fun main(i:Instrumentation):MainActivity {
        val ready=CountDownLatch(1)
        var activity:MainActivity?=null
        val app=i.targetContext.applicationContext as Application
        val callback=object:Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(a:Activity) {if(a is MainActivity) {activity=a;ready.countDown()}}
            override fun onActivityCreated(a:Activity,b:Bundle?) {}
            override fun onActivityStarted(a:Activity) {}
            override fun onActivityPaused(a:Activity) {}
            override fun onActivityStopped(a:Activity) {}
            override fun onActivitySaveInstanceState(a:Activity,b:Bundle) {}
            override fun onActivityDestroyed(a:Activity) {}
        }
        try {
            i.runOnMainSync {
                app.registerActivityLifecycleCallbacks(callback)
                i.targetContext.startActivity(Intent(i.targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            check(ready.await(20,TimeUnit.SECONDS)) {"MainActivity did not resume within 20 seconds"}
            return activity!!
        } finally {i.runOnMainSync {app.unregisterActivityLifecycleCallbacks(callback)}}
    }
}
