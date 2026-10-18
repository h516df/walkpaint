package cn.walkpaint

import java.time.LocalDate

data class GrowthMetric(val key:String,val label:String,val value:Long,val targets:List<Long>,val names:List<String>,val unit:String)
data class GrowthStats(val title:String,val subtitle:String,val metrics:List<GrowthMetric>,val weekValue:Long,val weekUnit:String,val weekGoals:List<Int>)
data class GrowthBadge(val id:String,val title:String,val detail:String,val current:Long,val target:Long,val points:Int) {
    val reached get()=current>=target
    val progress get()=(current.toFloat()/target).coerceIn(0f,1f)
}
object GrowthRules {
    fun badges(metrics:List<GrowthMetric>)=metrics.flatMap {m->m.targets.mapIndexed {index,target->
        GrowthBadge("${m.key}_$target",m.names[index],"${m.label} $target ${m.unit}",m.value.coerceAtLeast(0),target,(index+1)*25)
    }}
    fun streak(days:Set<LocalDate>,today:LocalDate):Int {
        var cursor=if(today in days)today else today.minusDays(1);var count=0
        while(cursor in days){count++;cursor=cursor.minusDays(1)}
        return count
    }
    fun weekStart(today:LocalDate)=today.minusDays((today.dayOfWeek.value-1).toLong())
    fun level(points:Int)=1+points.coerceAtLeast(0)/100
    fun unlocked(existing:Map<String,Long>,badges:List<GrowthBadge>,now:Long)=existing.toMutableMap().apply {
        badges.filter{it.reached}.forEach{putIfAbsent(it.id,now)}
    }.toMap()
}
