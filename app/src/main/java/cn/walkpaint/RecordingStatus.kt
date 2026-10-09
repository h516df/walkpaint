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
            if (!s.enabled) return RecordingStatus("PAUSED", "自动足迹已暂停", "已暂停 · 不会记录位置")
            if (!s.fine) return RecordingStatus("PERMISSION", "缺少精确定位权限", "请在系统设置允许精确位置")
            if (!s.gpsEnabled) return RecordingStatus("GPS_DISABLED", "系统 GPS 已关闭", "开启系统位置后将尝试恢复；中断区间不会补画")
            if (!s.running) return RecordingStatus("SERVICE_STOPPED", "记录服务未运行", "返回应用可尝试恢复；系统强停后必须手动打开")
            if (s.databaseError != null || (s.failure.startsWith("DATABASE_") && s.failureAt > s.savedAt))
                return RecordingStatus("SAVE_FAILED", "足迹保存失败", "请查看诊断并检查存储空间，原数据保留")
            if (s.failure.startsWith("GPS_REGISTER_") && s.failureAt >= s.startedAt && s.failureAt > s.callbackAt)
                return RecordingStatus("REGISTER_FAILED", "GPS 注册失败", "正在限频重试，尚未恢复记录")
            val currentCallback = s.callbackAt.takeIf { it >= s.startedAt } ?: 0
            if (!recent(currentCallback)) {
                if (currentCallback > 0 || !recent(s.startedAt))
                    return RecordingStatus("CALLBACK_INTERRUPTED", "GPS 回调已中断", "监听仍开启，尚未收到新定位；原因未确定，请查看诊断")
                return RecordingStatus("STARTING", "正在搜寻 GPS", "尚未获得本次有效定位，不代表已经保存足迹")
            }
            if (s.validAt < s.startedAt || !recent(s.validAt) || s.filterAt > s.validAt && s.filter in setOf(
                    "UNRELIABLE_ACCURACY", "MISSING_ACCURACY", "INVALID_COORDINATE", "STALE_FIX")) {
                val hint = when (s.filter) {
                    "UNRELIABLE_ACCURACY", "MISSING_ACCURACY" -> "定位精度不足，暂不记录新足迹"
                    "STALE_FIX" -> "收到过期定位，已拒绝保存"
                    "INVALID_COORDINATE" -> "收到无效定位，已拒绝保存"
                    else -> "收到定位回调，尚无新的有效 GPS"
                }
                return RecordingStatus("INVALID_FIX", "等待可靠 GPS", hint)
            }
            if (s.savedAt >= maxOf(s.startedAt,s.filterAt) && recent(s.savedAt, 30_000)) return RecordingStatus("SAVED", "新足迹已保存",
                "刚刚保存新足迹 · 仅确认匹配的道路上色", true)
            val hint = when (s.filter) {
                "JUMP_OR_TOO_FAST" -> "定位跳跃或速度超出当前记录范围，本次未保存"
                "NON_INCREASING_TIME" -> "重复或倒序定位，本次未保存"
                else -> "GPS 持续更新 · 尚无新的可靠位移，未新增足迹"
            }
            return RecordingStatus("WAITING_MOVEMENT", "GPS 已更新 · 等待位移", hint, true)
        }
    }
}
