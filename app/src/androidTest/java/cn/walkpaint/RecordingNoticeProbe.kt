package cn.walkpaint

import android.app.Instrumentation
import android.app.NotificationManager
import android.content.Intent
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import org.json.JSONArray
import org.json.JSONObject

/** Real Android provider/service/notification test, restricted to the dedicated host-guarded AVD.
 * Public synthetic fixes append test rows; original rows are verified separately by the host.
 */
internal object RecordingNoticeProbe {
    @Suppress("DEPRECATION")
    fun run(i:Instrumentation):String {
        check(android.os.Build.PRODUCT=="sdk_gphone64_x86_64") {"Google AVD required"}
        val c=i.targetContext
        val lm=c.getSystemService(LocationManager::class.java)
        val nm=c.getSystemService(NotificationManager::class.java)
        val enabled=AmbientTracking.enabled(c)
        check(!enabled && !AmbientTracking.running) {"start from paused test state"}
        val stages=JSONArray()
        fun notification():Pair<String,String>?=nm.activeNotifications.firstOrNull {it.id==17}?.notification?.extras?.let {
            (it.getCharSequence("android.title")?.toString() ?: "") to
                (it.getCharSequence("android.text")?.toString() ?: "")
        }
        fun expect(stage:String,fragment:String,timeout:Long=5000):Pair<String,String> {
            val deadline=SystemClock.elapsedRealtime()+timeout
            var n=notification()
            while(n==null || !(n.first+" "+n.second).contains(fragment)) {
                check(SystemClock.elapsedRealtime()<deadline) {"$stage expected $fragment, observed $n"}
                SystemClock.sleep(100);n=notification()
            }
            stages.put(JSONObject().put("stage",stage).put("title",n.first).put("detail",n.second))
            return n
        }
        fun fix(lat:Double,accuracy:Float=8f,age:Long=0) {
            lm.setTestProviderLocation(LocationManager.GPS_PROVIDER,Location(LocationManager.GPS_PROVIDER).apply {
                latitude=lat;longitude=2.3522;this.accuracy=accuracy
                time=System.currentTimeMillis()-age
                elapsedRealtimeNanos=SystemClock.elapsedRealtimeNanos()-age*1_000_000
                speed=1.6f;speedAccuracyMetersPerSecond=.3f
            })
        }
        var mockAdded=false
        try {
            val activity=UiEntry.main(i)
            lm.addTestProvider(LocationManager.GPS_PROVIDER,false,true,false,false,true,true,true,
                Criteria.POWER_HIGH,Criteria.ACCURACY_FINE);mockAdded=true
            lm.setTestProviderEnabled(LocationManager.GPS_PROVIDER,false)
            i.runOnMainSync {AmbientTracking.setEnabled(c,true);AmbientTracking.start(c)}
            expect("disabled","手机定位已关闭")
            lm.setTestProviderEnabled(LocationManager.GPS_PROVIDER,true)
            expect("enabled_before_fix","等待首个 GPS")
            fix(48.8566,240f);expect("weak","定位信号较弱")
            SystemClock.sleep(2200)
            fix(48.8566);expect("first_valid","定位正常")
            // Walking-speed public fixture, delivered through Android's actual LocationManager.
            for(n in 1..9) {SystemClock.sleep(2200);fix(48.8566+n*.000035)}
            val saved=expect("saved","已保存",35_000)
            // Same-state changes may wait at most the original 30-second deadline.
            val deadline=SystemClock.elapsedRealtime()+35_000
            while(!Regex("已保存 [1-9][0-9]* 点").containsMatchIn(notification()?.second ?: "")) {
                check(SystemClock.elapsedRealtime()<deadline) {"no committed points in notification: ${notification()}"}
                SystemClock.sleep(200)
            }
            expect("saved_count","精度 8米")
            lm.setTestProviderEnabled(LocationManager.GPS_PROVIDER,false)
            expect("disabled_after_save","手机定位已关闭",3000)
            lm.setTestProviderEnabled(LocationManager.GPS_PROVIDER,true)
            expect("resume_waiting","等待首个 GPS",3000)
            fix(48.85695,8f,120_000);expect("cached_not_healthy","定位信号较弱")
            SystemClock.sleep(2200);fix(48.85695);expect("fresh_after_cached","定位正常")
            // Stop deliveries while the real foreground service stays in the background.
            // This distinguishes a live process from actual GPS receipts and exercises recovery.
            val before=RecordingDiagnostics.snapshot(c)
            val probesBefore=(before["session_probe_start_count"] as? Number)?.toLong() ?: 0
            val registeredBefore=(before["registration_at"] as? Number)?.toLong() ?: 0
            i.runOnMainSync {activity.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))}
            expect("background_stream_interrupted","收不到定位信号",80_000)
            val recoveryDeadline=SystemClock.elapsedRealtime()+100_000
            var d=RecordingDiagnostics.snapshot(c)
            while((d["registration_at"] as? Number)?.toLong()?.let {it>registeredBefore}!=true ||
                d["registration"]!="GPS_RETRY_REGISTERED") {
                check(SystemClock.elapsedRealtime()<recoveryDeadline) {"bounded stream recovery missing"}
                SystemClock.sleep(250);d=RecordingDiagnostics.snapshot(c)
            }
            check((d["session_probe_start_count"] as? Number)?.toLong()==probesBefore+1) {"probe not bounded"}
            check(AmbientTracking.running) {"background service stopped during recovery"}
            stages.put(JSONObject().put("stage","background_bounded_recovery").put("one_probe",true)
                .put("reregistered",true).put("service_running",true))
            fix(48.85695);expect("fresh_after_background_recovery","定位正常")
            i.runOnMainSync {AmbientTracking.stop(c)}
            val stopDeadline=SystemClock.elapsedRealtime()+3000
            while(AmbientTracking.running || notification()!=null) {
                check(SystemClock.elapsedRealtime()<stopDeadline) {"pause left a running recorder/notification"}
                SystemClock.sleep(100)
            }
            return "RECORDING_NOTICE_PASS: "+JSONObject().put("stages",stages).put("paused_no_notification",true)
        } finally {
            i.runOnMainSync {AmbientTracking.stop(c)}
            SystemClock.sleep(500)
            if(mockAdded) lm.removeTestProvider(LocationManager.GPS_PROVIDER)
            AmbientTracking.setEnabled(c,enabled)
        }
    }
}
