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
    private var history: ProblemHistory? = null
    private var historySaveFailed = false
    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("recording_diagnostics", Context.MODE_PRIVATE)
    private fun historyPrefs(c:Context)=c.applicationContext.getSharedPreferences("feedback_history",Context.MODE_PRIVATE)
    private fun historyState(c:Context):ProblemHistory = history ?: historyPrefs(c).let {
        ProblemHistory(it.getString("events","") ?: "",it.getBoolean("enabled",true)).also { history=it }
    }

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
        if(current.dirty) {
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
        val recent=historyState(context)
        recent.snapshot(System.currentTimeMillis())
        if(recent.dirty) {
            historySaveFailed=runCatching {
                !historyPrefs(context).edit().putString("events",recent.encode()).putBoolean("enabled",recent.enabled).commit()
            }.getOrDefault(true)
            if(!historySaveFailed) recent.persisted() else schedule(context)
        }
    }

    fun event(context: Context, kind: String, reason: String, gap: Long? = null, occurredAt: Long = System.currentTimeMillis(), count: Int = 1) {
        val application = context.applicationContext
        val at = occurredAt
        // Mirrored into the in-memory field recorder, so an export carries the whole timeline
        // without a second set of call sites that could drift out of step with these.
        FieldRecorder.event(at, kind, reason)
        writer.execute {
            synchronized(lock) {
                val immediate = state(application).record(kind, reason, at, gap, count)
                ProblemCode.fromRecording(kind,reason,gap)?.let { historyState(application).record(it,at,System.currentTimeMillis()) }
                if (immediate) {
                    scheduledFlush?.cancel(false)
                    scheduledFlush = null
                    // Lifecycle/failure/gap evidence flushes pending routine counts too.
                    flush(application)
                } else schedule(application)
            }
        }
    }

    fun satellites(context: Context, visible: Int, used: Int) {
        val application = context.applicationContext
        val at = System.currentTimeMillis()
        writer.execute {
            synchronized(lock) {
                state(application).satellites(visible, used, at)
                schedule(application)
            }
        }
    }

    fun snapshot(context: Context): Map<String, Any?> = synchronized(lock) { state(context).snapshot() }

    data class HistoryView(val entries:List<ProblemEntry>,val enabled:Boolean,val saveFailed:Boolean)
    fun recent(context:Context):HistoryView=synchronized(lock) {
        val h=historyState(context)
        val entries=h.snapshot(System.currentTimeMillis())
        if(h.dirty) writer.execute { synchronized(lock) { schedule(context.applicationContext) } }
        HistoryView(entries,h.enabled,historySaveFailed)
    }
    fun problem(context:Context,code:ProblemCode) {
        val application=context.applicationContext
        writer.execute { synchronized(lock) {
            historyState(application).record(code,System.currentTimeMillis())
            if(code.critical) flush(application) else schedule(application)
        } }
    }
    fun collectHistory(context:Context,enabled:Boolean) {
        val application=context.applicationContext
        writer.execute { synchronized(lock) { historyState(application).setCollection(enabled);flush(application) } }
    }
    fun clearHistory(context:Context) {
        val application=context.applicationContext
        writer.execute { synchronized(lock) { historyState(application).clear();flush(application) } }
    }

    fun signals(context: Context, databaseError: String? = null): RecordingSignals {
        val values=snapshot(context)
        fun at(key: String)=(values[key] as? Number)?.toLong() ?: 0L
        return RecordingSignals(now=System.currentTimeMillis(),
            enabled=AmbientTracking.enabled(context),running=AmbientTracking.running,
            fine=AmbientTracking.hasFineLocation(context),
            gpsEnabled=context.getSystemService(android.location.LocationManager::class.java)
                .isProviderEnabled(android.location.LocationManager.GPS_PROVIDER),
            startedAt=at("service_start_at"),callbackAt=at("gps_callback_at"),validAt=at("valid_gps_at"),
            savedAt=maxOf(AmbientTracking.lastSaved,at("saved_at")),
            filterAt=at("filtered_at"),filter=values["filter_reason"] as? String ?: "",
            failureAt=at("failure_at"),failure=values["failure_reason"] as? String ?: "",databaseError=databaseError,
            receiptClock=true,startAgeMs=GpsReceipts.age(GpsReceipts.started,android.os.SystemClock.elapsedRealtime()),
            callbackAgeMs=GpsReceipts.age(GpsReceipts.continuous,android.os.SystemClock.elapsedRealtime()),
            validAgeMs=GpsReceipts.age(GpsReceipts.valid,android.os.SystemClock.elapsedRealtime()))
    }

    fun summary(context: Context): String {
        val values = synchronized(lock) { state(context).snapshot() }
        fun long(key: String) = (values[key] as? Number)?.toLong() ?: 0L
        fun string(key: String) = values[key] as? String ?: "无"
        val failed = synchronized(lock) { persistenceFailed }
        val now = System.currentTimeMillis()
        val recent = recent(context)
        fun age(key: String): String = long(key).let {
            if (it == 0L) "无" else "${(now - it).coerceAtLeast(0) / 1000} 秒前"
        }
        return listOf(
            "服务启动：${age("service_start_at")} · 累计 ${long("start_count")} 次",
            "服务心跳：${age("heartbeat_at")}（仅进程活性）",
            "GPS 注册：${age("registration_at")} · ${string("registration")}",
            "本次服务：持续GPS回调 ${long("session_gps_count")} / 有效GPS ${long("session_valid_gps_count")} / 已入库 ${long("session_saved_count")} / 过滤 ${long("session_filtered_count")}",
            "等待移动确认：${long("session_pending_count")} 次暂缓决定（可能随后随批入库，不是丢点统计）",
            "本次独立探测：${long("session_probe_start_count")} 次 / 新鲜回调 ${long("session_probe_fix_count")}；${string("probe_result")}（一次定位不代表持续恢复）",
            GpsFilterDiagnostics.session(values),
            GpsFilterDiagnostics.recent(recent.entries, recent.enabled, recent.saveFailed, now),
            "系统GPS/订阅：${string("provider_state")} · ${age("provider_at")}（订阅成功不代表已定位）",
            "卫星状态：${string("gnss_state")} · ${age("gnss_state_at")}",
            "最近卫星观测：${if (long("gnss_at") == 0L) "无" else "可见 ${long("gnss_visible")} / 参与定位 ${long("gnss_used")} · ${age("gnss_at")}"}",
            "本次GNSS首次定位：${age("gnss_first_fix_at")} · ${if (long("gnss_first_fix_at") == 0L) "耗时未知" else "耗时 ${long("gnss_ttff_ms")} 毫秒"}（不等于GPS回调或入库）",
            "GPS 最近回调：${age("gps_callback_at")} · 累计 ${long("gps_count")} 次",
            "最近有效 GPS：${age("valid_gps_at")} · 累计 ${long("valid_gps_count")} 次（通过时间、坐标、精度检查）",
            "最近入库：${age("saved_at")} · 累计 ${long("saved_count")} 点（实际 SQLite 提交）",
            "最近过滤：${if (long("filtered_at") == 0L) "无" else GpsFilterCategory.safeReason(string("filter_reason"))} · ${age("filtered_at")}",
            "最近失败：${string("failure_reason")} · ${age("failure_at")}",
            "回调断档 >45秒：${long("gap_count")} 次 · 最近 ${long("last_gap_ms") / 1000} 秒 · 最长 ${long("max_gap_ms") / 1000} 秒",
            "服务停止：${string("stop_reason")} · ${age("service_stop_at")}",
            "最近恢复：${string("recovery_reason")} · ${age("recovery_at")}",
            "${if(long("process_exit_at")>0 && long("process_exit_verified_at")==long("process_exit_at")) "本应用记录进程退出" else "历史退出线索（进程归属未知）"}：${string("process_exit_reason")} · ${age("process_exit_at")}（${if(string("process_exit_reason")=="ANDROID_REASON_10") "REASON_USER_REQUESTED；系统记录的用户请求类退出，不据此归因操作或厂商" else "仅系统原因码"}）",
            "屏幕/省电/待机：${string("power_state")} · ${age("power_at")}",
            "最近事件：${string("last_event")}",
            "诊断保存：${if (failed) "写入失败，等待重试" else "正常"}",
            "历史累计跨服务/升级保留；有效GPS计数自支持该字段的版本起，不可与更早回调/入库总数直接相减。请用本次服务四阶段计数比较。",
            "普通GPS计数约每30秒持久化，重大异常即时保存；卫星回调受系统/设备支持及前台限制，未收到不等于无卫星。断档不等于厂商杀后台。"
        ).joinToString("\n")
    }
}
