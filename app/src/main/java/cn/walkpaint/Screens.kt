package cn.walkpaint

import android.location.Location
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

internal fun exploredDistance(meters: Double): String = if(meters < 1000)
    "%.0f 米".format(Locale.CHINA,meters) else "%.2f 公里".format(Locale.CHINA,meters/1000)

@Composable
internal fun AppShell(
    selectedTab: Int, onTab: (Int) -> Unit, state: TrackState, fix: Location?, placeName: String? = null,
    automatic: Boolean, running: Boolean, trackingStatus: String, recordingTitle: String,
    recordingHealthy: Boolean, profileNotice: String?, onResolveTracking: () -> Unit, mapNotice: String?, selectedDay: String?, mapRoadState: RoadState,
    dailyRoads: Map<String,RoadState>, roadAreaHint: String, onShowAll: () -> Unit,
    needsBackground: Boolean, onLocate: () -> Unit, onZoom: (Int) -> Unit, onToggleTracking: (Boolean) -> Unit,
    onDemo: () -> Unit, onTrip: (Track) -> Unit, onExport: () -> Unit, onImport: () -> Unit,
    onBackgroundSettings: () -> Unit, onPowerSettings: () -> Unit, onRevoke: () -> Unit,
    onCopyDiagnostics: () -> Unit, internalBackupCount: Int, mapProviderLabel: String,
    onChooseAmap: () -> Unit, onOnlineMap: () -> Unit, roadState: RoadState,
    onRoadPack: () -> Unit, roadColor:Int, onRoadAppearance:()->Unit,
    appearanceMode:Int, onAppearanceMode:(Int)->Unit, onShowIntro:()->Unit,
    feedbackContent: @Composable (onBack:()->Unit)->Unit,
    updatesContent: @Composable (onBack:()->Unit)->Unit, mapContent: @Composable () -> Unit
) {
    Scaffold(containerColor=PageBackground, topBar={
        Surface(color=Canvas, shadowElevation=2.dp) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal=16.dp,vertical=6.dp),
                verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                listOf("探索","足迹").forEachIndexed { index,label ->
                    TextButton(onClick={onTab(index)}, modifier=Modifier.weight(1f),
                        colors=ButtonDefaults.textButtonColors(contentColor=if(selectedTab==index) Pigment else Muted)) {
                        Text(label, fontSize=18.sp, fontWeight=if(selectedTab==index) FontWeight.Bold else FontWeight.Normal)
                    }
                }
                IconButton(onClick={onTab(2)}) { Icon(painterResource(R.drawable.ic_person), "我的", tint=if(selectedTab==2) Pigment else Muted) }
            }
        }
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when(selectedTab) {
                0 -> DesignMapOverlay(state,selectedDay,automatic,recordingTitle,recordingHealthy,mapNotice,
                    mapProviderLabel,{onTab(2)},onToggleTracking,onLocate,onShowAll,
                    mapProviderLabel.contains("OpenFreeMap"),onZoom,mapContent)
                1 -> DesignTripsPage(state,roadColor,onTrip,onExport)
                else -> ProfilePage(state,appearanceMode,onAppearanceMode,onShowIntro,automatic,profileNotice,onResolveTracking,
                    onToggleTracking,onRevoke,onBackgroundSettings,onPowerSettings,onCopyDiagnostics,
                    roadState,roadAreaHint,onRoadPack,onExport,onImport,internalBackupCount,
                    mapProviderLabel,onChooseAmap,onOnlineMap,roadColor,onRoadAppearance,feedbackContent,updatesContent)
            }
        }
    }
}

@Composable
private fun MapPage(notice: String?,day: String?,onShowAll: ()->Unit,onLocate: ()->Unit,
    onSettings: ()->Unit,place:String,share:PlaceShare,showOsmCredit:Boolean,mapContent: @Composable ()->Unit) {
    val uriHandler=LocalUriHandler.current
    Box(Modifier.fillMaxSize()) {
        mapContent()
        Column(Modifier.align(Alignment.TopCenter).padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            // One line only: a problem when there is one, otherwise the progress. Stacking both was
            // half of what made the map look like a mess. An opaque pill, because a ten percent tint
            // over a basemap full of labels is not a background at all.
            if(notice!=null) Surface(onClick=onSettings,color=Canvas,shape=ChipShape,
                border=BorderStroke(1.dp,Line),shadowElevation=1.dp) {
                Text("$notice  ›",color=Ink,fontSize=CaptionSize,modifier=Modifier.padding(horizontal=12.dp,vertical=10.dp))
            } else Surface(color=Canvas,shape=ChipShape,border=BorderStroke(1.dp,Line),shadowElevation=1.dp) {
                Text("$place · 解锁 ${share.percent}%",color=Ink,fontSize=CaptionSize,
                    fontWeight=FontWeight.Medium,modifier=Modifier.padding(horizontal=12.dp,vertical=10.dp))
            }
            if(day!=null) Surface(onClick=onShowAll,color=Canvas,shape=ChipShape,border=BorderStroke(1.dp,Line)) {
                Text("$day · 返回全部探索  ›",color=Accent,fontSize=CaptionSize,
                    modifier=Modifier.padding(horizontal=12.dp,vertical=10.dp))
            }
        }
        Surface(onClick=onLocate,shape=CircleShape,color=PageBackground.copy(alpha=0.96f),
            shadowElevation=1.dp,modifier=Modifier.align(Alignment.BottomEnd).padding(end=16.dp,bottom=36.dp).size(48.dp)) {
            Box(contentAlignment=Alignment.Center) {
                Icon(painterResource(R.drawable.ic_location),"定位我",tint=Accent,modifier=Modifier.size(23.dp))
            }
        }
        // Only when OSM tiles are actually on screen: crediting OSM over an AMap basemap is
        // noise, and the licence only asks for the credit where its data is shown.
        if(showOsmCredit) Text("© OpenStreetMap contributors · ODbL 1.0",color=Ink.copy(alpha=0.8f),fontSize=10.sp,
            modifier=Modifier.align(Alignment.BottomStart).padding(start=8.dp,bottom=28.dp)
                .background(PageBackground.copy(alpha=0.80f),RoundedCornerShape(3.dp))
                .clickable { uriHandler.openUri("https://www.openstreetmap.org/copyright") }
                .padding(horizontal=4.dp,vertical=2.dp))
    }
}

@Composable
private fun Heading(title:String,subtitle:String) {
    Column(Modifier.padding(top=4.dp,bottom=18.dp)) {
        Text(title,color=Ink,fontSize=TitleSize,fontWeight=FontWeight.Bold,letterSpacing=(-0.6).sp)
        Spacer(Modifier.height(8.dp))
        Text(subtitle,color=Muted,fontSize=CaptionSize,lineHeight=20.sp)
    }
}

/**
 * A real card: white on the paper background, a hairline border and a soft edge. The previous
 * version tinted the panel three percent away from the page, which read as nothing at all.
 */
@Composable
private fun Section(title:String,content:@Composable ColumnScope.()->Unit) {
    Surface(color=Canvas,shape=CardShape,border=BorderStroke(1.dp,Line),shadowElevation=2.dp,
        modifier=Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text(title,color=Ink,fontSize=SectionSize,fontWeight=FontWeight.SemiBold)
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
private fun SettingRow(title:String,detail:String="",onClick:()->Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick=onClick).heightIn(min=56.dp).padding(vertical=10.dp),
        verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title,color=Ink,fontSize=BodySize,fontWeight=FontWeight.Medium)
            if(detail.isNotEmpty()) { Spacer(Modifier.height(4.dp));Text(detail,color=Muted,fontSize=CaptionSize,lineHeight=18.sp) }
        }
        Text("›",color=Faint,fontSize=20.sp,modifier=Modifier.padding(start=12.dp))
    }
}

@Composable
private fun TripsPage(state:TrackState,onTrip:(Track)->Unit,place:String,share:PlaceShare,streak:Int) {
    val days=remember(state) { DailyFootprints.days(state) }
    val today=DailyFootprints.key(System.currentTimeMillis())
    val todayDay=days.firstOrNull { it.key==today }
    val byMonth=remember(days) { days.groupBy { it.key.take(7) } }
   val exploration=remember(state) {
        Explorer.of(state,java.time.LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault())
            .toInstant().toEpochMilli())
    }
    LazyColumn(Modifier.fillMaxSize(),
        contentPadding=PaddingValues(start=20.dp,end=20.dp,top=20.dp,bottom=32.dp),
        verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item { Heading("足迹","走过哪里，就在地图上留下颜色。") }
        item {
            Column(Modifier.fillMaxWidth()) {
                Text("累计记录里程",color=Muted,fontSize=CaptionSize)
                Spacer(Modifier.height(10.dp))
                Text(exploredDistance(state.totalMeters),color=Pigment,fontSize=DisplaySize,
                    fontWeight=FontWeight.Bold,style=TabularFigures)
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    // Short labels: the three chips used to wrap onto a second line on a phone.
                    StatChip("今天 ${exploredDistance(todayDay?.meters ?: 0.0)}")
                   StatChip("${days.size} 天")
                }
            }
        }
        item { PlaceCard(place,share,streak) }
        item { RecapCard(RecapMaker.of(days,today.take(7)),RecapMaker.of(days,today.take(4))) }
        item { FootprintCalendar(days,today,onTrip) }
       if(days.isEmpty()) {
            item { Text("开启自动记录后，走过的地方会在地图上留下颜色。",color=Muted,fontSize=BodySize) }
        } else {
            byMonth.forEach { (month,tracks) ->
                item(key="m:$month") {
                    Text(monthLabel(month),color=Ink,fontSize=SectionSize,fontWeight=FontWeight.SemiBold,
                        modifier=Modifier.padding(top=6.dp))
                }
                items(tracks,key={it.key}) { day -> DayRow(day,today,onTrip) }
            }
        }
    }
}

private fun monthLabel(month:String):String = "${month.take(4)} 年 ${month.substring(5).toInt()} 月"

/**
 * The progress reading, anchored to a named place.
 *
 * "已点亮 78 块" told the person nothing, because 78 of what, and where. A place name and a share of
 * a stated radius says something: this is where you were, and this is how much of the two
 * kilometres around it you have covered. The denominator is printed underneath, because a
 * percentage without its denominator is not a fact, which is why the earlier attempt at a
 * percentage was dropped rather than shipped.
 */
/**
 * The month and the year in the person's own terms, not a chart.
 *
 * Numbers people actually say out loud: how far, how many days, the longest run, and the one day
 * they went furthest. Everything comes from the stored days, so it needs no server and cannot drift
 * from the calendar below it.
 */
@Composable
internal fun RecapCard(month:Recap?,year:Recap?) {
    if(month==null && year==null) return
    Section("回顾") {
        month?.let { RecapRow("本月", it) }
        if(year!=null && year.period!=month?.period) {
            Spacer(Modifier.height(14.dp))
            RecapRow("今年", year)
        }
    }
}

@Composable
private fun RecapRow(label:String,recap:Recap) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.Bottom) {
            Text(label,color=Muted,fontSize=CaptionSize,modifier=Modifier.padding(bottom=3.dp))
            Spacer(Modifier.weight(1f))
            Text(exploredDistance(recap.meters),color=Ink,fontSize=TitleSize,
                fontWeight=FontWeight.Bold,style=TabularFigures)
        }
        Spacer(Modifier.height(6.dp))
        Text("${recap.days} 天有足迹 · 连续最多 ${recap.longestStreak} 天 · 最远的一天 " +
            "${recap.bestDay.replace('-','/')} 走了 ${exploredDistance(recap.bestDayMeters)}",
            color=Muted,fontSize=MicroSize,lineHeight=17.sp)
    }
}

@Composable
private fun PlaceCard(place:String,share:PlaceShare,streak:Int) {
    Section("探索") {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(place,color=Ink,fontSize=SectionSize,fontWeight=FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text("附近 ${PlaceProgress.RADIUS_KM} 公里",color=Muted,fontSize=CaptionSize)
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment=Alignment.Bottom) {
                    Text("解锁 ",color=Muted,fontSize=CaptionSize,modifier=Modifier.padding(bottom=6.dp))
                    Text("${share.percent}",color=Pigment,fontSize=36.sp,
                        fontWeight=FontWeight.Bold,style=TabularFigures)
                    Text(" %",color=Pigment,fontSize=17.sp,fontWeight=FontWeight.Bold,
                        modifier=Modifier.padding(bottom=4.dp))
                }
            }
            Column(horizontalAlignment=Alignment.End) {
                // A run of one is not a run; today's chip already covers that case.
                if(streak>=2) {
                    Spacer(Modifier.height(6.dp))
                    Surface(color=CanvasSoft,shape=ChipShape) {
                        Text("连续 $streak 天",color=Ink,fontSize=CaptionSize,fontWeight=FontWeight.Medium,
                            modifier=Modifier.padding(horizontal=12.dp,vertical=8.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().height(8.dp).background(Line,CircleShape)) {
            if(share.percent>0) Box(Modifier.fillMaxWidth(share.percent/100f)
                .height(8.dp).background(Pigment,CircleShape))
        }
        Spacer(Modifier.height(10.dp))
        Text("以你所在位置为圆心 ${PlaceProgress.RADIUS_KM} 公里的范围里，你走过了 ${share.litCells} / ${share.totalCells} 块（一块约 550 米见方）。",
            color=Muted,fontSize=MicroSize,lineHeight=17.sp)
    }
}

@Composable
private fun StatChip(text:String) {
    Surface(color=CanvasSoft,shape=ChipShape) {
        Text(text,color=Ink,fontSize=CaptionSize,modifier=Modifier.padding(horizontal=12.dp,vertical=7.dp))
    }
}

/** One line per day. The bar that used to sit here compared every day with the single best day,
 * so most rows stayed near empty forever, and it repeated a number printed right beside it. */
@Composable
private fun DayRow(day:DailyFootprint,today:String,onTrip:(Track)->Unit) {
    Row(Modifier.fillMaxWidth().clickable { onTrip(day.focusTrack()) }.padding(vertical=14.dp),
        verticalAlignment=Alignment.CenterVertically) {
        Text(if(day.key==today) "今天" else day.key.replace('-','/'),color=Ink,
            fontSize=BodySize,fontWeight=FontWeight.Medium,modifier=Modifier.weight(1f))
        Text(exploredDistance(day.meters),color=Ink,fontSize=BodySize,style=TabularFigures)
        Text("›",color=Faint,fontSize=18.sp,modifier=Modifier.padding(start=8.dp))
    }
}

/**
 * A rolling year of footprints. Months and years tile continuously, the newest week is scrolled
 * into view, and tapping a day opens it, so "where did I walk last spring" is one glance away
 * instead of a list you have to read date by date.
 */
/** Day, month and year, drilled one into the other. */
private enum class CalendarMode { DAY, MONTH, YEAR }

@Composable
private fun heatFill(level:Int) = when {
    level<0 -> androidx.compose.ui.graphics.Color.Transparent
    level==0 -> Line
    else -> Pigment.copy(alpha=0.22f+0.195f*(level-1))
}

/** White text only where the tint is dark enough to need it. */
@Composable
private fun heatInk(level:Int) = if(level>=3) androidx.compose.ui.graphics.Color.White else Ink

/**
 * The three levels of the same picture.
 *
 * A year grid answers "did I keep it up" but cannot show which day of this week was skipped, and a
 * single day cannot show a habit. So the year is the overview, the month is the page you actually
 * read, and the day answers the one question neither can: when during the day did I walk. All three
 * share [YearHeatmap] banding, so a shade means the same thing wherever you are.
 */
@Composable
internal fun FootprintCalendar(days:List<DailyFootprint>,todayKey:String,onTrip:(Track)->Unit) {
    val today=remember { java.time.LocalDate.now() }
    val byKey=remember(days) { days.associateBy { it.key } }
    val meters=remember(days) { byKey.mapValues { it.value.meters } }
    var modeName by rememberSaveable { mutableStateOf(CalendarMode.MONTH.name) }
    var monthKey by rememberSaveable { mutableStateOf(todayKey.take(7)) }
    var dayKey by rememberSaveable { mutableStateOf(todayKey) }
    val mode=runCatching { CalendarMode.valueOf(modeName) }.getOrDefault(CalendarMode.MONTH)
    Section("足迹日历") {
        Row(Modifier.fillMaxWidth().background(CanvasSoft,ChipShape).padding(3.dp)) {
            CalendarMode.entries.forEach { entry ->
                val selected=entry==mode
                Surface(onClick={ modeName=entry.name },
                    color=if(selected) Canvas else androidx.compose.ui.graphics.Color.Transparent,
                    shape=ChipShape, shadowElevation=if(selected) 1.dp else 0.dp,
                    modifier=Modifier.weight(1f)) {
                    Text(calendarName(entry),color=if(selected) Ink else Muted,fontSize=CaptionSize,
                        fontWeight=if(selected) FontWeight.SemiBold else FontWeight.Normal,
                        textAlign=TextAlign.Center,modifier=Modifier.fillMaxWidth().padding(vertical=8.dp))
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        when(mode) {
            CalendarMode.YEAR -> YearStrip(today,meters) { date ->
                monthKey=date.toString().take(7); modeName=CalendarMode.MONTH.name }
            CalendarMode.MONTH -> MonthPage(monthKey,today,meters,byKey,dayKey,
                onShiftMonth={ monthKey=it }, onPickDay={ dayKey=it; modeName=CalendarMode.DAY.name
                    byKey[it]?.let { selected -> onTrip(selected.focusTrack()) } })
            CalendarMode.DAY -> DayPage(dayKey,byKey,today,todayKey,onTrip)
        }
    }
}

private fun calendarName(mode:CalendarMode)=when(mode) {
    CalendarMode.DAY -> "日"; CalendarMode.MONTH -> "月"; CalendarMode.YEAR -> "年"
}

/** The rolling year, still the overview: tapping a cell opens that month. */
@Composable
private fun YearStrip(today:java.time.LocalDate,meters:Map<String,Double>,onPickMonth:(java.time.LocalDate)->Unit) {
    val grid=remember(meters) { YearHeatmap.build(today,meters) }
    val scroll=rememberScrollState()
    LaunchedEffect(grid) { scroll.scrollTo(scroll.maxValue) }
    Column {
        Row(Modifier.horizontalScroll(scroll)) {
            grid.forEach { week ->
                Column(Modifier.padding(end=3.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                    week.cells.forEach { cell ->
                        Box(Modifier.size(13.dp)
                            .background(heatFill(cell.level),RoundedCornerShape(3.dp))
                            .then(if(cell.level>=0) Modifier.clickable { onPickMonth(cell.date) } else Modifier))
                        Spacer(Modifier.height(3.dp))
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(week.monthLabel ?: "",color=Muted,fontSize=9.sp,maxLines=1)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text("一格是一天，点一格看那个月",color=Muted,fontSize=MicroSize)
    }
}

/** A real calendar page: the day number plus its shade, so a skipped day is obvious. */
@Composable
private fun MonthPage(monthKey:String,today:java.time.LocalDate,meters:Map<String,Double>,
    byKey:Map<String,DailyFootprint>,selectedKey:String,
    onShiftMonth:(String)->Unit,onPickDay:(String)->Unit) {
    val ym=runCatching { java.time.YearMonth.parse(monthKey) }
        .getOrDefault(java.time.YearMonth.from(today))
    val grid=remember(ym,meters) { YearHeatmap.monthGrid(ym.year,ym.monthValue,today,meters) }
    Column {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            TextButton(onClick={ onShiftMonth(ym.minusMonths(1).toString()) }) { Text("‹",fontSize=20.sp) }
            Text(grid.label,color=Ink,fontSize=SectionSize,fontWeight=FontWeight.SemiBold,
                textAlign=TextAlign.Center,modifier=Modifier.weight(1f))
            TextButton(onClick={ onShiftMonth(ym.plusMonths(1).toString()) }) { Text("›",fontSize=20.sp) }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            listOf("一","二","三","四","五","六","日").forEach { label ->
                Text(label,color=Muted,fontSize=MicroSize,textAlign=TextAlign.Center,modifier=Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(6.dp))
        grid.weeks.forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { cell ->
                    Box(Modifier.weight(1f).padding(2.dp)) {
                        if(cell!=null) {
                            val key=cell.date.toString()
                            val hasData=byKey.containsKey(key)
                            Box(Modifier.fillMaxWidth().aspectRatio(1f)
                                .background(heatFill(cell.level),RoundedCornerShape(9.dp))
                                .then(if(hasData) Modifier.clickable { onPickDay(key) } else Modifier),
                                contentAlignment=Alignment.Center) {
                                Text(cell.date.dayOfMonth.toString(),
                                    color=if(hasData) heatInk(cell.level) else Faint,
                                    fontSize=CaptionSize,
                                    fontWeight=if(key==selectedKey) FontWeight.Bold else FontWeight.Normal)
                            }
                        } else Box(Modifier.fillMaxWidth().aspectRatio(1f))
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text("颜色越深，那天走得越多",color=Muted,fontSize=MicroSize)
    }
}

/** One day: how far, and when during the day. */
@Composable
private fun DayPage(dayKey:String,byKey:Map<String,DailyFootprint>,today:java.time.LocalDate,
    todayKey:String,onTrip:(Track)->Unit) {
    val day=byKey[dayKey]
    val hours=remember(dayKey) { YearHeatmap.hourStrip(day?.tracks.orEmpty()) }
    val peak=hours.maxOrNull() ?: 0.0
    Column {
        Text(if(dayKey==todayKey) "今天" else dayKey.replace('-','/'),color=Muted,fontSize=CaptionSize)
        Spacer(Modifier.height(8.dp))
        Text(exploredDistance(day?.meters ?: 0.0),color=Pigment,fontSize=27.sp,
            fontWeight=FontWeight.Bold,style=TabularFigures)
        Spacer(Modifier.height(4.dp))
        Text(if(day==null) "这一天没有记录" else "记下 ${day.points} 个点 · ${hourRange(hours)}",
            color=Muted,fontSize=CaptionSize)
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(3.dp),
            verticalAlignment=Alignment.Bottom) {
            hours.forEachIndexed { hour, meters ->
                val fraction=if(peak<=0.0) 0f else (meters/peak).coerceIn(0.0,1.0).toFloat()
                Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally) {
                    Box(Modifier.fillMaxWidth().height(40.dp),contentAlignment=Alignment.BottomCenter) {
                        Box(Modifier.fillMaxWidth().height((maxOf(fraction,0.04f)*40f).dp)
                            .background(if(fraction>0f) Pigment else Line,RoundedCornerShape(2.dp)))
                    }
                    if(hour%6==0) Text(hour.toString(),color=Muted,fontSize=9.sp) else Text("",fontSize=9.sp)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        if(day!=null) SettingRow("查看当天足迹",onClick={ onTrip(day.focusTrack()) })
    }
}

/** "9 点到 11 点" style summary of when the day's walking happened. */
private fun hourRange(hours:List<Double>):String {
    val active=hours.withIndex().filter { it.value>0.0 }.map { it.index }
    if(active.isEmpty()) return "无活动时段"
    return "${active.first()} 点到 ${active.last()+1} 点"
}

private val ExploredRoadColor=androidx.compose.ui.graphics.Color(ExploredRoadArgb)

@Composable
private fun ProfilePage(state:TrackState,appearanceMode:Int,onAppearanceMode:(Int)->Unit,onShowIntro:()->Unit,automatic:Boolean,notice:String?,onResolve:()->Unit,
    onToggle:(Boolean)->Unit,onRevoke:()->Unit,onPermissions:()->Unit,onPower:()->Unit,onDiagnostics:()->Unit,
    roads:RoadState,areaHint:String,onRoadPack:()->Unit,onExport:()->Unit,onImport:()->Unit,backupCount:Int,
    provider:String,onAmap:()->Unit,onOnline:()->Unit,roadColor:Int,onAppearance:()->Unit,
    feedbackContent: @Composable (onBack:()->Unit)->Unit,
    updatesContent: @Composable (onBack:()->Unit)->Unit) {
    var page by rememberSaveable { mutableStateOf("我的") }
    var revokeConfirm by rememberSaveable { mutableStateOf(false) }
    if(page=="问题反馈") { feedbackContent { page="更多设置" };return }
    if(page=="检查更新") { updatesContent { page="更多设置" };return }
    BackHandler(enabled=page!="我的") { page=if(page=="更多设置") "我的" else "更多设置" }
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        if(page!="我的") item {
            TextButton(onClick={ page=if(page=="更多设置") "我的" else "更多设置" }) { Text("返回") }
        }
        item { Heading(page,if(page=="我的") "放心探索，足迹由自己保管。" else "按需设置，历史足迹始终保留。") }
        when(page) {
            "我的" -> {
                item { DesignSummary(state) }
                item { Section("记录") {
                    Row(Modifier.fillMaxWidth().heightIn(min=56.dp),verticalAlignment=Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("自动记录",color=Ink,fontSize=BodySize,fontWeight=FontWeight.Medium)
                            Text("授权后自动累积足迹",color=Muted,fontSize=CaptionSize,modifier=Modifier.padding(top=4.dp))
                        }
                        Switch(checked=automatic,onCheckedChange=onToggle,
                            modifier=Modifier.semantics { contentDescription="自动记录" })
                    }
                    if(automatic && notice!=null) SettingRow(notice,"查看权限与记录状态",onResolve)
                    SettingRow("定位与后台权限","由 Android 系统管理",onPermissions)
                } }
                item { Section("地图与涂色") {
                    SettingRow("地图服务与隐私",provider,onAmap)
                    SettingRow("涂色颜色",RoadPaintColor.name(roadColor),onAppearance)
                } }
                item { Section("数据与备份") {
                    SettingRow("导出足迹 JSON","保存到你选择的位置",onExport)
                    SettingRow("导入 JSON 备份","合并去重，保留历史足迹",onImport)
                    SettingRow("诊断与反馈","预览报告或导出记录现场") { page="问题反馈" }
                } }
                item { Section("外观与关于") {
                    Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        listOf("跟随系统","浅色","深色").forEachIndexed { mode,label ->
                            FilterChip(selected=appearanceMode==mode,onClick={onAppearanceMode(mode)},label={Text(label)})
                        }
                    }
                    SettingRow("检查更新","当前版本 ${BuildConfig.VERSION_NAME}") { page="检查更新" }
                    SettingRow("首次使用引导","查看自动记录与隐私说明",onShowIntro)
                    SettingRow("更多设置","权限、电池、完整诊断与关于") { page="更多设置" }
                } }
                item { Text("足迹涂色  ${BuildConfig.VERSION_NAME} · 数据保存在本机",color=Muted,fontSize=12.sp) }
            }
            "更多设置" -> item { Section("设置") {
                SettingRow("备份与恢复","保存或找回你的足迹") { page="备份与恢复" }
                SettingRow("隐私与权限","管理授权与后台运行") { page="隐私与权限" }
                SettingRow("检查更新","当前版本 ${BuildConfig.VERSION_NAME}") { page="检查更新" }
                // The full page, not only the read-only dialog: this is where a problem is described,
                // where the same diagnosis is previewed, and where the raw field material — the fixes
                // themselves, the dropped ones and why — is exported. The dialog stays for anyone who
                // only wants the text to copy.
                SettingRow("诊断与反馈","预览报告，或导出记录现场") { page="问题反馈" }
                SettingRow("完整诊断文本","原始计数与道路复算，可复制",onDiagnostics)
                SettingRow("关于","版本与地图来源") { page="关于" }
            } }
            "备份与恢复" -> item { Section("保护足迹") {
                SettingRow("导出备份","保存到你选择的位置",onExport)
                SettingRow("恢复备份","合并已有数据，不清空历史足迹",onImport)
                Text("本机自动快照 $backupCount 份，建议定期导出备份。",color=Muted,fontSize=13.sp,lineHeight=20.sp)
            } }
            "隐私与权限" -> {
                item { Section("记录授权") {
                    Text("关闭自动记录只会停止后续记录，不删除历史，也不会撤销系统权限。",color=Muted,fontSize=13.sp,lineHeight=20.sp)
                    SettingRow("定位权限","在系统设置中管理位置授权",onPermissions)
                    SettingRow("提高后台记录稳定性","按需查看系统电池设置",onPower)
                    SettingRow("撤回自动记录授权","停止记录与自动恢复，保留历史") { revokeConfirm=true }
                } }
                item { Section("地图与隐私") {
                    SettingRow("地图服务与隐私",provider,onAmap)
                        Text("高德地图联网显示底图，足迹直接画在地图上，无需准备地图。原始足迹保存在本机，首次使用地图服务需同意其隐私说明。",color=Muted,fontSize=13.sp,lineHeight=20.sp)
                } }
            }
            "关于" -> item { Section("足迹涂色") {
                Text("版本 ${BuildConfig.VERSION_NAME}",color=Ink,fontSize=15.sp)
                Text("当前地图：$provider",color=Muted,fontSize=13.sp)
                Text("© OpenStreetMap contributors · ODbL 1.0\n高德等联网地图按各自授权和署名使用。",color=Muted,fontSize=13.sp,lineHeight=20.sp)
            } }
        }
    }
    if(revokeConfirm) AlertDialog(onDismissRequest={revokeConfirm=false},title={Text("撤回自动记录授权？")},
        text={Text("将停止记录并关闭自动恢复，历史足迹全部保留。系统位置权限仍由你在系统设置中管理。")},
        confirmButton={TextButton(onClick={revokeConfirm=false;onRevoke()}) {Text("撤回授权")}},
        dismissButton={TextButton(onClick={revokeConfirm=false}) {Text("取消")}})
}




