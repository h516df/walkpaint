package cn.walkpaint

import android.app.Instrumentation
import android.content.Intent
import android.os.SystemClock

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
            return "RECORDING_RECOVERY_PASS: rapid pause/resume kept the latest user choice and restarted recording"
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
