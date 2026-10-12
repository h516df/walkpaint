package cn.walkpaint

/** Invalidates queued work when recording stops, including when Android reuses a service. */
internal class RecordingSession {
    @Volatile private var generation = 0L
    @Volatile var active = false
        private set
    @Synchronized fun start(): Long { generation++;active=true;return generation }
    @Synchronized fun stop() { active=false;generation++ }
    fun ticket(): Long = generation
    fun accepts(ticket:Long):Boolean = active && generation==ticket
}
