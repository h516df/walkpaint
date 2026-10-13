package cn.walkpaint

/** One health tick, also used by the Android Handler loop. */
internal class HealthCheckLoop(
    private val active: () -> Boolean,
    private val check: () -> Unit,
    private val failed: (Exception) -> Unit,
    private val schedule: (Runnable) -> Unit
) : Runnable {
    override fun run() {
        if (!active()) return
        try {
            check()
        } catch (e: Exception) {
            failed(e)
        } finally {
            // A permission/provider/status exception must not silently end the watchdog.
            if (active()) schedule(this)
        }
    }
}
