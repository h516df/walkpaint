package cn.walkpaint

import java.time.YearMonth

/** A read-only view over existing daily snapshots; never rewrites track IDs or coordinates. */
internal object FootprintMonths {
    fun days(days:List<DailyFootprint>,month:String):List<DailyFootprint> {
        require(YearMonth.parse(month).toString()==month)
        return days.filter {it.key.startsWith("$month-")}
    }
    fun available(days:List<DailyFootprint>,today:YearMonth=YearMonth.now()):List<String> =
        (days.map {it.key.take(7)}+today.toString()).distinct().sortedDescending()
}
