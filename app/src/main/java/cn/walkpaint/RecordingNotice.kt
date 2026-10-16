package cn.walkpaint

/** One snapshot supplies both notification lines. No coordinates or network blue-dot claims. */
internal data class RecordingNotice(val code:String,val title:String,val detail:String) {
    val urgencyKey get()=if(code in setOf("SAVED","WAITING_MOVEMENT")) "TRACKING" else code
    companion object {
        fun of(status:RecordingStatus,callbackAgeMs:Long?,accuracy:Float?,saved:Int):RecordingNotice {
            val age=callbackAgeMs?.let {
                if(it<60_000) "${it.coerceAtLeast(0)/1000}秒前" else "${it/60_000}分钟前"
            }
            val detail=when(status.code) {
                "GPS_DISABLED"->"等待手机定位开启 · 本次已保存 $saved 点"
                "REGISTER_FAILED"->"定位订阅失败，正在有限重试 · 本次已保存 $saved 点"
                "STARTING"->"等待首个 GPS 位置 · 本次已保存 $saved 点"
                "CALLBACK_INTERRUPTED"->"${age?.let { "最近 GPS $it" } ?: "尚未收到 GPS"} · 本次已保存 $saved 点"
                "SAVED","WAITING_MOVEMENT"->"GPS ${age ?: "刚刚"}${accuracy?.takeIf { it.isFinite() && it in 1f..35f }?.let { " · 精度 ${it.toInt()}米" } ?: ""} · 本次已保存 $saved 点"
                else->status.hint
            }
            return RecordingNotice(status.code,"足迹涂色 · ${status.title}",detail)
        }
    }
}

/** Status transitions bypass throttling; same-state changes are delivered at the next deadline. */
internal class RecordingNoticeGate {
    private var last:RecordingNotice?=null
    private var at=0L
    fun delay(notice:RecordingNotice,now:Long):Long? {
        if(last==notice) return null
        if(last==null || last?.urgencyKey!=notice.urgencyKey) return 0
        return (INTERVAL_MS-(now-at)).coerceAtLeast(0)
    }
    fun published(notice:RecordingNotice,now:Long) {last=notice;at=now}
    companion object {const val INTERVAL_MS=30_000L}
}
