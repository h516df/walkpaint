package cn.walkpaint

import android.app.Instrumentation
import android.content.Intent
import android.os.SystemClock
import android.os.Handler
import android.os.Looper
import android.location.LocationManager

/** Exercises real queued service commands on the dedicated test AVD; never changes GPS rows. */
internal object RecordingRecoveryProbe {
    fun run(i: Instrumentation): String {
        val c=i.targetContext
        check(AmbientTracking.hasFineLocation(c)) { "test permission missing" }
        val original=AmbientTracking.enabled(c)
        try {
            i.runOnMainSync { AmbientTracking.setEnabled(c,true);AmbientTracking.start(c) }
            SystemClock.sleep(1200)
            check(AmbientTracking.running) { "initial start failed" }
            // Android receives STOP then ENABLE after this UI event returns. This is the same
            // ordering as quickly pausing and resuming from the app, without timing a shell tap.
            i.runOnMainSync {
                AmbientTracking.stop(c)
                AmbientTracking.setEnabled(c,true)
                AmbientTracking.start(c)
            }
            SystemClock.sleep(1800)
            check(AmbientTracking.enabled(c)) { "queued STOP overwrote the newer resume choice" }
            check(AmbientTracking.running) { "resume did not restart the foreground recorder" }
            val d=RecordingDiagnostics.snapshot(c)
            check((d["registration_at"] as? Number)?.toLong()?.let { it>0 } == true) { "GPS registration missing" }
            val activity=i.startActivitySync(Intent(c,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
            val handler=activity.javaClass.getDeclaredField("mainHandler").apply {isAccessible=true}.get(activity) as Handler
            val refresh=activity.javaClass.getDeclaredField("refreshTask").apply {isAccessible=true}.get(activity) as Runnable
            i.runOnMainSync { activity.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)) }
            SystemClock.sleep(1000)
            check(!handler.hasCallbacks(refresh)) { "UI poll survives real background transition" }
            check(AmbientTracking.running) { "background UI transition stopped recorder" }
            // Cancellation is exercised against Android's actual location manager, not a fake clock.
            val callbacks=java.util.concurrent.atomic.AtomicInteger()
            i.runOnMainSync {
                val probe=FreshGpsProbe(c.getSystemService(LocationManager::class.java),Handler(Looper.getMainLooper()))
                probe.start { _,_ -> callbacks.incrementAndGet() };probe.cancel()
            }
            SystemClock.sleep(16000)
            check(callbacks.get()==0) { "cancelled probe delivered late result or timeout" }
            return "RECORDING_RECOVERY_PASS: rapid pause/resume preserved choice; background UI poll stopped while service remained; native probe cancellation delivered no late result"
        } finally {
            i.runOnMainSync {
                AmbientTracking.stop(c)
            }
            SystemClock.sleep(600)
            AmbientTracking.setEnabled(c,original)
            if(original) i.runOnMainSync { AmbientTracking.start(c) }
        }
    }
}
