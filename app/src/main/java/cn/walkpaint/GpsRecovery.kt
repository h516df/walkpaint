package cn.walkpaint

/** Recovery is bounded per continuous-stream outage. Probe fixes cannot reset that episode. */
internal class GpsRecovery(private val started: Long) {
    enum class Action { NONE, PROBE, REGISTER }
    private var continuous: Long? = null
    private var lastProbe: Long? = null
    private var registeredOnce = false
    private var probing = false
    fun callback(now: Long) { continuous=now; registeredOnce=false }
    fun cancelProbe() { probing=false }
    fun stale(now: Long): Boolean = now-(continuous ?: started) >= if(continuous==null) ACQUIRE_MS else STALE_MS
    fun next(now: Long, registered: Boolean, provider: Boolean): Action {
        if(!provider || probing) return Action.NONE
        if(!registered && !registeredOnce) { registeredOnce=true;return Action.REGISTER }
        if(!stale(now) || lastProbe?.let { now-it < PROBE_INTERVAL_MS } == true) return Action.NONE
        lastProbe=now;probing=true;return Action.PROBE
    }
    fun probeFinished(now: Long): Boolean {
        probing=false
        if(!stale(now) || registeredOnce) return false
        registeredOnce=true;return true
    }
    companion object {
        const val ACQUIRE_MS=180_000L
        const val STALE_MS=90_000L
        const val PROBE_INTERVAL_MS=300_000L
        const val PROBE_MS=15_000L
        fun fresh(requestElapsed: Long, fixElapsed: Long, now: Long): Boolean =
            fixElapsed>=requestElapsed && fixElapsed<=now && now-requestElapsed in 0..PROBE_MS
    }
}

/** Process-local receipt times are monotonic. Persistent wall-clock history is separate. */
internal object GpsReceipts {
    @Volatile var started=0L
    @Volatile var continuous=0L
    @Volatile var valid=0L
    @Volatile var probe=0L
    fun start(now:Long) { started=now;continuous=0;valid=0;probe=0 }
    fun age(at:Long,now:Long):Long? = at.takeIf { it>0 && now>=it }?.let { now-it }
}
