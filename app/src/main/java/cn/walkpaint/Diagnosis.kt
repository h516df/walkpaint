package cn.walkpaint

/** Traffic light for one diagnosis line. */
internal enum class Health { OK, WARN, BAD }

/** One readable line: what it is, and the one thing to do about it. */
internal data class DiagnosisItem(
    val title: String,
    val level: Health,
    val detail: String,
    val action: String? = null,
)

/**
 * Everything the diagnosis needs, already reduced to primitives, so the reasoning can be tested
 * without a device. Ages are milliseconds; null means "never happened in this session".
 */
internal data class DiagnosisFacts(
    val enabled: Boolean,
    val running: Boolean,
    val fineLocation: Boolean,
    val backgroundLocation: Boolean,
    val phoneLocationOn: Boolean,
    val batteryWhitelisted: Boolean,
    val callbackAgeMs: Long?,
    val savedAgeMs: Long?,
    val savedCount: Long,
    /**
     * This session: fixes that looked like genuine travel, and fixes actually stored. The pair is
     * what separates "nothing to record because the phone is on a desk" from a broken recorder.
     */
    val movingThisSession: Long = 0,
    val savedThisSession: Long = 0,
    val filteredCount: Long,
    val stationaryFiltered: Long,
    val jumpFiltered: Long,
    val accuracyFiltered: Long,
    val longestGapMs: Long,
    val serviceStartCount: Long,
    val databaseError: String?,
    val dozeIdle: Boolean,
)

/**
 * Turns the raw counters into a conclusion a person can act on.
 *
 * The previous diagnostic was a wall of counts. Nobody could tell which number mattered, so a
 * problem report read like "静止/小位移 2210" with no statement of whether that was normal. Every
 * line here answers the same three questions: is this fine, does it matter, and what do I do.
 *
 * A named [syndromeId] is attached when the pattern matches something already understood, so a
 * report can be matched to a known cause instead of being re-diagnosed from scratch.
 */
internal object Diagnosis {
    /** A callback older than this means no positioning is arriving. */
    const val SIGNAL_WINDOW_MS = 45_000L
    /** No save for this long while positioning is healthy deserves a look. */
    const val STALE_SAVE_MS = 180_000L

    /** Stable identifiers for known patterns. Reported verbatim so a fix can be attached to one. */
    const val SCREEN_OK = "OK_RECORDING"
    const val SCREEN_PAUSED = "PAUSED_BY_CHOICE"
    const val SCREEN_PERMISSION = "MISSING_LOCATION_PERMISSION"
    const val SCREEN_PHONE_LOCATION_OFF = "PHONE_LOCATION_OFF"
    const val SCREEN_BACKGROUND_STOPPED = "BACKGROUND_STOPPED_BY_SYSTEM"
    const val SCREEN_NO_SIGNAL = "NO_POSITIONING_ARRIVING"
    const val SCREEN_WEAK_SIGNAL = "POSITIONING_TOO_IMPRECISE"
    const val SCREEN_STORAGE = "CANNOT_WRITE_TO_STORAGE"
    const val SCREEN_WALKING_FILTERED = "WALKING_REJECTED_AS_STATIONARY"
    const val SCREEN_LONG_GAP = "GAP_IN_RECORDING"
    const val SCREEN_UNCLEAR = "NOTHING_OBVIOUS"

    fun syndromeId(f: DiagnosisFacts): String = when {
        !f.enabled -> SCREEN_PAUSED
        !f.fineLocation -> SCREEN_PERMISSION
        !f.phoneLocationOn -> SCREEN_PHONE_LOCATION_OFF
        f.databaseError != null -> SCREEN_STORAGE
        !f.running -> SCREEN_BACKGROUND_STOPPED
        f.callbackAgeMs == null || f.callbackAgeMs > SIGNAL_WINDOW_MS -> SCREEN_NO_SIGNAL
        // Positioning is arriving and nothing has been saved for a while, yet almost everything is
        // being dropped as "not moving". That is the signature of a walk being mistaken for a
        // stationary receiver, which is a logic problem, not a phone problem.
        f.savedAgeMs == null || f.savedAgeMs > STALE_SAVE_MS -> {
            // Only call the recorder broken when the receiver itself reported travel and nothing
            // was stored anyway. A phone lying on a desk produces the same filter counters and is
            // completely normal, so counts alone must never raise this alarm.
            if (f.movingThisSession >= 3 && f.savedThisSession == 0L) SCREEN_WALKING_FILTERED
            else if (dominantFilter(f) == FilterKind.ACCURACY && f.accuracyFiltered > 0) SCREEN_WEAK_SIGNAL
            else SCREEN_UNCLEAR
        }
        f.longestGapMs > 10 * 60_000L -> SCREEN_LONG_GAP
        else -> SCREEN_OK
    }

    fun verdict(f: DiagnosisFacts): String = when (syndromeId(f)) {
        SCREEN_PAUSED -> "记录是关的。打开「自动记录」就会开始。"
        SCREEN_PERMISSION -> "缺少精确定位权限，现在记不了。去系统设置里允许「精确位置」。"
        SCREEN_PHONE_LOCATION_OFF -> "手机定位关着，现在收不到位置。打开它就自己恢复。"
        SCREEN_STORAGE -> "位置收到了，但写不进手机存储。检查一下剩余空间。"
        SCREEN_BACKGROUND_STOPPED -> "记录被系统停掉了。回到应用能恢复；把本应用加入省电白名单能减少这种情况。"
        SCREEN_NO_SIGNAL -> "收不到定位。多半是在室内或信号被挡住，到室外试试。"
        SCREEN_WEAK_SIGNAL -> "定位精度不够，暂时没记录。到空旷一点的地方会好。"
        SCREEN_WALKING_FILTERED -> "定位在更新，但你走的路被当成「没移动」丢掉了。这是需要修的问题，把这份诊断发我。"
        SCREEN_LONG_GAP -> "整体正常，但中间出现过一次较长的断档。系统清理后台时会这样，断档不是数据被删。"
        SCREEN_UNCLEAR -> "定位正常。手机放着不动时本来就不记录；如果你刚才一直在走却一个点都没记上，把这份诊断发我。"
        else -> "一切正常，不用管它。"
    }

    fun items(f: DiagnosisFacts): List<DiagnosisItem> = listOf(
        DiagnosisItem(
            "记录开关", if (f.enabled) Health.OK else Health.BAD,
            if (f.enabled) "已打开" else "已关闭",
            if (f.enabled) null else "打开「自动记录」",
        ),
        DiagnosisItem(
            "记录后台", if (f.running) Health.OK else Health.BAD,
            if (f.running) "在运行，本次已启动 ${f.serviceStartCount} 次" else "没有在运行",
            when {
                !f.running -> "回到应用就能恢复；把本应用加入省电白名单能减少这种情况"
                !f.batteryWhitelisted -> "建议把本应用加入省电白名单，减少被系统停掉"
                else -> null
            },
        ),
        DiagnosisItem(
            "定位信号", signalLevel(f), signalDetail(f),
            when {
                !f.fineLocation -> "在系统设置里允许「精确位置」"
                !f.phoneLocationOn -> "打开手机定位"
                signalLevel(f) == Health.BAD -> "到室外或稍等，重新拿到定位后会自动继续"
                else -> null
            },
        ),
        DiagnosisItem(
            "写入足迹", writeLevel(f), writeDetail(f),
            when {
                f.databaseError != null -> "检查手机剩余存储空间"
                f.savedAgeMs == null -> "出去走一段就会开始记录"
                else -> null
            },
        ),
        DiagnosisItem(
            "本次过滤", Health.OK, filterDetail(f), filterAdvice(f),
        ),
    )

    fun report(f: DiagnosisFacts): String {
        val id = syndromeId(f)
        val lines = items(f).joinToString("\n") { item ->
            val mark = when (item.level) {
                Health.OK -> "·"
                Health.WARN -> "·"
                Health.BAD -> "!"
            }
            val action = item.action?.let { "  → $it" } ?: ""
            "$mark ${item.title}：${item.detail}$action"
        }
        return buildString {
            append("—— 结论 ——\n")
            append(verdict(f)).append("\n")
            append("症状编号：").append(id).append("\n\n")
            append(lines)
            append("\n\n（下面的原始计数是给开发者看的，可以一起发出来。）")
        }
    }

    private enum class FilterKind { NONE, STATIONARY, JUMP, ACCURACY }

    private fun dominantFilter(f: DiagnosisFacts): FilterKind {
        val stationary = f.stationaryFiltered
        val jump = f.jumpFiltered
        val accuracy = f.accuracyFiltered
        val max = maxOf(stationary, jump, accuracy)
        if (max <= 0) return FilterKind.NONE
        return when (max) {
            stationary -> FilterKind.STATIONARY
            jump -> FilterKind.JUMP
            else -> FilterKind.ACCURACY
        }
    }

    private fun signalLevel(f: DiagnosisFacts): Health = when {
        !f.fineLocation || !f.phoneLocationOn -> Health.BAD
        f.callbackAgeMs == null || f.callbackAgeMs > SIGNAL_WINDOW_MS -> Health.BAD
        dominantFilter(f) == FilterKind.ACCURACY && f.accuracyFiltered > f.savedCount -> Health.WARN
        else -> Health.OK
    }

    private fun signalDetail(f: DiagnosisFacts): String {
        val age = f.callbackAgeMs
        return when {
            !f.fineLocation -> "没有精确定位权限"
            !f.phoneLocationOn -> "手机定位已关闭"
            age == null -> "本次还没有收到过定位"
            age > SIGNAL_WINDOW_MS -> "最近一次定位在 ${age / 1000} 秒前"
            else -> "正常，最近一次在 ${age / 1000} 秒前"
        }
    }

    private fun writeLevel(f: DiagnosisFacts): Health = when {
        f.databaseError != null -> Health.BAD
        f.savedAgeMs == null -> Health.WARN
        else -> Health.OK
    }

    private fun writeDetail(f: DiagnosisFacts): String = when {
        f.databaseError != null -> f.databaseError
        f.savedAgeMs == null -> "本次还没写过，共 ${f.savedCount} 个点"
        f.savedAgeMs < 60_000L -> "刚刚存过，共 ${f.savedCount} 个点"
        else -> "最近一次在 ${f.savedAgeMs / 1000} 秒前，共 ${f.savedCount} 个点"
    }

    private fun filterDetail(f: DiagnosisFacts): String {
        if (f.filteredCount <= 0) return "没有丢过点"
        return "丢下 ${f.filteredCount} 次（没移动 ${f.stationaryFiltered} / 跳得太远 ${f.jumpFiltered} / 精度不够 ${f.accuracyFiltered}）"
    }

    private fun filterAdvice(f: DiagnosisFacts): String? = when (dominantFilter(f)) {
        FilterKind.STATIONARY ->
            "手机放着不动时会有「没移动」，是正常的；若你明明在走它还在涨，把这份诊断发我"
        FilterKind.JUMP -> "「跳得太远」是 GPS 偶尔飘出去，属于保护，不用管"
        FilterKind.ACCURACY -> "「精度不够」说明当时信号差，走到空旷处会自己好"
        FilterKind.NONE -> null
    }
}
