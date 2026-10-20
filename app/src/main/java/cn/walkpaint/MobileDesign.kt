package cn.walkpaint

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas as DrawCanvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.LocalDate
import kotlin.math.cos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun DesignStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(value, color = Ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.SansSerif, style = TabularFigures)
        Text(label, color = Muted, fontSize = 12.sp)
    }
}

@Composable
internal fun DesignSummary(state: TrackState) {
    Surface(color = Canvas, shape = CardShape, border = BorderStroke(1.dp, Line)) {
        Row(Modifier.fillMaxWidth().padding(vertical=20.dp),verticalAlignment=Alignment.CenterVertically) {
            DesignStat(exploredDistance(state.totalMeters),"累计里程",Modifier.weight(1f).padding(horizontal=12.dp))
            VerticalDivider(Modifier.height(44.dp),color=Line)
            DesignStat("${state.dayCount}","探索天数",Modifier.weight(1f).padding(horizontal=12.dp))
        }
    }
}

/**
 * 历史回放的界面状态。进度用取值函数而不是数值，这样 25fps 的推进只会重画进度条本身，
 * 不会带着整张地图和整页界面一起重组。
 */
internal class ReplayControls(
    val active: Boolean,
    val playing: Boolean,
    val progress: () -> Float,
    val onStart: () -> Unit,
    val onToggle: () -> Unit,
    val onClose: () -> Unit
)

@Composable
internal fun DesignMapOverlay(state: TrackState, day: String?, automatic: Boolean,
    title: String, healthy: Boolean, notice: String?, provider: String, onSettings: () -> Unit,
    onToggle: (Boolean) -> Unit, onLocate: () -> Unit, onShowAll: () -> Unit,
    showOsmCredit: Boolean, onZoom: (Int) -> Unit, mapContent: @Composable () -> Unit, regionContent:@Composable ()->Unit={},
    replay: ReplayControls? = null,bottomControlsInset:androidx.compose.ui.unit.Dp=94.dp,onExploreLandmarks:()->Unit={},explorationNotice:String?=null) {
    val uri = androidx.compose.ui.platform.LocalUriHandler.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compactControls=maxHeight<600.dp && LocalDensity.current.fontScale>1.3f
        mapContent()
        val chrome=Canvas
        val chromeInk=Ink
        if(day!=null) Surface(onClick=onShowAll,color=chrome,shape=RoundedCornerShape(16.dp),
            modifier=Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top=12.dp,start=16.dp,end=16.dp)) {
            Row(Modifier.padding(horizontal=16.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically,
                horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(day,color=chromeInk,fontSize=16.sp,fontWeight=FontWeight.SemiBold)
                    Text("当天足迹与迷雾",color=chromeInk.copy(alpha=.8f),fontSize=12.sp)
                }
                if(replay?.active==false) Surface(onClick=replay.onStart,color=Color(0xFF2E4C7E),
                    shape=RoundedCornerShape(10.dp)) {
                    Text("回放",color=Color(0xFFEAF1FF),fontSize=13.sp,fontWeight=FontWeight.Medium,
                        modifier=Modifier.padding(horizontal=10.dp,vertical=6.dp))
                }
                Text("返回全部探索",color=Accent,fontSize=13.sp)
            }
        }
        if(day==null) Column(Modifier.align(Alignment.TopStart).statusBarsPadding().padding(top=12.dp,start=16.dp,end=16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        regionContent()
        if(explorationNotice!=null) Surface(onClick=onExploreLandmarks,color=chrome,shape=RoundedCornerShape(12.dp)) {
            Text(explorationNotice,color=chromeInk,fontSize=12.sp,
                modifier=Modifier.padding(12.dp).heightIn(min=24.dp))
        }
        if(notice!=null) Surface(onClick=onSettings,color=chrome,shape=RoundedCornerShape(16.dp),
            modifier=Modifier.fillMaxWidth()) {
            Row(Modifier.padding(horizontal=16.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_location),null,tint=Accent,modifier=Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(notice,color=chromeInk,fontSize=13.sp,lineHeight=20.sp,modifier=Modifier.weight(1f))
                Text("›",color=chromeInk,fontSize=20.sp,modifier=Modifier.padding(start=8.dp))
            }
        }
        }
        if(day==null && replay?.active!=true) Surface(onClick=onExploreLandmarks,color=Canvas,
            shape=RoundedCornerShape(14.dp),modifier=Modifier.align(Alignment.BottomStart)
                .padding(start=16.dp,bottom=bottomControlsInset).heightIn(min=48.dp)) {
            Box(Modifier.padding(horizontal=12.dp,vertical=10.dp),contentAlignment=Alignment.Center) {
                Text("探索点",color=Accent,fontSize=14.sp,fontWeight=FontWeight.Medium)
            }
        }
        val controls: @Composable ()->Unit = {
            Surface(color=Canvas,shape=RoundedCornerShape(14.dp)) {
                if(compactControls) Row {MapZoomButtons(onZoom,true)}
                else Column {MapZoomButtons(onZoom,false)}
            }
            Surface(onClick=onLocate,color=skinPalette(LocalVisualSkin.current,false).brand,shape=RoundedCornerShape(14.dp),modifier=Modifier.size(48.dp)) {
                Box(Modifier.semantics { contentDescription="定位我" },contentAlignment=Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_location),null,tint=Color.White,modifier=Modifier.size(22.dp))
                }
            }
        }
        if(compactControls) Row(Modifier.align(Alignment.BottomEnd).padding(end=16.dp,bottom=bottomControlsInset),
            horizontalArrangement=Arrangement.spacedBy(12.dp),content= {controls()})
        else Column(Modifier.align(Alignment.BottomEnd).padding(end=16.dp,bottom=bottomControlsInset),
            verticalArrangement=Arrangement.spacedBy(12.dp),horizontalAlignment=Alignment.End,content={controls()})
        if(replay!=null && replay.active) {
            val progress=replay.progress().coerceIn(0f,1f)
            Surface(color=chrome,shape=RoundedCornerShape(16.dp),
                modifier=Modifier.align(Alignment.BottomStart).fillMaxWidth()
                    .padding(start=16.dp,end=if(compactControls)16.dp else 88.dp,bottom=bottomControlsInset+if(compactControls)60.dp else 0.dp)) {
                Column(Modifier.padding(horizontal=14.dp,vertical=10.dp),
                    verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,
                        horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        Surface(onClick=replay.onToggle,color=Color(0xFF2E4C7E),shape=RoundedCornerShape(10.dp)) {
                            Text(if(replay.playing) "暂停" else "继续",color=Color(0xFFEAF1FF),fontSize=14.sp,
                                modifier=Modifier.padding(horizontal=12.dp,vertical=8.dp))
                        }
                        Spacer(Modifier.weight(1f))
                        Text("${(progress*100).toInt()}%",color=chromeInk,fontSize=12.sp)
                        Surface(onClick=replay.onClose,color=Color.Transparent,shape=RoundedCornerShape(10.dp)) {
                            Text("关闭",color=Accent,fontSize=14.sp,
                                modifier=Modifier.padding(horizontal=4.dp,vertical=8.dp))
                        }
                    }
                    // A separate full-width track remains visible at 320dp and enlarged text.
                    Box(Modifier.fillMaxWidth().height(6.dp).background(Color(0xFF31405A),RoundedCornerShape(3.dp))) {
                        if(progress>0f) Box(Modifier.fillMaxWidth(progress).height(6.dp)
                            .background(Color(0xFF9CC2FF),RoundedCornerShape(3.dp)))
                    }
                }
            }
        }
        if (showOsmCredit) Text("© OpenStreetMap contributors · ODbL 1.0", color = Ink, fontSize = 10.sp,
            modifier = Modifier.align(Alignment.BottomStart).padding(start=4.dp,end=4.dp,bottom=28.dp).background(Canvas, RoundedCornerShape(4.dp))
                .clickable { uri.openUri("https://www.openstreetmap.org/copyright") }.padding(4.dp))
    }
}

@Composable
private fun MapZoomButtons(onZoom:(Int)->Unit,horizontal:Boolean) {
    listOf("放大地图" to 1,"缩小地图" to -1).forEachIndexed { index,(label,delta) ->
        if(index>0) {
            if(horizontal) VerticalDivider(Modifier.height(48.dp).padding(vertical=10.dp),color=Line)
            else HorizontalDivider(Modifier.width(48.dp).padding(horizontal=10.dp),color=Line)
        }
        Surface(onClick={onZoom(delta)},color=Color.Transparent,modifier=Modifier.size(48.dp)) {
            Box(Modifier.semantics {contentDescription=label},contentAlignment=Alignment.Center) {
                Text(if(delta>0) "+" else "−",color=Ink,fontSize=26.sp)
            }
        }
    }
}

/** The thumbnail is the saved route itself, with each original gap kept. No invented basemap. */
@Composable
internal fun RouteThumbnail(track: Track, modifier: Modifier = Modifier, color: Int = RoadPaintColor.DEFAULT) {
    // Distant disconnected records should not squash a short real walk into two invisible dots.
    // Full history remains available on the map; the preview explicitly shows one continuous part.
    val preview = remember(track) {
        RecordedTrail.visiblePaths(listOf(track)).maxByOrNull { Geo.distance(it) } ?: emptyList()
    }
    val lines = remember(preview) { preview.zipWithNext() }
    val background = CanvasSoft
    val pigment = Color(color)
    val faint = Line
    DrawCanvas(modifier.background(background, RoundedCornerShape(12.dp)).semantics { contentDescription = "最长连续路线段预览" }) {
        if (preview.isEmpty()) return@DrawCanvas
        val points = preview
        val lat0 = points.first().lat
        val lon0 = points.first().lon
        val scale = cos(Math.toRadians(lat0)).coerceAtLeast(.01)
        val coords = points.map { (it.lon - lon0) * scale to lat0 - it.lat }
        val minX = coords.minOf { it.first }; val maxX = coords.maxOf { it.first }
        val minY = coords.minOf { it.second }; val maxY = coords.maxOf { it.second }
        val fit = minOf(size.width * .78 / (maxX - minX).coerceAtLeast(.00005),
            size.height * .70 / (maxY - minY).coerceAtLeast(.00005))
        fun position(p: TrackPoint) = Offset(
            size.width / 2 + (((p.lon - lon0) * scale - (minX + maxX) / 2) * fit).toFloat(),
            size.height / 2 + ((lat0 - p.lat - (minY + maxY) / 2) * fit).toFloat())
        drawLine(faint, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), 1.dp.toPx())
        drawLine(faint, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 1.dp.toPx())
        lines.forEach { (a, b) -> drawLine(pigment, position(a), position(b), 3.dp.toPx(), StrokeCap.Round) }
        drawCircle(pigment, 3.dp.toPx(), position(points.first()))
        drawCircle(pigment, 4.dp.toPx(), position(points.last()))
    }
}

@Composable
internal fun DesignTripsPage(state: TrackState, roadColor: Int, onTrip: (Track) -> Unit,
    onExport: () -> Unit,onSkin:()->Unit={}) {
    val days = remember(state) { DailyFootprints.days(state) }
    val journal=rememberJournal()
    var searching by rememberSaveable {mutableStateOf(false)}
    var query by rememberSaveable {mutableStateOf("")}
    var starredOnly by rememberSaveable {mutableStateOf(false)}
    val filtered by produceState(days,days,journal.entries,query,starredOnly) {
        value=withContext(Dispatchers.Default){JournalSearch.filter(days,journal.entries,query,starredOnly)}
    }
    val tags=remember(days,journal.entries) {days.flatMap {journal.entries[it.key]?.tags.orEmpty()}
        .groupingBy {it}.eachCount().entries.sortedWith(compareByDescending<Map.Entry<String,Int>> {it.value}.thenBy {it.key}).take(8).map {it.key}}
    var detailKey by rememberSaveable { mutableStateOf<String?>(null) }
    var showStats by rememberSaveable { mutableStateOf(false) }
    val selected = days.firstOrNull { it.key == detailKey }
    if (selected != null) {
        DesignTripDetail(selected, roadColor, { detailKey = null }, { onTrip(selected.focusTrack()) }, onExport,journal)
        return
    }
    LazyColumn(Modifier.fillMaxSize().imePadding(), contentPadding = PaddingValues(horizontal=20.dp,vertical=12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { TrailHeader("足迹") {
            TextButton(onClick={showStats=!showStats}) { Text(if(showStats) "日历" else "统计") }
            TextButton(onClick={searching=!searching;if(!searching)query=""}){Text(if(searching)"收起查找" else "查找")}
            IconButton(onClick=onExport) { Icon(painterResource(R.drawable.ic_export),"导出全部足迹",tint=Pigment) }
        } }
        if(searching) item {OutlinedTextField(query,{query=it.take(200)},modifier=Modifier.fillMaxWidth(),
            label={Text("日期、标题、备注或标签")},singleLine=true,
            trailingIcon={if(query.isNotEmpty())TextButton(onClick={query=""}){Text("清空")}})}
        item {Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            FilterChip(selected=!starredOnly,onClick={starredOnly=false},label={Text("全部")})
            FilterChip(selected=starredOnly,onClick={starredOnly=true},label={Text("已收藏")})
            if(searching) tags.forEach {tag->FilterChip(selected=query=="#$tag",onClick={query=if(query=="#$tag")"" else "#$tag"},label={Text(tag)})}
        }}
        journal.error?.let {message->item {Text(message,color=MaterialTheme.colorScheme.error,fontSize=13.sp)}}
        if(journal.loading) item {LinearProgressIndicator(Modifier.fillMaxWidth())}
        item {
            val month=java.time.LocalDate.now().toString().take(7)
            val monthDays=remember(days,month) {days.filter {it.key.startsWith(month)}}
            Text("本月 ${monthDays.size} 天出发 · ${exploredDistance(monthDays.sumOf {it.meters})}",color=Muted,fontSize=14.sp,
                modifier=Modifier.padding(bottom=8.dp))
        }
        if (!searching && !starredOnly && showStats) item { RecapCard(RecapMaker.of(days, java.time.LocalDate.now().toString().take(7)),
            RecapMaker.of(days, java.time.LocalDate.now().toString().take(4))) }
        else if(!searching && !starredOnly) item { FootprintCalendar(days, DailyFootprints.key(System.currentTimeMillis())) { track ->
            detailKey = days.firstOrNull { it.tracks.any { original -> original.id == track.id } }?.key
                ?: track.points.firstOrNull()?.let { DailyFootprints.key(it.time) }
        } }
        if(days.isNotEmpty()) {
            item { TrailSectionLabel(if(searching)"查找结果 · ${filtered.size} 天" else if(starredOnly)"收藏的足迹 · ${filtered.size} 天" else "最近探索") }
            items(if(searching || starredOnly)filtered else days.take(5),key={"recent:${it.key}"}) { day ->
                val memo=journal.entries[day.key] ?: DayMemo()
                Surface(onClick={detailKey=day.key},color=Canvas,shape=RoundedCornerShape(16.dp)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.CenterVertically,
                        horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        RouteThumbnail(day.focusTrack(),Modifier.size(56.dp),roadColor)
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                            Text(memo.title.ifBlank {day.key},color=Ink,fontSize=15.sp,fontWeight=FontWeight.Medium,maxLines=1,
                                overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            Text((if(memo.title.isBlank())"" else "${day.key} · ")+exploredDistance(day.meters),color=Muted,fontSize=13.sp)
                        }
                        Text(if(memo.starred)"★" else "›",color=if(memo.starred)Pigment else Faint,fontSize=22.sp)
                    }
                }
            }
            if((searching || starredOnly) && filtered.isEmpty()) item {Text(if(starredOnly && query.isBlank())"还没有收藏。打开某一天的足迹，就可以收藏。" else "没有找到匹配的足迹，试试其他日期或标签。",color=Muted,fontSize=14.sp)}
        }
        if (days.isEmpty()) item {
            Text("第一段足迹，等你出发。开启自动记录后，已保存的路线会出现在日历里。", color = Muted, fontSize = 14.sp)
        }
    }
}

@Composable
private fun DesignTripDetail(day: DailyFootprint, color: Int, onBack: () -> Unit, onMap: () -> Unit, onExport: () -> Unit,journal:JournalView) {
    BackHandler(onBack = onBack)
    val summary = remember(day) { TrailSummary.of(day.tracks) }
    val integrity=remember(day) { TrailIntegrity.of(day.tracks) }
    var showIntegrity by remember(day.key) { mutableStateOf(false) }
    var postcard by rememberSaveable(day.key) {mutableStateOf(false)}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=20.dp,vertical=12.dp),
        verticalArrangement=Arrangement.spacedBy(14.dp)) {
        TrailHeader("当天足迹",day.key,onBack)
        RouteThumbnail(day.focusTrack(),Modifier.fillMaxWidth().height(180.dp),color)
        DayJournalActions(day,journal){postcard=true}
        TrailPanel {
            Row(Modifier.fillMaxWidth()) {
                DesignStat(exploredDistance(summary.meters),"里程",Modifier.weight(1f))
                DesignStat(summary.duration,"有效记录时长",Modifier.weight(1f))
            }
            HorizontalDivider(color=Line)
            Text("${summary.points} 个足迹点，${day.segments} 段记录",color=Muted,fontSize=13.sp)
        }
        Button(onClick=onMap,shape=PanelShape,modifier=Modifier.fillMaxWidth().heightIn(min=50.dp)) {
            Icon(painterResource(R.drawable.ic_map),null,modifier=Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp));Text("在地图查看这天的足迹")
        }
        TextButton(onClick={showIntegrity=!showIntegrity},modifier=Modifier.fillMaxWidth()) {
            Text(if(showIntegrity) "收起记录完整性" else "记录完整性")
        }
        if(showIntegrity) TrailPanel { Text("预览显示最长连续路线；定位断档保留分段。\n"+integrity.report(),color=Muted,fontSize=13.sp,lineHeight=21.sp) }
        OutlinedButton(onClick=onExport,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) { Text("导出全部足迹备份") }
    }
    if(postcard) PostcardDialog(day,journal.entries[day.key] ?: DayMemo(),color){postcard=false}
}

@Composable
internal fun DesignOnboarding(mapConsentRequired: Boolean = false, onPrivacy: () -> Unit = {},
    onEnable: () -> Unit, onSkip: () -> Unit) {
    Dialog(onDismissRequest = {if(!mapConsentRequired)onSkip()}, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(color = CanvasSoft, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp)) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Icon(painterResource(R.drawable.ic_route), null, tint = Pigment, modifier = Modifier.padding(top = 8.dp).size(36.dp))
                    Text("足迹涂色", color = Muted, fontSize = 13.sp)
                    Text("走到哪里，\n就点亮哪里。", color = Ink, fontSize = 32.sp, lineHeight = 42.sp, fontWeight = FontWeight.Bold)
                    Text("允许定位后，走过的地方会在地图上慢慢亮起来。", color = Muted, fontSize = 15.sp, lineHeight = 24.sp)
                    Text(if(mapConsentRequired) "默认高德地图，无需注册。足迹保存在本机。" else "足迹保存在本机，随时可以暂停记录。",
                        color = Ink, fontSize = 15.sp, lineHeight = 24.sp)
                    Text("开启后，应用会在后台和锁屏时使用定位保存足迹，可能增加耗电。记录提示由系统管理，可随时暂停。",
                        color = Muted, fontSize = 13.sp, lineHeight = 21.sp)
                    if(mapConsentRequired) {
                        Text(AmapDisclosure.SUMMARY, color = Muted, fontSize = 13.sp, lineHeight = 21.sp)
                        TextButton(onClick = onPrivacy, contentPadding = PaddingValues(0.dp)) { Text("阅读高德隐私政策") }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text("下一步选择“精确位置”和“使用应用时允许”。后台增强设置可稍后再开。", color = Muted, fontSize = 12.sp, lineHeight = 20.sp)
                Spacer(Modifier.height(12.dp))
                Button(onClick = onEnable, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Text("同意并开始", fontWeight = FontWeight.SemiBold)
                }
                TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("暂不开始") }
            }
        }
    }
}

/**
 * First-run map disclosure. Deliberately a full screen with two explicit buttons rather than a
 * dismissible dialog: a first-time user who taps outside a dialog, or presses back, used to be
 * left with a permanently empty map area and no explanation. Here a decision is always recorded
 * by a button press, and the "no map" choice states what will happen instead.
 */
@Composable
internal fun DesignMapConsent(onAgree: () -> Unit, onDecline: () -> Unit, onOpenPolicy: () -> Unit) {
    Dialog(onDismissRequest = {}, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(color = CanvasSoft, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Icon(painterResource(R.drawable.ic_route), null, tint = Pigment, modifier = Modifier.padding(top = 24.dp).size(42.dp))
                Text("还差一步", color = Muted, fontSize = 13.sp)
                Text("地图需要联网，\n这件事由你决定。", color = Ink, fontSize = 28.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold)
                OnboardingFeature(R.drawable.ic_lock, "用什么地图", "使用高德开放平台地图与搜索 SDK（北京高德图强科技有限公司）显示底图和地名。")
                OnboardingFeature(R.drawable.ic_notification, "它会看到什么", "地图服务可能收到经纬度、IP/网络、GNSS、设备参数、系统和传感器信息。")
                OnboardingFeature(R.drawable.ic_route, "你的足迹在哪", "足迹数据库始终只保存在本机，地图服务看不到你的足迹记录，也不会因为拒绝而停止记录。")
                Button(onClick = onAgree, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Text("同意并启用地图", fontWeight = FontWeight.SemiBold)
                }
                TextButton(onClick = onDecline, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("暂不用地图") }
                Text("选“暂不用地图”后，地图页会显示文字提示，你随时可以在设置里开启；自动记录不受影响。", color = Muted, fontSize = 12.sp, lineHeight = 20.sp)
                TextButton(onClick = onOpenPolicy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("阅读高德隐私政策") }
            }
        }
    }
}

@Composable
private fun OnboardingFeature(icon: Int, title: String, detail: String) {
    Surface(color = Canvas, shape = PanelShape, border = BorderStroke(1.dp, Line)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(painterResource(icon), null, tint = Pigment, modifier = Modifier.size(22.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(detail, color = Muted, fontSize = 13.sp, lineHeight = 20.sp)
            }
        }
    }
}
