package cn.walkpaint

import android.app.Instrumentation
import android.app.NotificationManager
import android.content.Intent
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock

/** Real service/SQLite path with notifications denied by the dedicated AVD host. */
internal object HiddenNotificationProbe {
    @Suppress("DEPRECATION")
    fun run(i:Instrumentation):String {
        check(android.os.Build.PRODUCT=="sdk_gphone64_x86_64")
        val c=i.targetContext
        check(!c.getSystemService(NotificationManager::class.java).areNotificationsEnabled())
        check(!AmbientTracking.enabled(c) && !AmbientTracking.running)
        val lm=c.getSystemService(LocationManager::class.java)
        var mock=false
        try {
            // Wait for Application.onCreate on the main thread. startActivitySync can wait
            // forever when singleTop reuses the task currently behind system settings.
            i.runOnMainSync {c.startActivity(Intent(c,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))}
            val db=(c.applicationContext as WalkPaintApp).repository
            val before=db.snapshot().realPointCount
            lm.addTestProvider(LocationManager.GPS_PROVIDER,false,true,false,false,true,true,true,
                Criteria.POWER_HIGH,Criteria.ACCURACY_FINE);mock=true
            lm.setTestProviderEnabled(LocationManager.GPS_PROVIDER,true)
            i.runOnMainSync { AmbientTracking.setEnabled(c,true);AmbientTracking.start(c) }
            val deadline=SystemClock.elapsedRealtime()+10_000
            while(!AmbientTracking.running && SystemClock.elapsedRealtime()<deadline) SystemClock.sleep(100)
            check(AmbientTracking.running) {"Service did not start with notifications denied"}
            for(n in 0..8) {
                lm.setTestProviderLocation(LocationManager.GPS_PROVIDER,Location(LocationManager.GPS_PROVIDER).apply {
                    latitude=48.8566+n*.00004;longitude=2.3522;accuracy=8f
                    time=System.currentTimeMillis();elapsedRealtimeNanos=SystemClock.elapsedRealtimeNanos()
                    speed=1.6f;speedAccuracyMetersPerSecond=.3f
                })
                SystemClock.sleep(2200)
            }
            val end=SystemClock.elapsedRealtime()+10_000
            while(db.snapshot().realPointCount<=before && SystemClock.elapsedRealtime()<end) SystemClock.sleep(100)
            val added=db.snapshot().realPointCount-before
            check(added>=2) {"No continuous SQLite commits with notifications denied: $added"}
            check(AmbientTracking.running)
            check(TrackingSetup.notice("SAVED",true)==null)
            check(TrackingSetup.notice("CALLBACK_INTERRUPTED",true)=="很久没收到定位了")
            return "HIDDEN_NOTIFICATION_PASS denied=true service_running=true committed_points=$added actual_gps_failure_visible=true"
        } finally {
            i.runOnMainSync {AmbientTracking.stop(c)}
            if(mock) runCatching {lm.removeTestProvider(LocationManager.GPS_PROVIDER)}
            val end=SystemClock.elapsedRealtime()+5000
            while(AmbientTracking.running && SystemClock.elapsedRealtime()<end) SystemClock.sleep(50)
            check(!AmbientTracking.running)
            // finish() immediately kills this test process. Flush cleanup before that boundary.
            check(c.getSharedPreferences("footprint_preferences",android.content.Context.MODE_PRIVATE)
                .edit().putBoolean("automatic_enabled",false).commit())
        }
    }
}
