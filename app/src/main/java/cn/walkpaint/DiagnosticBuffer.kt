package cn.walkpaint

/** Live diagnostics stay current; routine events are folded into one bounded snapshot. */
internal class DiagnosticBuffer(initial: Map<String, Any?> = emptyMap()) {
    private val values = initial.toMutableMap()
    var dirty = false
        private set

    fun record(kind: String, reason: String, at: Long, gap: Long? = null): Boolean {
        fun count(key: String) { values[key] = long(key) + 1 }
        values["last_event_at"] = at
        values["last_event"] = reason
        when (kind) {
            "start" -> { values["service_start_at"] = at; count("start_count") }
            "heartbeat" -> values["heartbeat_at"] = at
            "gps" -> { values["gps_callback_at"] = at; count("gps_count") }
            "valid_gps" -> { values["valid_gps_at"] = at; count("valid_gps_count") }
            "power" -> { values["power_at"] = at; values["power_state"] = reason }
            "exit" -> { values["process_exit_at"] = at; values["process_exit_reason"] = reason }
            "recovery" -> { values["recovery_at"] = at; values["recovery_reason"] = reason; count("recovery_count") }
            "saved" -> { values["saved_at"] = at; count("saved_count") }
            "filtered" -> {
                values["filtered_at"] = at; values["filter_reason"] = reason; count("filtered_count")
            }
            "failure" -> { values["failure_at"] = at; values["failure_reason"] = reason }
            "register" -> { values["registration_at"] = at; values["registration"] = reason }
            "stop" -> { values["service_stop_at"] = at; values["stop_reason"] = reason }
        }
        if (gap != null) {
            values["last_gap_ms"] = gap
            count("gap_count")
            values["max_gap_ms"] = maxOf(gap, long("max_gap_ms"))
        }
        dirty = true
        return gap != null || kind in setOf("start", "stop", "failure", "register", "exit", "recovery", "power")
    }

    fun long(key: String): Long = (values[key] as? Number)?.toLong() ?: 0L
    fun snapshot(): Map<String, Any?> = values.toMap()
    fun persisted() { dirty = false }

    companion object { const val FLUSH_INTERVAL_MS = 30_000L }
}
