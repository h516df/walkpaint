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
                        lastRegistrationAge: Long): Boolean = providerEnabled &&
            (!registered || (lastCallbackAge > 90_000L && lastRegistrationAge > 90_000L))
    }
}
