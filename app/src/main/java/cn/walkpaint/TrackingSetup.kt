package cn.walkpaint

/** Permission order for the user's explicit enable action, including optional deferrals. */
internal object TrackingSetup {
    enum class Step { LOCATION, BACKGROUND, NOTIFICATIONS, START }

    fun next(sdk: Int, fine: Boolean, background: Boolean, notifications: Boolean,
             backgroundDeferred: Boolean = false, notificationAttempted: Boolean = false): Step = when {
        !fine -> Step.LOCATION
        sdk >= 29 && !background && !backgroundDeferred -> Step.BACKGROUND
        sdk >= 33 && !notifications && !notificationAttempted -> Step.NOTIFICATIONS
        else -> Step.START
    }

    fun notice(code: String, background: Boolean, notifications: Boolean): String? = when (code) {
        "PERMISSION" -> "需要允许精确定位"
        "GPS_DISABLED" -> "系统定位已关闭"
        "SAVE_FAILED" -> "足迹暂时无法保存"
        "REGISTER_FAILED", "SERVICE_STOPPED" -> "当前无法记录"
        else -> when {
            !background -> "需要允许后台定位"
            !notifications -> "记录通知未开启"
            code == "CALLBACK_INTERRUPTED" -> "很久没收到定位了"
            code in setOf("STARTING", "INVALID_FIX") -> "正在等待可靠定位"
            else -> null
        }
    }
}

