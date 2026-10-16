package cn.walkpaint

/** Bounds future automatic snapshots without deleting any existing user backup. */
internal object BackupBudget {
    const val MAX_FILES=30
    const val MAX_TOTAL=128L*1024*1024
    const val MAX_FILE=40L*1024*1024
    const val MIN_FREE=32L*1024*1024
    data class Decision(val maxBytes:Long,val problem:String?=null)
    fun decide(count:Int,total:Long,targetBytes:Long,targetExists:Boolean,free:Long):Decision {
        if(count<0 || total<0 || targetBytes<0) return Decision(0,"无法核对备份占用，请导出备份")
        if((!targetExists && count>=MAX_FILES) || count>MAX_FILES || total>MAX_TOTAL)
            return Decision(0,"自动备份已达数量或容量预算，请导出备份；原快照保留")
        if(free<MIN_FREE) return Decision(0,"剩余空间不足，自动备份暂缓；请导出备份")
        val allowed=minOf(MAX_FILE,MAX_TOTAL-(total-targetBytes).coerceAtLeast(0))
        return if(allowed>0) Decision(allowed) else Decision(0,"自动备份已达容量预算，请导出备份")
    }
}

/** Only schedules work. Serialization and file I/O run on the separate backup executor. */
internal class BackupSchedule {
    private var busy=false
    private var closed=false
    private var lastAttempt:Long?=null
    @Synchronized fun start(timestamp:Long,elapsed:Long,lastSuccess:Long):Boolean {
        if(closed || busy || lastSuccess>0 && timestamp-lastSuccess in 0 until INTERVAL) return false
        if(lastAttempt?.let {elapsed-it in 0 until RETRY}==true) return false
        lastAttempt=elapsed;busy=true;return true
    }
    @Synchronized fun finished() {busy=false}
    @Synchronized fun close() {closed=true}
    companion object {
        const val INTERVAL=30*60_000L
        const val RETRY=5*60_000L
    }
}
