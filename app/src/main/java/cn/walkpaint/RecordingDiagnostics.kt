package cn.walkpaint

import android.content.Context
import java.util.concurrent.Executors

/** Diagnostics contain fixed reason codes, timestamps and counters, never GPS coordinates.
 * A single worker folds high-frequency location events and flushes at most every 30 seconds.
 * Critical failures, lifecycle changes and GPS gaps commit immediately.
 */
internal object RecordingDiagnostics {
    private val writer = Executors.newSingleThreadScheduledExecutor()
    private val lock = Any()
    private var buffer: DiagnosticBuffer? = null
    private var scheduledFlush: java.util.concurrent.ScheduledFuture<*>? = null
    private var persistenceFailed = false
    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("recording_diagnostics", Context.MODE_PRIVATE)

    private fun state(context: Context): DiagnosticBuffer =
        buffer ?: DiagnosticBuffer(prefs(context).all).also { buffer = it }

    private fun schedule(context: Context) {
        if (scheduledFlush != null) return
        scheduledFlush = writer.schedule({
            synchronized(lock) {
                scheduledFlush = null
                flush(context)
            }
        }, DiagnosticBuffer.FLUSH_INTERVAL_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
    }

    private fun flush(context: Context) {
        val current = state(context)
        if (!current.dirty) return
        val edit = prefs(context).edit()
        current.snapshot().forEach { (key, value) ->
            when (value) {
                is Long -> edit.putLong(key, value)
                is String -> edit.putString(key, value)
            }
        }
        persistenceFailed = !edit.commit()
        if (!persistenceFailed) current.persisted() else schedule(context)
    }

    fun event(context: Context, kind: String, reason: String, gap: Long? = null) {
        val application = context.applicationContext
        val at = System.currentTimeMillis()
        writer.execute {
            synchronized(lock) {
                val immediate = state(application).record(kind, reason, at, gap)
                if (immediate) {
                    scheduledFlush?.cancel(false)
                    scheduledFlush = null
                    // Lifecycle/failure/gap evidence flushes pending routine counts too.
                    flush(application)
                } else schedule(application)
            }
        }
    }

    fun summary(context: Context): String {
        val values = synchronized(lock) { state(context).snapshot() }
        fun long(key: String) = (values[key] as? Number)?.toLong() ?: 0L
        fun string(key: String) = values[key] as? String ?: "无"
        val failed = synchronized(lock) { persistenceFailed }
        val now = System.currentTimeMillis()
        fun age(key: String): String = long(key).let {
            if (it == 0L) "无" else "${(now - it).coerceAtLeast(0) / 1000} 秒前"
        }
        return listOf(
            "服务启动：${age("service_start_at")} · 累计 ${long("start_count")} 次",
            "服务心跳：${age("heartbeat_at")}（仅进程活性）",
            "GPS 注册：${age("registration_at")} · ${string("registration")}",
            "GPS 最近回调：${age("gps_callback_at")} · 累计 ${long("gps_count")} 次",
            "最近入库：${age("saved_at")} · 累计 ${long("saved_count")} 点",
            "最近过滤：${string("filter_reason")} · ${age("filtered_at")}",
            "最近失败：${string("failure_reason")} · ${age("failure_at")}",
            "回调断档 >45秒：${long("gap_count")} 次 · 最近 ${long("last_gap_ms") / 1000} 秒 · 最长 ${long("max_gap_ms") / 1000} 秒",
            "服务停止：${string("stop_reason")} · ${age("service_stop_at")}",
            "最近事件：${string("last_event")}",
            "诊断保存：${if (failed) "写入失败，等待重试" else "正常"}",
            "普通GPS计数约每30秒持久化，重大异常即时保存；断档不等于厂商杀后台。"
        ).joinToString("\n")
    }
}
