package cn.walkpaint

/** A closed vocabulary shared by the live counters and the existing five-minute history. */
internal enum class GpsFilterCategory(val sessionKey: String, val label: String) {
    STATIONARY("session_filter_stationary_count", "静止/小位移"),
    JUMP("session_filter_jump_count", "跳变/过快 JUMP_OR_TOO_FAST"),
    ACCURACY("session_filter_accuracy_count", "精度"),
    TIME("session_filter_time_count", "时间"),
    OTHER("session_filter_other_count", "其他");

    val problemCode: ProblemCode get() = when (this) {
        STATIONARY -> ProblemCode.GPS_STATIONARY
        JUMP -> ProblemCode.GPS_JUMP
        ACCURACY -> ProblemCode.GPS_ACCURACY
        TIME -> ProblemCode.GPS_STALE
        OTHER -> ProblemCode.GPS_FILTERED
    }

    companion object {
        fun fromReason(reason: String): GpsFilterCategory = when (reason) {
            "STATIONARY_OR_SMALL_MOVE" -> STATIONARY
            "JUMP_OR_TOO_FAST" -> JUMP
            "MISSING_ACCURACY", "UNRELIABLE_ACCURACY" -> ACCURACY
            "STALE_FIX", "NON_INCREASING_TIME" -> TIME
            else -> OTHER
        }

        // Unknown text is counted as other, but cannot become persisted/report text.
        fun safeReason(reason: String): String = when (reason) {
            "STATIONARY_OR_SMALL_MOVE", "JUMP_OR_TOO_FAST", "MISSING_ACCURACY",
            "UNRELIABLE_ACCURACY", "STALE_FIX", "NON_INCREASING_TIME", "INVALID_COORDINATE" -> reason
            else -> "OTHER_FILTER"
        }

        fun fromProblem(code: ProblemCode): GpsFilterCategory? = when (code) {
            ProblemCode.GPS_STATIONARY -> STATIONARY
            ProblemCode.GPS_JUMP -> JUMP
            ProblemCode.GPS_ACCURACY -> ACCURACY
            ProblemCode.GPS_STALE -> TIME
            ProblemCode.GPS_FILTERED -> OTHER
            else -> null
        }
    }
}

internal object GpsFilterDiagnostics {
    data class Window(val whole: Map<GpsFilterCategory, Long>, val boundary: Map<GpsFilterCategory, Long>)

    private fun row(counts: Map<GpsFilterCategory, Long>) = GpsFilterCategory.entries.joinToString(" / ") {
        "${it.label} ${counts[it] ?: 0L}"
    }

    fun session(values: Map<String, Any?>): String {
        fun count(key: String) = (values[key] as? Number)?.toLong()?.coerceAtLeast(0) ?: 0L
        val counts = GpsFilterCategory.entries.associateWith { count(it.sessionKey) }
        val classified = counts.values.sum()
        val total = count("session_filtered_count")
        val missing = (total - classified).coerceAtLeast(0)
        return "本次服务GPS过滤分类（独立于四阶段）：\n" + row(counts) +
            "\n已分类 $classified / 过滤总数 $total；旧版或未记录分类不可回溯 $missing。" +
            (if (classified > total) " 分类计数与总数不一致，请结合保存状态检查。" else "") +
            "\n过滤包含有效GPS之前的精度/时间等拒绝，不能用有效GPS减入库来代替过滤数；本次服务重启后分类重置。"
    }

    /** No proportional allocation: an aggregate straddling the cutoff has unknown timing inside it. */
    fun window(entries: List<ProblemEntry>, now: Long, duration: Long): Window {
        require(duration > 0)
        val cutoff = now - duration
        val whole = GpsFilterCategory.entries.associateWith { 0L }.toMutableMap()
        val boundary = whole.toMutableMap()
        entries.take(ProblemHistory.MAX_EVENTS).forEach { e ->
            val category = GpsFilterCategory.fromProblem(e.code) ?: return@forEach
            if (e.first <= 0 || e.last < e.first || e.last > now || e.count !in 1..ProblemHistory.MAX_COUNT) return@forEach
            val target = when {
                e.first >= cutoff -> whole
                e.last >= cutoff -> boundary
                else -> return@forEach
            }
            target[category] = target.getValue(category) + e.count
        }
        return Window(whole, boundary)
    }

    fun recent(entries: List<ProblemEntry>, enabled: Boolean, saveFailed: Boolean, now: Long): String {
        val windows = listOf("最近30分钟" to 30 * 60_000L, "最近2小时" to 2 * 60 * 60_000L)
        return listOf(
            "近期GPS过滤分类（既有5分钟聚合线索）：${if (enabled) "收集中" else "已关闭收集"}${if (saveFailed) "，本机保存失败，统计可能不完整" else ""}",
            windows.joinToString("\n\n") { (name, duration) ->
                val w = window(entries, now, duration)
                "$name · 整桶次数：\n${row(w.whole)}\n跨窗口起点桶的全部次数：\n${row(w.boundary)}" +
                    if (w.whole.values.sum() + w.boundary.values.sum() == 0L) "\n无保留过滤桶，不代表没有过滤。" else ""
            },
            "口径：仅统计当前保留的过滤线索，按事件时间；整桶首次/末次均在窗口内。跨起点桶只知全部次数，其中多少在窗口内未知，不分摊、不计入整桶次数。",
            "关闭收集、清空、7天/500条上限裁剪及升级前未记录的历史不可回溯；旧版其他类无法再细分。计数不是完整行车统计，不能判断行车时段，也不能仅凭最近一次停车过滤解释行车问题。"
        ).joinToString("\n\n")
    }
}
