package cn.walkpaint

import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId

@Composable private fun MedalMark(unlocked:Boolean,modifier:Modifier=Modifier) {
    val ink=if(unlocked)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    Canvas(modifier.size(40.dp)) {
        val w=size.width;val h=size.height;val stroke=2.dp.toPx()
        drawLine(ink,Offset(w*.35f,h*.58f),Offset(w*.26f,h*.94f),stroke)
        drawLine(ink,Offset(w*.65f,h*.58f),Offset(w*.74f,h*.94f),stroke)
        drawCircle(ink,w*.29f,Offset(w*.5f,h*.36f),style=Stroke(stroke))
        if(unlocked){drawLine(ink,Offset(w*.38f,h*.36f),Offset(w*.47f,h*.45f),stroke);drawLine(ink,Offset(w*.47f,h*.45f),Offset(w*.63f,h*.27f),stroke)}
    }
}

@Composable fun GrowthEntry(stats:GrowthStats,modifier:Modifier=Modifier) {
    val context=LocalContext.current;val store=remember{GrowthStore(context)}
    var opened by rememberSaveable{mutableStateOf(false)}
    val days=store.days;val streak=GrowthRules.streak(days,LocalDate.now())
    val metrics=stats.metrics+listOf(
        GrowthMetric("visit","使用天数",days.size.toLong(),listOf(3,7,30),listOf("初来相伴","一周相伴","月度同行"),"天"),
        GrowthMetric("streak","连续使用",streak.toLong(),listOf(3,7),listOf("渐入佳境","七日同行"),"天"))
    val badges=GrowthRules.badges(metrics)
    var earned by remember{mutableStateOf(store.earned())}
    LaunchedEffect(badges){earned=store.settle(badges)}
    val points=badges.filter{it.id in earned}.sumOf{it.points}
    Surface(onClick={opened=true},modifier=modifier.fillMaxWidth().padding(vertical=6.dp),shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
            MedalMark(true,Modifier.size(32.dp))
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(stats.title,fontWeight=FontWeight.Medium)
                Text("Lv.${GrowthRules.level(points)} · ${badges.count{it.id in earned}} 枚徽章",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("查看",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
        }
    }
    if(opened) Dialog(onDismissRequest={opened=false},properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) {
            GrowthDashboard(stats,badges,earned,store,points){opened=false}
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun GrowthDashboard(stats:GrowthStats,badges:List<GrowthBadge>,earned:Map<String,Long>,store:GrowthStore,points:Int,onClose:()->Unit) {
    val today=LocalDate.now();val days=store.days
    val firstDate=Instant.ofEpochMilli(store.firstInstall).atZone(ZoneId.systemDefault()).toLocalDate()
    var goal by remember{mutableIntStateOf(store.goal(stats.weekGoals[1]))}
    var allBadges by rememberSaveable{mutableStateOf(false)}
    var timeline by rememberSaveable{mutableStateOf(false)}
    var information by remember{mutableStateOf(false)}
    val next=badges.filter{it.id !in earned}.maxByOrNull{it.progress}
    val colors=MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=6.dp),verticalAlignment=Alignment.CenterVertically) {
            TextButton(onClick=onClose){Text("返回")}
            Text(stats.title,Modifier.weight(1f),fontSize=17.sp,fontWeight=FontWeight.SemiBold)
            TextButton(onClick={information=true}){Text("说明")}
        }
        LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(horizontal=28.dp,vertical=24.dp),verticalArrangement=Arrangement.spacedBy(32.dp)) {
            item {Row(Modifier.fillMaxWidth().padding(vertical=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(20.dp)) {
                MedalMark(true,Modifier.size(48.dp))
                Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text("Lv.${GrowthRules.level(points)}",fontSize=26.sp,fontWeight=FontWeight.SemiBold)
                    Text("收获 ${badges.count{it.id in earned}} 枚徽章",fontSize=14.sp,color=colors.onSurfaceVariant)
                }
            }}
            item {Row(Modifier.fillMaxWidth()) {
                listOf("相伴" to "${java.time.temporal.ChronoUnit.DAYS.between(firstDate,today).coerceAtLeast(0)+1} 天","连续使用" to "${GrowthRules.streak(days,today)} 天").forEach{(label,value)->
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Text(label,fontSize=14.sp,color=colors.onSurfaceVariant)
                        Text(value,fontSize=22.sp,fontWeight=FontWeight.Medium)
                    }
                }
            }}
            item {HorizontalDivider(color=colors.outlineVariant.copy(alpha=.5f))}
            item {Column(verticalArrangement=Arrangement.spacedBy(14.dp)) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Text("本周小目标",Modifier.weight(1f),fontWeight=FontWeight.SemiBold,fontSize=18.sp)
                    Text("${stats.weekValue} / $goal",fontSize=15.sp,color=colors.primary)
                }
                LinearProgressIndicator(progress={(stats.weekValue.toFloat()/goal).coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth().height(4.dp))
                FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){stats.weekGoals.forEach{value->FilterChip(selected=goal==value,onClick={goal=value;store.goal(value,true)},label={Text("$value ${stats.weekUnit}",fontSize=12.sp)})}}
                if(stats.weekValue>=goal)Text("本周已达成",fontSize=12.sp,color=colors.primary)
            }}
            if(next!=null)item {Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text("下一枚徽章",fontSize=12.sp,color=colors.onSurfaceVariant)
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                    MedalMark(false)
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                        Text(next.title,fontSize=18.sp,fontWeight=FontWeight.Medium)
                        Text(next.detail,fontSize=12.sp,color=colors.onSurfaceVariant)
                    }
                }
                LinearProgressIndicator(progress={next.progress},modifier=Modifier.fillMaxWidth().height(4.dp))
                Text("${next.current} / ${next.target}",fontSize=12.sp,color=colors.onSurfaceVariant)
            }}
            item {Column {
                TextButton(onClick={allBadges=!allBadges},contentPadding=PaddingValues(0.dp)) {Text(if(allBadges)"收起徽章" else "查看全部徽章 · ${badges.size}")}
                TextButton(onClick={timeline=!timeline},contentPadding=PaddingValues(0.dp)) {Text(if(timeline)"收起成长记录" else "查看成长记录")}
            }}
            if(allBadges)badges.chunked(2).forEach{row->item {Row(horizontalArrangement=Arrangement.spacedBy(24.dp)) {row.forEach{badge->val unlocked=badge.id in earned
                Column(Modifier.weight(1f).padding(vertical=12.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    MedalMark(unlocked)
                    Text(badge.title,fontWeight=FontWeight.Medium,fontSize=15.sp)
                    Text(if(unlocked)"已获得" else badge.detail,fontSize=12.sp,color=colors.onSurfaceVariant)
                    if(!unlocked)LinearProgressIndicator(progress={badge.progress},modifier=Modifier.fillMaxWidth().height(3.dp))
                }
            };if(row.size==1)Spacer(Modifier.weight(1f))}}}
            if(timeline){
                item {Column(verticalArrangement=Arrangement.spacedBy(14.dp)) {
                    Text("最近 28 天",fontWeight=FontWeight.SemiBold,fontSize=18.sp)
                    Text("用过 ${days.size} 天 · 打开了 ${store.seconds/60} 分钟",fontSize=14.sp,color=colors.onSurfaceVariant)
                    Text("这台手机的初次安装：$firstDate",fontSize=13.sp,color=colors.onSurfaceVariant)
                    (27 downTo 0).toList().chunked(7).forEach{week->Row(horizontalArrangement=Arrangement.spacedBy(7.dp)){week.forEach{ago->val date=today.minusDays(ago.toLong());val active=date in days
                        Box(Modifier.weight(1f).height(24.dp).background(if(active)colors.primary else colors.surfaceContainerHighest,RoundedCornerShape(5.dp)).semantics{contentDescription="$date ${if(active)"使用过" else "未记录使用"}"})
                    }}}
                }}
                if(earned.isEmpty())item {Text("第一枚徽章正等着你。",fontSize=14.sp,color=colors.onSurfaceVariant)}
                badges.filter{it.id in earned}.sortedByDescending{earned[it.id]}.forEach{badge->item {Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    Text(badge.title,fontSize=15.sp)
                    Text(Instant.ofEpochMilli(earned.getValue(badge.id)).atZone(ZoneId.systemDefault()).toLocalDate().toString(),fontSize=12.sp,color=colors.onSurfaceVariant)
                }}}
            }
        }
    }
    if(information) AlertDialog(onDismissRequest={information=false},title={Text("成长说明")},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("平时使用就能收获徽章，拿到后一直保留。")
        Text("小目标每周一重新开始。不打卡，也不扣分。")
        Text("使用记录从新版开始累计，仅存在这台手机。换机或清除数据会丢失。")
    }},confirmButton={TextButton(onClick={information=false}){Text("知道了")}})
}

@Composable fun RecentTasksPreference() {
    val context=LocalContext.current;val store=remember{GrowthStore(context)}
    var hidden by remember{mutableStateOf(store.hidden)}
    Row(Modifier.fillMaxWidth().heightIn(min=64.dp).semantics{contentDescription="从最近任务中隐藏"}.toggleable(value=hidden,role=Role.Switch,onValueChange={hidden=it;store.hideRecents(it)}).padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.weight(1f)){Text("从最近任务中隐藏");Text("桌面仍能打开；不影响系统省电限制",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        Switch(hidden,onCheckedChange=null)
    }
}
