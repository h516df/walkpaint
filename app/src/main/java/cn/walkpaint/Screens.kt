package cn.walkpaint

import android.location.Location
import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

internal fun exploredDistance(meters: Double): String = if(meters < 1000)
    "%.0f 米".format(Locale.CHINA,meters) else "%.2f 公里".format(Locale.CHINA,meters/1000)

@Composable
internal fun AppShell(
    selectedTab: Int, onTab: (Int) -> Unit, state: TrackState, fix: Location?,
    automatic: Boolean, running: Boolean, trackingStatus: String, recordingTitle: String,
    recordingHealthy: Boolean, profileNotice: String?, onResolveTracking: () -> Unit, mapNotice: String?, selectedDay: String?, mapRoadState: RoadState,
    dailyRoads: Map<String,RoadState>, roadAreaHint: String, onShowAll: () -> Unit,
    needsBackground: Boolean, onLocate: () -> Unit, onToggleTracking: (Boolean) -> Unit,
    onDemo: () -> Unit, onTrip: (Track) -> Unit, onExport: () -> Unit, onImport: () -> Unit,
    onBackgroundSettings: () -> Unit, onPowerSettings: () -> Unit, onRevoke: () -> Unit,
    onCopyDiagnostics: () -> Unit, internalBackupCount: Int, mapProviderLabel: String,
    onChooseAmap: () -> Unit, onOnlineMap: () -> Unit, roadState: RoadState,
    onRoadPack: () -> Unit, roadColor:Int, onRoadAppearance:()->Unit,
    feedbackContent: @Composable (onBack:()->Unit)->Unit, mapContent: @Composable () -> Unit
) {
    var preparationDismissed by rememberSaveable { mutableStateOf(false) }
    Scaffold(containerColor=PageBackground, bottomBar={
        Surface(color=PageBackground.copy(alpha=0.98f)) {
            Row(Modifier.fillMaxWidth().navigationBarsPadding().height(58.dp)) {
                listOf("地图","足迹","我的").forEachIndexed { index,label ->
                    val chosen=index==selectedTab
                    Column(Modifier.weight(1f).fillMaxHeight().clickable { onTab(index) },
                        horizontalAlignment=Alignment.CenterHorizontally,
                        verticalArrangement=Arrangement.Center) {
                        Icon(painterResource(when(index) { 0->R.drawable.ic_map;1->R.drawable.ic_route;else->R.drawable.ic_person }),
                            contentDescription=label,tint=if(chosen) Accent else Muted,modifier=Modifier.size(22.dp))
                        Spacer(Modifier.height(3.dp))
                        Text(label,color=if(chosen) Accent else Muted,fontSize=11.sp,
                            fontWeight=if(chosen) FontWeight.SemiBold else FontWeight.Normal)
                    }
                }
            }
        }
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when(selectedTab) {
                0 -> MapPage(mapNotice,selectedDay,onShowAll,onLocate,{onTab(2)},
                    automatic && fix!=null && roadAreaHint.isNotBlank() && roadState.error==null && !preparationDismissed,
                    {preparationDismissed=true;onRoadPack()},{preparationDismissed=true},mapContent)
                1 -> TripsPage(state,roadState,dailyRoads,onTrip)
                else -> ProfilePage(automatic,profileNotice,onResolveTracking,
                    onToggleTracking,onRevoke,onBackgroundSettings,onPowerSettings,onCopyDiagnostics,
                    roadState,roadAreaHint,onRoadPack,onExport,onImport,internalBackupCount,
                    mapProviderLabel,onChooseAmap,onOnlineMap,roadColor,onRoadAppearance,feedbackContent)
            }
        }
    }
}

@Composable
private fun MapPage(notice: String?,day: String?,onShowAll: ()->Unit,onLocate: ()->Unit,
    onSettings: ()->Unit,needsMapData:Boolean,onMapData:()->Unit,onDismissPreparation:()->Unit,mapContent: @Composable ()->Unit) {
    val uriHandler=LocalUriHandler.current
    Box(Modifier.fillMaxSize()) {
        mapContent()
        Column(Modifier.align(Alignment.TopCenter).padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            if(notice!=null) Surface(onClick=onSettings,color=Panel.copy(alpha=0.96f),shape=RoundedCornerShape(8.dp)) {
                Text("$notice  ›",color=Ink,fontSize=12.sp,modifier=Modifier.padding(horizontal=12.dp,vertical=10.dp))
            }
            if(day!=null) Surface(onClick=onShowAll,color=PageBackground.copy(alpha=0.96f),shape=RoundedCornerShape(8.dp)) {
                Text("$day · 返回全部探索  ›",color=Accent,fontSize=12.sp,
                    modifier=Modifier.padding(horizontal=12.dp,vertical=10.dp))
            }
            if(day==null && needsMapData) Surface(color=Panel.copy(alpha=0.96f),shape=RoundedCornerShape(8.dp)) {
                Column(Modifier.padding(horizontal=12.dp,vertical=6.dp)) {
                    Text("还没准备好这里的地图",color=Ink,fontSize=13.sp)
                    Row {
                        TextButton(onClick=onMapData) { Text("准备地图") }
                        TextButton(onClick=onDismissPreparation) { Text("暂不") }
                    }
                }
            }
        }
        Surface(onClick=onLocate,shape=CircleShape,color=PageBackground.copy(alpha=0.96f),
            shadowElevation=1.dp,modifier=Modifier.align(Alignment.BottomEnd).padding(end=16.dp,bottom=36.dp).size(48.dp)) {
            Box(contentAlignment=Alignment.Center) {
                Icon(painterResource(R.drawable.ic_location),"定位我",tint=Accent,modifier=Modifier.size(23.dp))
            }
        }
        // Keep the native SDK logo/copyright at the bottom unobscured.
        Text("© OpenStreetMap contributors · ODbL 1.0",color=Ink.copy(alpha=0.8f),fontSize=10.sp,
            modifier=Modifier.align(Alignment.BottomStart).padding(start=8.dp,bottom=28.dp)
                .background(PageBackground.copy(alpha=0.80f),RoundedCornerShape(3.dp))
                .clickable { uriHandler.openUri("https://www.openstreetmap.org/copyright") }
                .padding(horizontal=4.dp,vertical=2.dp))
    }
}

@Composable
private fun Heading(title:String,subtitle:String) {
    Column(Modifier.padding(top=8.dp,bottom=12.dp)) {
        Text(title,color=Ink,fontSize=28.sp,fontWeight=FontWeight.SemiBold)
        Spacer(Modifier.height(7.dp))
        Text(subtitle,color=Muted,fontSize=13.sp,lineHeight=20.sp)
    }
}

@Composable
private fun Section(title:String,content:@Composable ColumnScope.()->Unit) {
    Column(Modifier.fillMaxWidth().background(Panel,RoundedCornerShape(12.dp)).padding(16.dp)) {
        Text(title,color=Ink,fontSize=16.sp,fontWeight=FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun SettingRow(title:String,detail:String="",onClick:()->Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick=onClick).heightIn(min=52.dp).padding(vertical=8.dp),
        verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title,color=Ink,fontSize=14.sp)
            if(detail.isNotEmpty()) { Spacer(Modifier.height(4.dp));Text(detail,color=Muted,fontSize=12.sp,lineHeight=18.sp) }
        }
        Text("›",color=Muted,fontSize=22.sp,modifier=Modifier.padding(start=12.dp))
    }
}

@Composable
private fun TripsPage(state:TrackState,roads:RoadState,daily:Map<String,RoadState>,onTrip:(Track)->Unit) {
    val days=remember(state) { DailyFootprints.days(state) }
    val today=DailyFootprints.key(System.currentTimeMillis())
    val todayDay=days.firstOrNull { it.key==today }
    val todayRoad=daily[today]
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
        item { Heading("足迹","一点点走过，一点点解锁。") }
        item {
            Column(Modifier.fillMaxWidth().padding(vertical=4.dp)) {
                Text("累计已探索道路",color=Muted,fontSize=13.sp)
                Spacer(Modifier.height(8.dp))
                Text(exploredDistance(roads.meters),color=Accent,fontSize=38.sp,fontWeight=FontWeight.Medium)
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(24.dp)) {
                    Text("${roads.intervals.map { it.roadId }.distinct().size} 条道路已点亮",color=Ink,fontSize=13.sp)
                    Text("${days.size} 天探索",color=Muted,fontSize=13.sp)
                }
            }
        }
        item {
            Section("今天的探索") {
                Text(todayRoad?.let { exploredDistance(it.meters) } ?: if(todayDay==null) "0 米" else "正在整理…",
                    color=Accent,fontSize=24.sp,fontWeight=FontWeight.Medium)
                Spacer(Modifier.height(6.dp))
                Text(if(todayDay==null) "还没有新的足迹，出门走走吧。" else "今天走过的真实道路，重复经过只计一次。",
                    color=Muted,fontSize=13.sp,lineHeight=20.sp)
                if(todayDay!=null) SettingRow("查看今天走过的道路",onClick={onTrip(todayDay.focusTrack())})
            }
        }
        item {
            if(roads.roads.isNotEmpty()) {
                val fraction=(roads.meters/roads.totalMeters.coerceAtLeast(1.0)).coerceIn(0.0,1.0).toFloat()
                Column {
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                        Text("当前道路区域",color=Ink,fontSize=13.sp)
                        Text("%.2f%%".format(Locale.CHINA,fraction*100),color=Accent,fontSize=13.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(progress={fraction},modifier=Modifier.fillMaxWidth().height(4.dp),
                        color=ExploredRoadColor,trackColor=AccentSoft,drawStopIndicator={})
                    Spacer(Modifier.height(8.dp))
                    Text("进度仅针对已下载道路，新的区域会扩大探索范围。",color=Muted,fontSize=12.sp,lineHeight=18.sp)
                }
            } else Text("在「我的」准备附近道路，走过的路才会逐渐上色。",color=Muted,fontSize=13.sp,lineHeight=20.sp)
        }
        item { Text("每日足迹",color=Ink,fontSize=17.sp,fontWeight=FontWeight.SemiBold) }
        if(days.isEmpty()) item { Text("你的第一条道路，等你来探索。",color=Muted,fontSize=14.sp) }
        items(days,key={it.key}) { day ->
            Row(Modifier.fillMaxWidth().clickable { onTrip(day.focusTrack()) }.heightIn(min=64.dp).padding(vertical=8.dp),
                verticalAlignment=Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_route),null,tint=Lilac,modifier=Modifier.size(22.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(if(day.key==today) "今天" else day.key,color=Ink,fontSize=15.sp,fontWeight=FontWeight.Medium)
                    Spacer(Modifier.height(6.dp))
                    Text(daily[day.key]?.let { "已探索 ${exploredDistance(it.meters)}" } ?: "点击查看当天道路",
                        color=Muted,fontSize=12.sp)
                }
                Text("›",color=Muted,fontSize=22.sp)
            }
            HorizontalDivider(color=AccentSoft)
        }
    }
}

private val ExploredRoadColor=androidx.compose.ui.graphics.Color(ExploredRoadArgb)

@Composable
private fun ProfilePage(automatic:Boolean,notice:String?,onResolve:()->Unit,
    onToggle:(Boolean)->Unit,onRevoke:()->Unit,onPermissions:()->Unit,onPower:()->Unit,onDiagnostics:()->Unit,
    roads:RoadState,areaHint:String,onRoadPack:()->Unit,onExport:()->Unit,onImport:()->Unit,backupCount:Int,
    provider:String,onAmap:()->Unit,onOnline:()->Unit,roadColor:Int,onAppearance:()->Unit,
    feedbackContent: @Composable (onBack:()->Unit)->Unit) {
    var page by rememberSaveable { mutableStateOf("我的") }
    var revokeConfirm by rememberSaveable { mutableStateOf(false) }
    if(page=="问题反馈") { feedbackContent { page="更多设置" };return }
    BackHandler(enabled=page!="我的") { page=if(page=="更多设置") "我的" else "更多设置" }
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        if(page!="我的") item {
            TextButton(onClick={ page=if(page=="更多设置") "我的" else "更多设置" }) { Text("返回") }
        }
        item { Heading(page,if(page=="我的") "放心探索，足迹由自己保管。" else "按需设置，历史足迹始终保留。") }
        when(page) {
            "我的" -> {
                item { Section("自动记录") {
                    Row(Modifier.fillMaxWidth().heightIn(min=56.dp),verticalAlignment=Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(if(automatic) "已开启" else "未开启",color=Ink,fontSize=16.sp,fontWeight=FontWeight.Medium)
                            Spacer(Modifier.height(6.dp))
                            Text("开启后，出门会自动记录走过的道路",color=Muted,fontSize=13.sp,lineHeight=20.sp)
                        }
                        Switch(checked=automatic,onCheckedChange=onToggle,
                            modifier=Modifier.semantics { contentDescription="自动记录" })
                    }
                    if(automatic && notice!=null) SettingRow(notice,"去解决",onResolve)
                } }
                item { Section("偏好") {
                    SettingRow("涂色颜色",RoadPaintColor.name(roadColor),onAppearance)
                    SettingRow("更多设置","备份、隐私与地图数据") { page="更多设置" }
                } }
                item { Text("足迹涂色  ${BuildConfig.VERSION_NAME}",color=Muted,fontSize=12.sp,modifier=Modifier.padding(vertical=8.dp)) }
            }
            "更多设置" -> item { Section("设置") {
                SettingRow("备份与恢复","保存或找回你的足迹") { page="备份与恢复" }
                SettingRow("隐私与权限","管理授权与后台运行") { page="隐私与权限" }
                SettingRow("地图数据","准备这里的地图",onRoadPack)
                SettingRow("问题反馈","预览并分享本机故障线索") { page="问题反馈" }
                SettingRow("高级诊断","记录异常时查看原因",onDiagnostics)
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
                    SettingRow("联网底图 / 离线道路","管理联网地图授权",onOnline)
                    Text("原始足迹保存在本机。联网地图与地图数据下载需要单独同意；开自动记录不会替你同意联网。",color=Muted,fontSize=13.sp,lineHeight=20.sp)
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
