package cn.walkpaint

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The last half hour of what the receiver actually delivered, held in memory only.
 *
 * The persisted diagnostics are deliberately coordinate-free, so they can answer "is recording
 * working" but never "where did it go wrong". When the person notices something and cannot describe
 * it, this is what turns that moment into evidence. Nothing here is written to disk or uploaded:
 * the raw material leaves the phone only when the person copies or shares it themselves.
 */
internal object FieldRecorder {
    /** How much history an export carries. */
    const val WINDOW_MS = 30 * 60_000L
    const val MAX_FIXES = 600
    const val MAX_REJECTS = 300
    const val MAX_EVENTS = 300

    internal data class Fix(
        val at: Long,
        val lat: Double,
        val lon: Double,
        val accuracy: Float,
        val provider: String,
        val stored: Boolean,
    )

    internal data class Reject(val at: Long, val reason: String, val accuracy: Float)

    internal data class Event(val at: Long, val kind: String, val reason: String)

    internal data class Snapshot(
        val fixes: List<Fix>,
        val rejects: List<Reject>,
        val events: List<Event>,
        val dropped: Long,
    )

    private val lock = Any()
    private val fixes = ArrayDeque<Fix>()
    private val rejects = ArrayDeque<Reject>()
    private val events = ArrayDeque<Event>()
    /** Counts what fell out of the window, so an export can say the view is not the whole story. */
    private var dropped = 0L

    /** One fix as the receiver reported it, before any filter has had its say. */
    fun fix(at: Long, lat: Double, lon: Double, accuracy: Float, provider: String) {
        synchronized(lock) {
            fixes.addLast(Fix(at, lat, lon, accuracy, provider, stored = false))
            while (fixes.size > MAX_FIXES) { fixes.removeFirst(); dropped++ }
        }
    }

    /** Marks the most recent fix as the one that reached the database. */
    fun stored() {
        synchronized(lock) {
            val last = fixes.removeLastOrNull() ?: return
            fixes.addLast(last.copy(stored = true))
        }
    }

    fun reject(at: Long, reason: String, accuracy: Float) {
        synchronized(lock) {
            rejects.addLast(Reject(at, reason, accuracy))
            while (rejects.size > MAX_REJECTS) rejects.removeFirst()
        }
    }

    fun event(at: Long, kind: String, reason: String) {
        synchronized(lock) {
            events.addLast(Event(at, kind, reason))
            while (events.size > MAX_EVENTS) events.removeFirst()
        }
    }

    /** Everything inside the window, oldest first. Also drops what has aged out for good. */
    fun snapshot(now: Long): Snapshot = synchronized(lock) {
        val floor = now - WINDOW_MS
        while (fixes.isNotEmpty() && fixes.first().at < floor) { fixes.removeFirst(); dropped++ }
        while (rejects.isNotEmpty() && rejects.first().at < floor) rejects.removeFirst()
        while (events.isNotEmpty() && events.first().at < floor) events.removeFirst()
        Snapshot(fixes.toList(), rejects.toList(), events.toList(), dropped)
    }

    fun clear() = synchronized(lock) {
        fixes.clear(); rejects.clear(); events.clear(); dropped = 0L
    }
}

/** Facts the field export needs, already reduced to primitives so its layout can be tested. */
internal data class FieldFacts(
    val now: Long,
    val version: String,
    val device: String,
    val android: String,
    val engine: String,
    val permissions: String,
    val recording: String,
    val camera: String,
    val veil: String,
    val storage: String,
    val diagnosis: String,
)

/**
 * The on-demand export: everything needed to work out why the phone and the map disagreed, in the
 * order a person would ask about it. The raw fixes are the point — a summary alone is what already
 * failed to answer "where was it wrong".
 */
internal object FieldReport {
    /** The category that means "hand over the raw field material", not a described problem. */
    const val CATEGORY = "记录现场"

    fun build(facts: FieldFacts, snapshot: FieldRecorder.Snapshot): String {
        val out = StringBuilder()
        out.appendLine("—— 记录现场 ——")
        out.appendLine("导出时间：${stamp(facts.now)}（手机本地时间）")
        out.appendLine("版本：${facts.version} · Android ${facts.android} · ${facts.device}")
        out.appendLine("地图引擎：${facts.engine}")
        out.appendLine("权限与后台：${facts.permissions}")
        out.appendLine()
        out.appendLine("—— 结论（自动判定，未必正确）——")
        out.appendLine(facts.diagnosis)
        out.appendLine()
        out.appendLine("—— 记录状态 ——")
        out.appendLine(facts.recording)
        out.appendLine()
        out.appendLine("—— 地图、视野与迷雾（决定你看到什么）——")
        out.appendLine(facts.camera)
        out.appendLine(facts.veil)
        out.appendLine()
        out.appendLine("—— 数据 ——")
        out.appendLine(facts.storage)
        out.appendLine()
        out.appendLine("—— 原始定位：最近 ${FieldRecorder.WINDOW_MS / 60_000} 分钟（含坐标，最新在最后）——")
        if (snapshot.fixes.isEmpty()) {
            out.appendLine("这段时间没有收到任何定位回调。")
        } else {
            out.appendLine("时间,纬度,经度,精度米,来源,结果")
            snapshot.fixes.forEach { f ->
                out.appendLine("${clock(f.at)},${f.lat},${f.lon},${f.accuracy},${f.provider}," +
                    if (f.stored) "已入库" else "未入库")
            }
        }
        if (snapshot.dropped > 0) out.appendLine("（另有 ${snapshot.dropped} 个更早的点已滚出窗口，未包含）")
        out.appendLine()
        out.appendLine("—— 被丢弃的点与原因（$FieldRecorder.MAX_REJECTS 条上限）——")
        if (snapshot.rejects.isEmpty()) out.appendLine("没有丢弃记录。")
        else snapshot.rejects.forEach { r ->
            out.appendLine("${clock(r.at)},${r.reason},精度${if (r.accuracy > 0f) r.accuracy.toInt().toString() else "未知"}米")
        }
        out.appendLine()
        out.appendLine("—— 事件时间线（最新在最后）——")
        if (snapshot.events.isEmpty()) out.appendLine("没有事件。")
        else snapshot.events.forEach { e -> out.appendLine("${clock(e.at)} ${e.kind} ${e.reason}") }
        out.appendLine()
        out.appendLine("这个文件含你的行走坐标；它不会自动上传，只在你主动分享时离开手机。")
        return out.toString()
    }

    private fun clock(at: Long) = SimpleDateFormat("HH:mm:ss", Locale.ROOT).format(Date(at))
    private fun stamp(at: Long) = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(Date(at))
}
