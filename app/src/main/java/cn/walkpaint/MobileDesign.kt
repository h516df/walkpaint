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

@Composable
internal fun DesignMapOverlay(state: TrackState, day: String?, automatic: Boolean,
    title: String, healthy: Boolean, notice: String?, provider: String, onSettings: () -> Unit,
    onToggle: (Boolean) -> Unit, onLocate: () -> Unit, onShowAll: () -> Unit,
    showOsmCredit: Boolean, onZoom: (Int) -> Unit, mapContent: @Composable () -> Unit, regionContent:@Composable ()->Unit={}) {
    val uri = androidx.compose.ui.platform.LocalUriHandler.current
    Box(Modifier.fillMaxSize()) {
        mapContent()
        val chrome=Color(0xFF172334)
        val chromeInk=Color(0xFFE9EFFA)
        if(day!=null) Surface(onClick=onShowAll,color=chrome,shape=RoundedCornerShape(16.dp),
            modifier=Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top=12.dp,start=16.dp,end=16.dp)) {
            Row(Modifier.padding(horizontal=16.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically,
                horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(day,color=chromeInk,fontSize=16.sp,fontWeight=FontWeight.SemiBold)
                    Text("当天足迹与迷雾",color=chromeInk.copy(alpha=.8f),fontSize=12.sp)
                }
                Text("返回全部探索",color=Color(0xFFB8D3FF),fontSize=13.sp)
            }
        }
        if(day==null) Column(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top=12.dp,start=16.dp,end=16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        regionContent()
        if(notice!=null) Surface(onClick=onSettings,color=chrome,shape=RoundedCornerShape(16.dp),
            modifier=Modifier.fillMaxWidth()) {
            Row(Modifier.padding(horizontal=16.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_location),null,tint=Color(0xFFB8D3FF),modifier=Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(notice,color=chromeInk,fontSize=13.sp,lineHeight=20.sp,modifier=Modifier.weight(1f))
                Text("›",color=chromeInk,fontSize=20.sp,modifier=Modifier.padding(start=8.dp))
            }
        }
        }
        Column(Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end=16.dp,bottom=94.dp),
            verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Surface(color=Color.White,shape=RoundedCornerShape(14.dp)) {
                Column {
                    listOf("放大地图" to 1,"缩小地图" to -1).forEachIndexed { index,(label,delta) ->
                        if(index>0) HorizontalDivider(Modifier.width(48.dp).padding(horizontal=10.dp),color=Color(0xFFE5EAF2))
                        Surface(onClick={onZoom(delta)},color=Color.Transparent,modifier=Modifier.size(48.dp)) {
                            Box(Modifier.semantics { contentDescription=label },contentAlignment=Alignment.Center) {
                                Text(if(delta>0) "+" else "−",color=Color(0xFF26374F),fontSize=26.sp)
                            }
                        }
                    }
                }
            }
            Surface(onClick=onLocate,color=skinPalette(LocalVisualSkin.current,false).brand,shape=RoundedCornerShape(14.dp),modifier=Modifier.size(48.dp)) {
                Box(Modifier.semantics { contentDescription=if(day!=null) "定位当天" else "定位我" },contentAlignment=Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_location),null,tint=Color.White,modifier=Modifier.size(22.dp))
                }
            }
        }
        if (showOsmCredit) Text("© OpenStreetMap contributors · ODbL 1.0", color = Ink, fontSize = 10.sp,
            modifier = Modifier.align(Alignment.BottomStart).background(Canvas, RoundedCornerShape(4.dp))
                .clickable { uri.openUri("https://www.openstreetmap.org/copyright") }.padding(4.dp))
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
        val span = maxOf(maxX - minX, maxY - minY, .00005)
        val side = minOf(size.width, size.height) * .74f
        fun position(p: TrackPoint) = Offset(
            size.width / 2 + (((p.lon - lon0) * scale - (minX + maxX) / 2) / span * side).toFloat(),
            size.height / 2 + ((lat0 - p.lat - (minY + maxY) / 2) / span * side).toFloat())
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
    var detailKey by rememberSaveable { mutableStateOf<String?>(null) }
    var showStats by rememberSaveable { mutableStateOf(false) }
    val selected = days.firstOrNull { it.key == detailKey }
    if (selected != null) {
        DesignTripDetail(selected, roadColor, { detailKey = null }, { onTrip(selected.focusTrack()) }, onExport)
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal=20.dp,vertical=12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { TrailHeader("足迹","${days.size} 个探索日，记录每一次出发") {
            TextButton(onClick={showStats=!showStats}) { Text(if(showStats) "日历" else "统计") }
            IconButton(onClick=onExport) { Icon(painterResource(R.drawable.ic_export),"导出全部足迹",tint=Pigment) }
        } }
        item {
            val month=java.time.LocalDate.now().toString().take(7)
            val monthDays=remember(days,month) {days.filter {it.key.startsWith(month)}}
            SkinHero(exploredDistance(monthDays.sumOf {it.meters}),"${monthDays.size}",onSkin,
                "本月里程","本月探索天数")
        }
        if (showStats) item { RecapCard(RecapMaker.of(days, java.time.LocalDate.now().toString().take(7)),
            RecapMaker.of(days, java.time.LocalDate.now().toString().take(4))) }
        else item { FootprintCalendar(days, DailyFootprints.key(System.currentTimeMillis())) { track ->
            detailKey = days.firstOrNull { it.tracks.any { original -> original.id == track.id } }?.key
                ?: track.points.firstOrNull()?.let { DailyFootprints.key(it.time) }
        } }
        if(days.isNotEmpty()) {
            item { TrailSectionLabel("最近探索") }
            items(days.take(5),key={"recent:${it.key}"}) { day ->
                Surface(onClick={detailKey=day.key},color=Canvas,shape=RoundedCornerShape(16.dp),border=BorderStroke(1.dp,Line)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.CenterVertically,
                        horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        RouteThumbnail(day.focusTrack(),Modifier.size(56.dp),roadColor)
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                            Text(day.key,color=Ink,fontSize=15.sp,fontWeight=FontWeight.Medium)
                            Text(exploredDistance(day.meters),color=Muted,fontSize=13.sp)
                        }
                        Text("›",color=Faint,fontSize=22.sp)
                    }
                }
            }
        }
        if (days.isEmpty()) item {
            Text("第一段足迹，等你出发。开启自动记录后，已保存的路线会出现在日历里。", color = Muted, fontSize = 14.sp)
        }
    }
}

@Composable
private fun DesignTripDetail(day: DailyFootprint, color: Int, onBack: () -> Unit, onMap: () -> Unit, onExport: () -> Unit) {
    BackHandler(onBack = onBack)
    val summary = remember(day) { TrailSummary.of(day.tracks) }
    val integrity=remember(day) { TrailIntegrity.of(day.tracks) }
    var showIntegrity by remember(day.key) { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=20.dp,vertical=12.dp),
        verticalArrangement=Arrangement.spacedBy(14.dp)) {
        TrailHeader("当天足迹",day.key,onBack)
        RouteThumbnail(day.focusTrack(),Modifier.fillMaxWidth().height(180.dp),color)
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
        Text("地图将回到当天的足迹、迷雾和最后位置。预览显示最长连续路线；定位断档保留分段。",
            color=Muted,fontSize=13.sp,lineHeight=21.sp)
        TextButton(onClick={showIntegrity=!showIntegrity},modifier=Modifier.fillMaxWidth()) {
            Text(if(showIntegrity) "收起记录完整性" else "记录完整性")
        }
        if(showIntegrity) TrailPanel { Text(integrity.report(),color=Muted,fontSize=13.sp,lineHeight=21.sp) }
        OutlinedButton(onClick=onExport,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) { Text("导出全部足迹备份") }
    }

}

@Composable
internal fun DesignOnboarding(onEnable: () -> Unit, onSkip: () -> Unit) {
    Dialog(onDismissRequest = onSkip, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(color = CanvasSoft, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Icon(painterResource(R.drawable.ic_route), null, tint = Pigment, modifier = Modifier.padding(top = 24.dp).size(42.dp))
                Text("足迹涂色  ·  WalkPaint", color = Muted, fontSize = 13.sp)
                Text("走过的路，\n在地图上慢慢涂满。", color = Ink, fontSize = 32.sp, lineHeight = 42.sp, fontWeight = FontWeight.Bold)
                Text("定位后点亮周围 50 米，走动时逐步揭开地图。足迹由你自己保管。", color = Muted, fontSize = 15.sp, lineHeight = 24.sp)
                OnboardingFeature(R.drawable.ic_route, "自动累积足迹", "你同意并授权精确定位后，前台服务会记录走过的路线。")
                OnboardingFeature(R.drawable.ic_lock, "足迹只存本机", "原始足迹保存在本地，可导出 JSON 备份；联网地图服务另行征求同意。")
                OnboardingFeature(R.drawable.ic_notification, "记录时保留通知", "后台定位需要系统授权与常驻通知，可能增加耗电。你随时可以暂停。")
                Button(onClick = onEnable, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Text("同意并开启自动记录", fontWeight = FontWeight.SemiBold)
                }
                TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("先看看，暂不记录") }
                Text("下一步由 Android 系统请求权限。拒绝授权也能查看已有足迹；不会自动上传位置。", color = Muted, fontSize = 12.sp, lineHeight = 20.sp)
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
