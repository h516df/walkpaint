package cn.walkpaint

/** Receipt gaps measure missing callbacks, not time spent standing still. */
internal class RecordingHealth(private var lastCallbackElapsed: Long? = null) {
    fun callback(elapsed: Long): Long? {
        val gap = lastCallbackElapsed?.let { elapsed - it }
        lastCallbackElapsed = elapsed
        return gap?.takeIf { it > 45_000L }
    }

    companion object {
        fun shouldRetry(registered: Boolean, providerEnabled: Boolean, lastCallbackAge: Long,
                        lastRegistrationAge: Long, acquiringGnss: Boolean = false): Boolean {
            // Let a fresh GNSS search finish, but a stream of zero-used satellite statuses must
            // never postpone recovery forever. Re-register at most once per search interval.
            val retryAfter = if(acquiringGnss) 180_000L else 90_000L
            return providerEnabled && (!registered ||
                (lastCallbackAge > retryAfter && lastRegistrationAge > retryAfter))
        }
    }
}
