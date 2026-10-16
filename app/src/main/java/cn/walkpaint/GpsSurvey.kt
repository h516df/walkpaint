package cn.walkpaint

/** Reduced 60-second evidence. No coordinates, satellite identifiers, NMEA or measurements. */
internal class GpsSurvey(private val start:Long,private val recordingRunning:Boolean=true) {
    var continuous=0;private set
    var probe=0;private set
    private var visible=0
    private var used=0
    private var observations=0
    private var cn0Count=0
    private var cn0Total=0.0
    private var cn0Min=Double.POSITIVE_INFINITY
    private var cn0Max=0.0
    private var ephemeris=0
    private var finished=false
    private var finishedAt:Long?=null
    private val timeline=ArrayList<String>()
    fun active(now:Long)=!finished && now-start in 0 until 60_000L
    fun satellites(now:Long,visible:Int,used:Int,cn0:List<Double>,ephemeris:Int) {
        if(!active(now)) return
        this.visible=visible;this.used=used;this.ephemeris=ephemeris;observations++
        cn0.filter { it.isFinite() && it>=0 }.forEach {
            cn0Count++;cn0Total+=it;cn0Min=minOf(cn0Min,it);cn0Max=maxOf(cn0Max,it)
        }
    }
    fun fix(now:Long,continuous:Boolean) { if(active(now)) { if(continuous) this.continuous++ else probe++ } }
    fun event(now:Long,text:String) {
        if(active(now) && timeline.size<20) timeline.add("${(now-start)/1000} 秒：${text.take(80)}")
    }
    fun stop(now:Long,reason:String) { event(now,reason);finished=true;finishedAt=now }
    fun report(now:Long):String {
        val elapsed=(((finishedAt ?: now)-start).coerceIn(0,60_000)/1000)
        val conclusion=when {
            continuous>0 -> "短测期间收到持续 GPS 回调；能否保存还需看精度和移动情况。"
            probe>0 && !recordingRunning -> "短测拿到一次 GPS 位置；自动记录仍保持暂停。"
            probe>0 -> "独立探测收到定位，持续监听尚未收到；不能宣称连续记录恢复。"
            observations>0 && used==0 -> "可见卫星，但参与定位为 0；本次尚未获得 GPS，无法单凭此结果区分信号或系统订阅问题。"
            else -> "未获得可用定位证据；空结果不能证明没有卫星。"
        }
        val signal=if(cn0Count==0) "未知" else "最低 %.1f / 平均 %.1f / 最高 %.1f dB-Hz".format(java.util.Locale.ROOT,cn0Min,cn0Total/cn0Count,cn0Max)
        return "${if(active(now)) "短时定位诊断进行中" else "短时定位诊断结束"} · $elapsed/60 秒\n"+
            "$conclusion\n卫星观测 $observations 次 · 最近可见 $visible / 参与 $used / 有星历 $ephemeris\n"+
            "C/N0：$signal\n持续回调 $continuous / 独立探测 $probe\n"+timeline.joinToString("\n")+
            "\n只含短测计数和相对时间；不含坐标、卫星编号或设备标识。"
    }
}

internal object ShortGpsSurvey {
    private var survey:GpsSurvey?=null
    private fun now()=android.os.SystemClock.elapsedRealtime()
    @Synchronized fun start(recordingRunning:Boolean) { survey=GpsSurvey(now(),recordingRunning) }
    @Synchronized fun active()=survey?.active(now())==true
    @Synchronized fun event(reason:String) { survey?.event(now(),when(reason) {
        "PROBE_FRESH_GPS" -> "独立探测收到新 GPS 位置"
        "PROBE_TIMEOUT" -> "独立探测15秒内未获得新定位"
        "PROBE_SUBSCRIPTION_FAILED" -> "独立探测订阅失败"
        else -> reason
    }) }
    @Synchronized fun fix(continuous:Boolean) { survey?.fix(now(),continuous) }
    @Synchronized fun satellites(s:android.location.GnssStatus) {
        survey?.satellites(now(),s.satelliteCount,(0 until s.satelliteCount).count { s.usedInFix(it) },
            (0 until s.satelliteCount).map { s.getCn0DbHz(it).toDouble() },
            (0 until s.satelliteCount).count { s.hasEphemerisData(it) })
    }
    @Synchronized fun stop(reason:String) { survey?.stop(now(),reason) }
    @Synchronized fun report():String=survey?.report(now()) ?: ""
}
