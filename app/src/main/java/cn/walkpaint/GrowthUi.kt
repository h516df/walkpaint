package cn.walkpaint

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.res.painterResource
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
import androidx.compose.ui.draw.rotate
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

@Composable internal fun MedalMark(unlocked:Boolean,modifier:Modifier=Modifier) {
    val ink=if(unlocked)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    Canvas(modifier) {
        val w=size.width;val h=size.height;val stroke=2.dp.toPx()
        drawLine(ink,Offset(w*.35f,h*.58f),Offset(w*.26f,h*.94f),stroke)
        drawLine(ink,Offset(w*.65f,h*.58f),Offset(w*.74f,h*.94f),stroke)
        drawCircle(ink,w*.29f,Offset(w*.5f,h*.36f),style=Stroke(stroke))
        if(unlocked){drawLine(ink,Offset(w*.38f,h*.36f),Offset(w*.47f,h*.45f),stroke);drawLine(ink,Offset(w*.47f,h*.45f),Offset(w*.63f,h*.27f),stroke)}
    }
}

@Composable fun GrowthEntry(stats:GrowthStats,state:TrackState,roadColor:Int,modifier:Modifier=Modifier) {
    val context=LocalContext.current;val store=remember{GrowthStore(context)}
    var opened by rememberSaveable{mutableStateOf(false)}
    var regionRetry by remember{mutableIntStateOf(0)}
    val journey=rememberRegionJourney(state,opened,regionRetry)
    val regionalBadges=RegionJourneyRules.badges(journey.visits)
    val days=store.days;val streak=GrowthRules.streak(days,LocalDate.now())
    val metrics=stats.metrics+listOf(
        GrowthMetric("visit","使用天数",days.size.toLong(),listOf(3,7,30),listOf("初来相伴","一周相伴","月度同行"),"天"),
        GrowthMetric("streak","连续使用",streak.toLong(),listOf(3,7),listOf("渐入佳境","七日同行"),"天"))
    val badges=GrowthRules.badges(metrics)+regionalBadges
    var earned by remember{mutableStateOf(store.earned())}
    LaunchedEffect(badges){earned=store.settle(badges)}
    Surface(onClick={opened=true},modifier=modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp),color=Canvas) {
        Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
            val recent=state.realTracks.lastOrNull()
            if(recent!=null) RouteThumbnail(recent,Modifier.size(64.dp),roadColor)
            else Icon(painterResource(R.drawable.ic_route),null,tint=Pigment,modifier=Modifier.size(32.dp))
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                Text(stats.title,fontWeight=FontWeight.Medium,fontSize=17.sp)
                Text(if(state.dayCount>0)"${state.dayCount} 天足迹 · 继续探索山河" else "从第一次出发开始",fontSize=13.sp,color=Muted)
            }
            Icon(painterResource(R.drawable.ic_back),null,Modifier.size(16.dp).rotate(180f),tint=Muted)
        }
    }
    if(opened) Dialog(onDismissRequest={opened=false},properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) {
            GrowthDashboard(stats,state,roadColor,badges,earned,store,journey,{regionRetry++}){opened=false}
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun GrowthDashboard(stats:GrowthStats,state:TrackState,roadColor:Int,badges:List<GrowthBadge>,earned:Map<String,Long>,store:GrowthStore,journey:JourneyView,onRegionRetry:()->Unit,onClose:()->Unit) {
    val today=LocalDate.now();val days=store.days
    val firstDate=Instant.ofEpochMilli(store.firstInstall).atZone(ZoneId.systemDefault()).toLocalDate()
    var goal by remember{mutableIntStateOf(store.goal(stats.weekGoals[1]))}
    var allBadges by rememberSaveable{mutableStateOf(false)}
    var timeline by rememberSaveable{mutableStateOf(false)}
    var goalOpen by rememberSaveable{mutableStateOf(false)}
    var information by remember{mutableStateOf(false)}
    var memories by rememberSaveable{mutableStateOf(false)}
    val regionBadges=badges.filter{it.id.startsWith("region_")}
    val displayedBadges=if(memories)badges else regionBadges
    val collection=displayedBadges.filter{it.id in earned}.sortedByDescending{earned[it.id]}
    val next=displayedBadges.filter{it.id !in earned}.maxByOrNull{it.progress}
    val colors=MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=6.dp),verticalAlignment=Alignment.CenterVertically) {
            TextButton(onClick=onClose){Text("返回")}
            Text(stats.title,Modifier.weight(1f),fontSize=17.sp,fontWeight=FontWeight.SemiBold)
            TextButton(onClick={information=true}){Text("说明")}
        }
        LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(horizontal=24.dp,vertical=20.dp),verticalArrangement=Arrangement.spacedBy(24.dp)) {
            item {RegionJourneyPanel(state,journey,onRegionRetry)}
            item {HorizontalDivider(color=colors.outlineVariant.copy(alpha=.5f))}
            item {Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text(if(memories)"沿途纪念" else "山河成就",Modifier.weight(1f),fontWeight=FontWeight.SemiBold,fontSize=18.sp)
                Text("${collection.size} 枚",fontSize=13.sp,color=colors.onSurfaceVariant)
            }}
            if(collection.isEmpty()) item {Text(if(memories)"沿途的纪念会留在这里。" else "三地、七天，开启第一程寻迹。",fontSize=14.sp,color=colors.onSurfaceVariant)}
            (if(allBadges)displayedBadges else collection.take(3)).forEach {badge->item(key=badge.id) {
                val unlocked=badge.id in earned
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(16.dp),verticalAlignment=Alignment.CenterVertically) {
                    MedalMark(unlocked,Modifier.size(28.dp))
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                        Text(badge.title,fontWeight=FontWeight.Medium,fontSize=16.sp)
                        Text(if(unlocked)Instant.ofEpochMilli(earned.getValue(badge.id)).atZone(ZoneId.systemDefault()).toLocalDate().toString() else badge.detail,fontSize=13.sp,color=colors.onSurfaceVariant)
                    }
                    Text(if(unlocked)"已收藏" else "未解锁",fontSize=12.sp,color=colors.onSurfaceVariant)
                }
            }}
            item {TextButton(onClick={allBadges=!allBadges},contentPadding=PaddingValues(0.dp)) {Text(if(allBadges)"收起徽章" else "查看全部徽章 · ${displayedBadges.size}")}}
            if(next!=null && !allBadges)item {Text("下一枚 · ${next.title}\n${next.detail}",fontSize=14.sp,color=colors.onSurfaceVariant)}
            item {HorizontalDivider(color=colors.outlineVariant.copy(alpha=.5f))}
            item {TextButton(onClick={memories=!memories;allBadges=false},contentPadding=PaddingValues(0.dp)){Text(if(memories)"返回山河成就" else "里程与使用纪念")}}
            item {Column(Modifier.animateContentSize(),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                TextButton(onClick={goalOpen=!goalOpen},contentPadding=PaddingValues(0.dp)) {Text(if(goalOpen)"收起本周小目标" else "给自己一个小目标")}
                if(goalOpen) {
                    Text("本周小目标",fontWeight=FontWeight.Medium,fontSize=17.sp)
                    Text(if(stats.weekValue>=goal)"本周已达成 · 走过 ${stats.weekValue} ${stats.weekUnit}" else "本周走过 ${stats.weekValue} / $goal ${stats.weekUnit}",fontSize=14.sp,color=colors.onSurfaceVariant)
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){stats.weekGoals.forEach{value->FilterChip(selected=goal==value,onClick={goal=value;store.goal(value,true)},label={Text("$value ${stats.weekUnit}")})}}
                    Text("随心选，不打卡，也不扣分。",fontSize=13.sp,color=colors.onSurfaceVariant)
                }
                TextButton(onClick={timeline=!timeline},contentPadding=PaddingValues(0.dp)) {Text(if(timeline)"收起成长记录" else "查看成长记录")}
            }}
            if(timeline){
                item {Column(verticalArrangement=Arrangement.spacedBy(14.dp)) {
                    Text("最近 28 天",fontWeight=FontWeight.SemiBold,fontSize=18.sp)
                    Text("相伴 ${java.time.temporal.ChronoUnit.DAYS.between(firstDate,today).coerceAtLeast(0)+1} 天 · 用过 ${days.size} 天",fontSize=14.sp,color=colors.onSurfaceVariant)
                    Text("这台手机的初次安装：$firstDate",fontSize=13.sp,color=colors.onSurfaceVariant)
                    Text("连续使用 ${GrowthRules.streak(days,today)} 天 · 打开了 ${store.seconds/60} 分钟",fontSize=13.sp,color=colors.onSurfaceVariant)
                    (27 downTo 0).toList().chunked(7).forEach{week->Row(horizontalArrangement=Arrangement.spacedBy(7.dp)){week.forEach{ago->val date=today.minusDays(ago.toLong());val active=date in days
                        Box(Modifier.weight(1f).height(24.dp).background(if(active)colors.primary else colors.surfaceContainerHighest,RoundedCornerShape(5.dp)).semantics{contentDescription="$date ${if(active)"使用过" else "未记录使用"}"})
                    }}}
                }}
            }
        }
    }
    if(information) AlertDialog(onDismissRequest={information=false},title={Text("行记说明")},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("旧足迹会逐步补全地区；抽样可能遗漏短暂经过的地方。")
        Text("打开时，高德会处理部分历史位置来识别地区；离开即停止新查询。")
        Text("地区成就按实际记录日与到访地累计；步行和驾车均计入，不用公里数换算。")
        Text("小目标每周一重新开始。不打卡，也不扣分。")
        Text("使用纪念仅存在这台手机。换机或清除数据会丢失。")
    }},confirmButton={TextButton(onClick={information=false}){Text("知道了")}})
}

@Composable fun RecentTasksPreference() {
    val context=LocalContext.current;val store=remember{GrowthStore(context)}
    var hidden by remember{mutableStateOf(store.hidden)}
    Row(Modifier.fillMaxWidth().heightIn(min=64.dp).semantics{contentDescription="后台隐藏"}.toggleable(value=hidden,role=Role.Switch,onValueChange={hidden=it;store.hideRecents(it)}).padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.weight(1f)){Text("后台隐藏");Text("不显示最近任务卡片，桌面仍能打开",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        Switch(hidden,onCheckedChange=null)
    }
}
