package cn.walkpaint

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.net.Uri
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Ink = Color(0xFF193D40)
private val Muted = Color(0xFF758C89)
private val Accent = Color(0xFF187F78)
private val AccentSoft = Color(0xFFDFF2ED)
private val Lilac = Color(0xFF8271C7)
private val PageBackground = Color(0xFFF4F8F6)

@Composable
internal fun AppShell(
    selectedTab: Int,
    onTab: (Int) -> Unit,
    state: TrackState,
    fix: Location?,
    automatic: Boolean,
    running: Boolean,
    trackingStatus: String,
    needsBackground: Boolean,
    onLocate: () -> Unit,
    onToggleTracking: (Boolean) -> Unit,
    onDemo: () -> Unit,
    onTrip: (Track) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onBackgroundSettings: () -> Unit,
    onCopyDiagnostics: () -> Unit,
    internalBackupCount: Int,
    mapProviderLabel: String,
    onChooseAmap: () -> Unit,
    mapContent: @Composable () -> Unit
) {
    Scaffold(
        containerColor = PageBackground,
        bottomBar = {
            NavigationBar(containerColor = Color.White, tonalElevation = 10.dp) {
                listOf("地图", "足迹", "我的").forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { onTab(index) },
                        icon = {
                            Icon(painterResource(when (index) { 0 -> R.drawable.ic_map; 1 -> R.drawable.ic_route; else -> R.drawable.ic_person }),
                                contentDescription = tab, tint = if (selectedTab == index) Accent else Muted)
                        },
                        label = { Text(tab, fontWeight = if (selectedTab == index) FontWeight.SemiBold else FontWeight.Normal) }
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (selectedTab) {
                0 -> MapPage(
                    state, fix, automatic, running, trackingStatus,
                    onLocate, onToggleTracking, onDemo, mapProviderLabel, mapContent
                )
                1 -> TripsPage(state, onTrip)
                else -> ProfilePage(
                    state, automatic, running, trackingStatus, needsBackground,
                    internalBackupCount, onToggleTracking, onExport, onImport, onBackgroundSettings,
                    onCopyDiagnostics, mapProviderLabel, onChooseAmap
                )
            }
        }
    }
}

@Composable
private fun MapPage(
    state: TrackState,
    fix: Location?,
    automatic: Boolean,
    running: Boolean,
    status: String,
    onLocate: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDemo: () -> Unit,
    mapProviderLabel: String,
    mapContent: @Composable () -> Unit
) {
    var detailsExpanded by remember { mutableStateOf(false) }
    val statusParts = status.lines()
    val statusHint = statusParts.firstOrNull().orEmpty()
    val statusNumbers = statusParts.drop(1).firstOrNull().orEmpty()
    Box(Modifier.fillMaxSize()) {
        mapContent()

        Column(
            Modifier.align(Alignment.TopCenter).fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                color = Color.White.copy(alpha = 0.96f),
                shadowElevation = 3.dp,
                shape = RoundedCornerShape(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("足迹涂色", color = Ink, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(3.dp))
                        Text("GPS 足迹预览 · 道路涂色尚未接通", color = Muted, fontSize = 11.sp)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        val distanceLabel = when {
                            state.totalMeters < 1.0 && state.realPointCount > 0 -> "已记录起点"
                            state.totalMeters < 1000 -> "%.0f 米".format(Locale.CHINA, state.totalMeters)
                            else -> "%.2f km".format(Locale.CHINA, state.totalMeters / 1000)
                        }
                        Text(distanceLabel, color = Accent, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            FootprintInsights.traveledDays(state).toString() + " 天 · " +
                                FootprintInsights.exploredCells(state).toString() + " 格",
                            color = Muted, fontSize = 11.sp
                        )
                    }
                }
            }
            if (fix == null) {
                Surface(
                    color = Color.White.copy(alpha = 0.94f),
                    shape = RoundedCornerShape(13.dp), shadowElevation = 1.dp
                ) {
                    Text("正在等候当前位置 · 点击右下角定位按钮",
                        fontSize = 12.sp, color = Muted,
                        modifier = Modifier.fillMaxWidth().padding(10.dp))
                }
            }
        }

        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Surface(
                    onClick = onLocate,
                    shape = CircleShape,
                    color = Color.White,
                    shadowElevation = 4.dp,
                    modifier = Modifier.size(51.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(painterResource(R.drawable.ic_location), "定位我",
                            tint = Accent, modifier = Modifier.size(26.dp))
                    }
                }
            }
            Surface(
                color = Color.White.copy(alpha = 0.97f),
                shape = RoundedCornerShape(20.dp),
                shadowElevation = 4.dp
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).background(
                            if (running) Accent else Color(0xFFD7A663), CircleShape))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (running && statusHint.startsWith("刚刚保存")) "新足迹已保存"
                            else if (running) "自动监测已开启"
                            else if (automatic) "等待系统允许恢复"
                            else "自动足迹已暂停",
                            color = Ink, fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(checked = automatic, onCheckedChange = onToggle,
                            modifier = Modifier.height(34.dp))
                    }
                    if (statusHint.isNotBlank()) {
                        Text(statusHint, color = Muted, fontSize = 11.sp, maxLines = 1,
                            overflow = TextOverflow.Ellipsis)
                    }
                    if (statusNumbers.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(statusNumbers, color = Muted, fontSize = 11.sp, maxLines = 2,
                            overflow = TextOverflow.Ellipsis)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("地图 · " + mapProviderLabel, color = Muted, fontSize = 10.sp,
                            modifier = Modifier.weight(1f))
                        TextButton(onClick = { detailsExpanded = !detailsExpanded }) {
                            Text(if (detailsExpanded) "收起详情" else "查看详情",
                                color = Accent, fontSize = 11.sp)
                        }
                    }
                    if (detailsExpanded) {
                        HorizontalDivider(color = PageBackground)
                        Spacer(Modifier.height(7.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(onClick = onDemo, color = Color(0xFFF1EFFF),
                                shape = RoundedCornerShape(12.dp)) {
                                Text(
                                    if (state.tracks.any { it.demo }) "清除演示路线" else "试看涂色效果",
                                    color = Lilac, fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp)
                                )
                            }
                            Spacer(Modifier.width(9.dp))
                            Text("蓝绿=真实 · 紫色=演示", color = Muted, fontSize = 10.sp)
                        }
                        Spacer(Modifier.height(7.dp))
                        Text(
                            if (mapProviderLabel == "高德地图") "© 高德"
                            else "OpenFreeMap · © OpenStreetMap contributors",
                            color = Muted, fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TinyStat(value: String, title: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(value, color = Ink, fontWeight = FontWeight.Bold, fontSize = 21.sp)
        Spacer(Modifier.height(2.dp))
        Text(title, color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun PageHeading(eyebrow: String, title: String, desc: String) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 19.dp)) {
        Text(eyebrow.uppercase(Locale.ROOT), color = Accent, fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp)
        Spacer(Modifier.height(6.dp))
        Text(title, color = Ink, fontSize = 29.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(7.dp))
        Text(desc, color = Muted, fontSize = 13.sp)
    }
}

@Composable
private fun WhiteCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier = modifier, color = Color.White, shape = RoundedCornerShape(22.dp),
        shadowElevation = 2.dp) {
        Column(Modifier.fillMaxWidth().padding(17.dp), content = content)
    }
}

@Composable
private fun TripsPage(state: TrackState, onTrip: (Track) -> Unit) {
    val recent = FootprintInsights.lastVisited(state, 250)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            PageHeading("EXPLORE", "我的足迹", "走过的时间与路线，都在这里慢慢积累。")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                WhiteCard(Modifier.weight(1f)) {
                    Text("已走距离", color = Muted, fontSize = 12.sp)
                    Spacer(Modifier.height(7.dp))
                    Text("%.2f km".format(Locale.CHINA, state.totalMeters / 1000),
                        color = Accent, fontWeight = FontWeight.Bold, fontSize = 23.sp)
                }
                WhiteCard(Modifier.weight(1f)) {
                    Text("记录的天数", color = Muted, fontSize = 12.sp)
                    Spacer(Modifier.height(7.dp))
                    Text("${FootprintInsights.traveledDays(state)} 天",
                        color = Accent, fontWeight = FontWeight.Bold, fontSize = 23.sp)
                }
            }
            Spacer(Modifier.height(15.dp))
            Text("最近去过的路线", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink)
            Spacer(Modifier.height(2.dp))
            Text("按日期整理，点击任意路线回地图查看", color = Muted, fontSize = 12.sp)
        }
        if (recent.isEmpty()) {
            item {
                WhiteCard {
                    Text("还没有真实足迹", fontSize = 17.sp, color = Ink, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Text("打开地图，首次授权自动记录后，日常走路会在这里留下彩色记录。", color = Muted, fontSize = 13.sp)
                }
            }
        } else {
            items(recent, key = { it.id }) { trip ->
                Surface(onClick = { onTrip(trip) }, color = Color.White,
                    shape = RoundedCornerShape(20.dp), shadowElevation = 1.dp) {
                    Row(Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = AccentSoft, shape = RoundedCornerShape(15.dp),
                            modifier = Modifier.size(49.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(painterResource(R.drawable.ic_route), "足迹路线", tint = Accent)
                            }
                        }
                        Spacer(Modifier.width(13.dp))
                        Column(Modifier.weight(1f)) {
                            Text(SimpleDateFormat("MM月dd日  EEEE", Locale.CHINA).format(Date(trip.started)),
                                color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Spacer(Modifier.height(5.dp))
                            Text("${trip.points.size} 个定位点 · %.2f km".format(Locale.CHINA, Geo.distance(trip.points) / 1000),
                                color = Muted, fontSize = 12.sp)
                        }
                        Text("›", color = Accent, fontSize = 26.sp)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun ProfilePage(
    state: TrackState,
    automatic: Boolean,
    running: Boolean,
    status: String,
    needsBackground: Boolean,
    internalBackupCount: Int,
    onToggle: (Boolean) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onBackgroundSettings: () -> Unit,
    onCopyDiagnostics: () -> Unit,
    mapProviderLabel: String,
    onChooseAmap: () -> Unit
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        item {
            PageHeading("MY JOURNEY", "我的", "属于自己的城市探索档案，不需要账号。")
            WhiteCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = AccentSoft, shape = CircleShape, modifier = Modifier.size(64.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(painterResource(R.drawable.ic_person), "个人资料", tint = Accent, modifier = Modifier.size(36.dp))
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("自由探索者", color = Ink, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                        Spacer(Modifier.height(5.dp))
                        Text("你的足迹由自己保管", color = Muted, fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(22.dp))
                Row {
                    TinyStat(
                        if (state.totalMeters < 1000) "%.0f 米".format(Locale.CHINA, state.totalMeters)
                        else "%.2f km".format(Locale.CHINA, state.totalMeters / 1000),
                        "总里程", Modifier.weight(1f)
                    )
                    TinyStat("${FootprintInsights.traveledDays(state)}", "足迹天数", Modifier.weight(1f))
                    TinyStat("${FootprintInsights.exploredCells(state)}", "探索网格", Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                Text("探索网格约为半公里级估算，不代表真实道路覆盖率。", color = Muted, fontSize = 11.sp)
            }
        }
        item {
            WhiteCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("自动足迹", color = Ink, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text(if (running) "运行中 · 外出自动尝试记录" else if (automatic) "系统已暂停，打开 App 可尝试恢复" else "启用一次，平时不用手动开始",
                            color = Muted, fontSize = 12.sp)
                    }
                    Switch(checked = automatic, onCheckedChange = onToggle)
                }
                Spacer(Modifier.height(8.dp))
                Text(status, color = Muted, fontSize = 11.sp)
                Spacer(Modifier.height(7.dp))
                TextButton(onClick = onCopyDiagnostics) {
                    Text("查看记录诊断（无坐标）", fontSize = 12.sp)
                }
                if (needsBackground) {
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = PageBackground)
                    Spacer(Modifier.height(10.dp))
                    Text("为了重启后尽量自动恢复，在系统定位权限中设置“始终允许”。即使授权，系统省电策略仍可能打断。", color = Muted, fontSize = 12.sp)
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(onClick = onBackgroundSettings, shape = RoundedCornerShape(13.dp)) {
                        Text("打开后台定位权限设置")
                    }
                }
            }
        }
        item {
            WhiteCard {
                Text("备份与恢复", color = Ink, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Spacer(Modifier.height(6.dp))
                Text("备份文件可保存到手机或云盘。恢复采用合并方式，不清空现有足迹。",
                    color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(15.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = onExport, modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp)) { Text("导出备份") }
                    OutlinedButton(onClick = onImport, modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp)) { Text("导入备份") }
                }
                Spacer(Modifier.height(10.dp))
                Text("本地每日快照：$internalBackupCount 份 · 卸载 App 会一起删除；请定期导出到云盘。",
                    color = Muted, fontSize = 11.sp)
            }
        }
        item {
            WhiteCard {
                Text("地图与功能扩展", color = Ink, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Spacer(Modifier.height(8.dp))
                Text("当前底图：$mapProviderLabel", color = Accent, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                Spacer(Modifier.height(5.dp))
                Text("高德需配置专属开发者 Key 并同意隐私政策。目前使用开放地图，历史轨迹仍保存在本机。", color = Muted, fontSize = 12.sp)
                if (BuildConfig.AMAP_KEY.isNotBlank() && mapProviderLabel != "高德地图") {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onChooseAmap) { Text("切换到高德地图") }
                }
                Spacer(Modifier.height(11.dp))
                HorizontalDivider(color = PageBackground)
                Spacer(Modifier.height(9.dp))
                Text("当前显示 GPS 轨迹；道路匹配与累计核心已测试，但尚未接通真实道路涂色。", color = Muted, fontSize = 12.sp)
            }
        }
        item {
            Text("V0.4 预览版 · 精度受 GPS 和电池策略影响 · 定位点只存本机，地图瓦片在线请求第三方服务。",
                color = Muted, fontSize = 11.sp, modifier = Modifier.padding(6.dp))
        }
    }
}
