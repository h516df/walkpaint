package cn.walkpaint

/** Fixed vocabulary only: never persist callback text, exception messages or location data. */
internal enum class ProblemCode(val label:String, val hint:String, val critical:Boolean=true) {
    RECORDING_STARTED("开始记录","记录服务已经启动"),
    RECORDING_STOPPED("停止记录","服务已停止，可在“我的”查看开关"),
    GPS_REGISTERED("定位已订阅","订阅成功不等于已经定位"),
    GPS_GAP("定位回调有间隔","记录可能不连续，请结合当前定位状态检查"),
    GPS_FILTERED("位置未采用","位置精度、时间或移动可靠性未满足记录要求",false),
    GPS_ACCURACY("定位精度不足","当前精度未满足保存要求",false),
    GPS_STALE("定位时间不可靠","过期或重复时间的位置未写入足迹",false),
    GPS_STATIONARY("移动距离不足","静止或较小移动不会反复增加足迹",false),
    GPS_JUMP("位置跳变未采用","不可靠的大幅跳点被拒绝，避免涂错道路",false),
    RECORDING_FAILED("记录遇到异常","查看权限与当前记录状态，历史足迹仍保留"),
    PROCESS_EXIT("系统报告进程退出","这是系统原因线索，不能据此判断厂商限制"),
    RECOVERY("记录服务恢复","上一次服务结束信息不完整"),
    PERMISSION_DENIED("记录授权未完成","未获得的授权会影响记录，按需重新设置"),
    ROAD_DOWNLOAD_FAILED("地图数据下载失败","可稍后重试，或导入地图文件"),
    ROAD_IMPORT_FAILED("地图文件未导入","请检查文件格式，原道路与足迹保留"),
    BACKUP_FAILED("备份操作未完成","请检查目标文件和存储空间"),
    MAP_UNAVAILABLE("地图暂不可用","可使用离线地图，记录仍独立保存");

    val problem get() = this !in setOf(RECORDING_STARTED,RECORDING_STOPPED,GPS_REGISTERED,RECOVERY)
    companion object {
        fun fromRecording(kind:String, reason:String, gap:Long?):ProblemCode? = when(kind) {
            "start" -> RECORDING_STARTED
            "stop" -> RECORDING_STOPPED
            "register" -> if(reason in setOf("GPS_REGISTERED","GPS_RETRY_REGISTERED")) GPS_REGISTERED else RECORDING_FAILED
            "gps" -> if(gap!=null) GPS_GAP else null
            "filtered" -> when(reason) {
                "MISSING_ACCURACY","UNRELIABLE_ACCURACY" -> GPS_ACCURACY
                "STALE_FIX","NON_INCREASING_TIME" -> GPS_STALE
                "STATIONARY_OR_SMALL_MOVE" -> GPS_STATIONARY
                "JUMP_OR_TOO_FAST" -> GPS_JUMP
                else -> GPS_FILTERED
            }
            "failure" -> RECORDING_FAILED
            "exit" -> PROCESS_EXIT
            "recovery" -> RECOVERY
            else -> null
        }
    }
}

internal data class ProblemEntry(val code:ProblemCode,val first:Long,val last:Long,val count:Long)
internal class ProblemHistory(encoded:String="", var enabled:Boolean=true, now:Long=System.currentTimeMillis()) {
    private val entries=mutableListOf<ProblemEntry>()
    var dirty=false; private set
    init {
        // Bound input before parsing; an unknown or corrupted row cannot become report text.
        if(encoded.toByteArray(Charsets.UTF_8).size<=MAX_BYTES) encoded.lineSequence().take(MAX_EVENTS).forEach { line ->
            val f=line.split('|')
            if(f.size==4) runCatching {
                val code=ProblemCode.valueOf(f[0]); val first=f[1].toLong();val last=f[2].toLong();val count=f[3].toLong()
                if(first>0 && last>=first && last<=now && count in 1..MAX_COUNT) entries+=ProblemEntry(code,first,last,count)
            }
        }
        trim(now)
    }
    fun record(code:ProblemCode,at:Long) {
        trim(at)
        if(!enabled || at<=0) return
        val index=entries.indexOfLast { it.code==code && at-it.last in 0L..AGGREGATE_MS && at-it.first in 0L..AGGREGATE_MS }
        if(index>=0) { val old=entries.removeAt(index);entries+=old.copy(last=at,count=(old.count+1).coerceAtMost(MAX_COUNT)) }
        else entries+=ProblemEntry(code,at,at,1)
        dirty=true; trim(at)
    }
    fun snapshot(now:Long):List<ProblemEntry> { trim(now); return entries.toList().asReversed() }
    fun encode():String = entries.joinToString("\n") { "${it.code.name}|${it.first}|${it.last}|${it.count}" }
    fun setCollection(value:Boolean) { enabled=value;dirty=true }
    fun clear() { entries.clear();dirty=true }
    fun persisted() { dirty=false }
    private fun trim(now:Long) {
        if(entries.removeAll { it.first<now-KEEP_MS || it.last>now }) dirty=true
        while(entries.size>MAX_EVENTS || encode().toByteArray(Charsets.UTF_8).size>MAX_BYTES) { entries.removeAt(0);dirty=true }
    }
    companion object {
        const val MAX_EVENTS=500;const val MAX_BYTES=256*1024;const val MAX_COUNT=1_000_000_000L
        const val KEEP_MS=7*24*60*60_000L;const val AGGREGATE_MS=5*60_000L
    }
}
