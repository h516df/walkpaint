package cn.walkpaint

/** Status uses callback receipts and real commits; a running service alone is never healthy. */
internal data class RecordingSignals(
    val now: Long, val enabled: Boolean = true, val running: Boolean = true,
    val fine: Boolean = true, val gpsEnabled: Boolean = true,
    val startedAt: Long = 0, val callbackAt: Long = 0, val validAt: Long = 0,
    val savedAt: Long = 0, val filterAt: Long = 0, val filter: String = "",
    val failureAt: Long = 0, val failure: String = "", val databaseError: String? = null
)
internal data class RecordingStatus(val code: String, val title: String, val hint: String,
                                    val healthy: Boolean = false) {
    companion object {
        fun assess(s: RecordingSignals): RecordingStatus {
            fun recent(at: Long, window: Long = 45_000) = at > 0 && s.now - at in 0..window
            if (!s.enabled) return RecordingStatus("PAUSED", "已暂停记录", "暂停期间不会记录位置")
            if (!s.fine) return RecordingStatus("PERMISSION", "缺少精确定位权限", "请在系统设置里允许「精确位置」")
            if (!s.gpsEnabled) return RecordingStatus("GPS_DISABLED", "手机定位已关闭", "打开手机定位后会自动继续；中间那段不会补画")
            if (!s.running) return RecordingStatus("SERVICE_STOPPED", "记录已停止", "回到应用可尝试恢复；被系统强制停止后需要手动打开")
            if (s.databaseError != null || (s.failure.startsWith("DATABASE_") && s.failureAt > s.savedAt))
                return RecordingStatus("SAVE_FAILED", "这次没能存下来", "请检查手机存储空间；已有的足迹没有丢")
            if (s.failure.startsWith("GPS_REGISTER_") && s.failureAt >= s.startedAt && s.failureAt > s.callbackAt)
                return RecordingStatus("REGISTER_FAILED", "定位没有启动", "正在重试，暂时还没在记录")
            val currentCallback = s.callbackAt.takeIf { it >= s.startedAt } ?: 0
            if (!recent(currentCallback)) {
                if (currentCallback > 0 || !recent(s.startedAt))
                    return RecordingStatus("CALLBACK_INTERRUPTED", "收不到定位信号", "监听还开着，但没有新的定位进来；请到室外或稍等")
                return RecordingStatus("STARTING", "正在搜寻定位", "还没拿到第一个有效定位，此时并没有在记录")
            }
            if (s.validAt < s.startedAt || !recent(s.validAt) || s.filterAt > s.validAt && s.filter in setOf(
                    "UNRELIABLE_ACCURACY", "MISSING_ACCURACY", "INVALID_COORDINATE", "STALE_FIX")) {
                val hint = when (s.filter) {
                    "UNRELIABLE_ACCURACY", "MISSING_ACCURACY" -> "定位精度不够，暂时不记录"
                    "STALE_FIX" -> "收到的定位已过期，没有保存"
                    "INVALID_COORDINATE" -> "收到的定位无效，没有保存"
                    else -> "有定位进来，但还不足以上报足迹"
                }
                return RecordingStatus("INVALID_FIX", "定位信号较弱", hint)
            }
            if (s.savedAt >= maxOf(s.startedAt,s.filterAt) && recent(s.savedAt, 30_000)) return RecordingStatus("SAVED", "刚刚记下新足迹",
                "这一笔已经存好了", true)
            val hint = when (s.filter) {
                "JUMP_OR_TOO_FAST" -> "这一步跳得太远，不像真的走过，本次未保存"
                "NON_INCREASING_TIME" -> "收到的定位重复或时间倒序，本次未保存"
                else -> "定位在更新，但还没走出可记录的距离"
            }
            return RecordingStatus("WAITING_MOVEMENT", "定位正常 · 还没走出距离", hint, true)
        }
    }
}
