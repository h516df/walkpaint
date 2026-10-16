package cn.walkpaint

import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.Handler
import android.os.SystemClock
import java.util.concurrent.Executor

/** Native platform probe. One original deadline also bounds a cached-result fallback. */
internal class FreshGpsProbe(private val manager:LocationManager,private val handler:Handler) {
    private var generation=0
    private var signal:CancellationSignal?=null
    private var listener:LocationListener?=null
    private var deadline:Runnable?=null
    fun cancel() {
        generation++
        signal?.cancel();signal=null
        listener?.let { runCatching { manager.removeUpdates(it) } };listener=null
        deadline?.let { handler.removeCallbacks(it) };deadline=null
    }
    @Suppress("MissingPermission")
    fun start(completed:(Location?,String)->Unit) {
        cancel()
        val ticket=generation
        val start=SystemClock.elapsedRealtime()
        val startNanos=SystemClock.elapsedRealtimeNanos()
        fun finish(fix:Location?,reason:String) {
            if(ticket!=generation) return
            cancel();completed(fix,reason)
        }
        fun fresh(fix:Location?)=fix?.provider==LocationManager.GPS_PROVIDER &&
            fix.elapsedRealtimeNanos>=startNanos && fix.elapsedRealtimeNanos<=SystemClock.elapsedRealtimeNanos() &&
            GpsRecovery.fresh(start,fix.elapsedRealtimeNanos/1_000_000,SystemClock.elapsedRealtime())
        fun fallback() {
            if(ticket!=generation || listener!=null) return
            if(SystemClock.elapsedRealtime()-start>=GpsRecovery.PROBE_MS) { finish(null,"PROBE_TIMEOUT");return }
            val temporary=object:LocationListener {
                override fun onLocationChanged(fix:Location) { if(fresh(fix)) finish(fix,"PROBE_FRESH_GPS") }
            }
            listener=temporary
            try { manager.requestLocationUpdates(LocationManager.GPS_PROVIDER,1000L,0f,temporary,handler.looper) }
            catch(_:Exception) { finish(null,"PROBE_SUBSCRIPTION_FAILED") }
        }
        deadline=Runnable { finish(null,"PROBE_TIMEOUT") }.also { handler.postDelayed(it,GpsRecovery.PROBE_MS) }
        if(Build.VERSION.SDK_INT>=30) {
            signal=CancellationSignal()
            try {
                manager.getCurrentLocation(LocationManager.GPS_PROVIDER,signal,Executor { handler.post(it) }) { fix ->
                    if(ticket==generation) {
                        if(fresh(fix)) finish(fix,"PROBE_FRESH_GPS") else fallback()
                    }
                }
            } catch(_:Exception) { fallback() }
        } else fallback()
    }
}
