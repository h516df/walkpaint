package cn.walkpaint

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.YearMonth

@Composable internal fun FootprintMonthPicker(days:List<DailyFootprint>,monthKey:String,onPick:(String)->Unit) {
    val month=YearMonth.parse(monthKey)
    val current=YearMonth.now()
    val monthDays=remember(days,monthKey){FootprintMonths.days(days,monthKey)}
    val available=remember(days,current){FootprintMonths.available(days,current)}
    var choosing by rememberSaveable {mutableStateOf(false)}
    Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            TextButton(onClick={onPick(month.minusMonths(1).toString())},
                modifier=Modifier.semantics {contentDescription="上个月"}) {Text("‹",fontSize=24.sp)}
            TextButton(onClick={choosing=true},modifier=Modifier.weight(1f).semantics {contentDescription="选择月份"}) {
                Text("${month.year}年${month.monthValue}月 ▾",color=Ink,fontWeight=FontWeight.SemiBold)
            }
            TextButton(enabled=month<current,onClick={onPick(month.plusMonths(1).toString())},
                modifier=Modifier.semantics {contentDescription="下个月"}) {Text("›",fontSize=24.sp)}
        }
        Text("${monthDays.size} 天出发 · ${exploredDistance(monthDays.sumOf {it.meters})}",color=Muted,fontSize=14.sp)
        if(month!=current) TextButton(onClick={onPick(current.toString())},contentPadding=PaddingValues(horizontal=0.dp)) {Text("回到本月")}
    }
    if(choosing) AlertDialog(onDismissRequest={choosing=false},title={Text("选择月份")},
        text={LazyColumn(Modifier.fillMaxWidth().heightIn(max=360.dp)) {
            items(available,key={it}) {key->
                val ym=YearMonth.parse(key)
                TextButton(onClick={onPick(key);choosing=false},modifier=Modifier.fillMaxWidth()) {
                    Text("${ym.year}年${ym.monthValue}月"+if(key==current.toString())" · 本月" else "",
                        fontWeight=if(key==monthKey)FontWeight.Bold else FontWeight.Normal)
                }
            }
        }},confirmButton={TextButton(onClick={choosing=false}){Text("取消")}})
}
