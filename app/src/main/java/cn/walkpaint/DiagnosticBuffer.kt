package cn.walkpaint

/** Live diagnostics stay current; routine events are folded into one bounded snapshot. */
internal class DiagnosticBuffer(initial: Map<String, Any?> = emptyMap()) {
    private val values = initial.toMutableMap()
    var dirty = false
        private set

    fun record(kind: String, reason: String, at: Long, gap: Long? = null, amount: Int = 1): Boolean {
        val safeReason = if (kind == "filtered") GpsFilterCategory.safeReason(reason) else reason
        require(amount in 1..TrailIngress.MAX_POINTS)
        fun count(key: String) { values[key] = long(key) + if(kind=="saved") amount else 1 }
        values["last_event_at"] = at
        values["last_event"] = safeReason
        when (kind) {
            "start" -> {
                values["service_start_at"] = at; count("start_count")
                values.putIfAbsent("staged_counts_since", at)
                // "moving" counts fixes where the receiver looked like it was genuinely travelling.
                // Without it a diagnosis cannot tell "nothing to record because the phone is on a
                // desk" from "recording is broken while the person walks".
                listOf("gps", "valid_gps", "saved", "filtered", "moving", "probe_start", "probe_fix").forEach {
                    values["session_${it}_count"] = 0L
                }
                GpsFilterCategory.entries.forEach { values[it.sessionKey] = 0L }
                // A previous session's satellite visibility is not current evidence.
                listOf("gnss_state_at", "gnss_at", "gnss_visible", "gnss_used", "gnss_first_fix_at", "gnss_ttff_ms").forEach { values[it] = 0L }
                values["gnss_state"] = "尚无本次卫星状态"
            }
            "heartbeat" -> values["heartbeat_at"] = at
            "gps" -> { values["gps_callback_at"] = at; count("gps_count") }
            "valid_gps" -> { values["valid_gps_at"] = at; count("valid_gps_count") }
            "power" -> { values["power_at"] = at; values["power_state"] = reason }
            "exit" -> { values["process_exit_at"] = at; values["process_exit_reason"] = reason }
            "exit_verified" -> values["process_exit_verified_at"]=at
            "recovery" -> { values["recovery_at"] = at; values["recovery_reason"] = reason; count("recovery_count") }
            "saved" -> { values["saved_at"] = at; count("saved_count") }
            "moving" -> { values["moving_at"] = at; count("moving_count") }
            "probe_start" -> { values["probe_start_at"]=at;count("session_probe_start_count") }
            "probe_fix" -> { values["probe_fix_at"]=at;count("session_probe_fix_count") }
            "probe_result" -> { values["probe_result_at"]=at;values["probe_result"]=reason }
            "filtered" -> {
                values["filtered_at"] = at; values["filter_reason"] = safeReason; count("filtered_count")
                count(GpsFilterCategory.fromReason(safeReason).sessionKey)
            }
            "failure" -> { values["failure_at"] = at; values["failure_reason"] = reason }
            "register" -> { values["registration_at"] = at; values["registration"] = reason }
            "stop" -> { values["service_stop_at"] = at; values["stop_reason"] = reason }
        }
        if (kind in setOf("gps", "valid_gps", "saved", "filtered", "moving")) count("session_${kind}_count")
        if (kind == "gnss") { values["gnss_state_at"] = at; values["gnss_state"] = reason }
        if (kind == "gnss_first") { values["gnss_first_fix_at"] = at; values["gnss_ttff_ms"] = gap ?: 0L }
        if (kind == "provider") { values["provider_at"] = at; values["provider_state"] = reason }
        if (gap != null && kind == "gps") {
            values["last_gap_ms"] = gap
            count("gap_count")
            values["max_gap_ms"] = maxOf(gap, long("max_gap_ms"))
        }
        dirty = true
        return gap != null || kind in setOf("start", "stop", "failure", "register", "exit", "recovery", "power")
    }

    fun satellites(visible: Int, used: Int, at: Long) {
        require(visible >= 0 && used in 0..visible)
        values["gnss_at"] = at
        values["gnss_visible"] = visible.toLong()
        values["gnss_used"] = used.toLong()
        dirty = true
    }

    fun long(key: String): Long = (values[key] as? Number)?.toLong() ?: 0L
    fun snapshot(): Map<String, Any?> = values.toMap()
    fun persisted() { dirty = false }

    companion object { const val FLUSH_INTERVAL_MS = 30_000L }
}
