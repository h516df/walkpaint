package cn.walkpaint

import android.Manifest
import android.content.Context
import android.content.ClipData
import android.content.ClipboardManager
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
import org.maplibre.android.maps.Style
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

class MainActivity : ComponentActivity() {
    private val repository by lazy { (application as WalkPaintApp).repository }
    private var currentTab by mutableIntStateOf(0)
    private var snapshot by mutableStateOf(TrackState())
    private var userFix by mutableStateOf<Location?>(null)
    private var trackingStatus by mutableStateOf("正在检查位置状态")
    private var recordingTitle by mutableStateOf("正在检查记录状态")
    private var recordingHealthy by mutableStateOf(false)
    private var trackingEnabled by mutableStateOf(false)
    private var trackingRunning by mutableStateOf(false)
    private var backgroundGranted by mutableStateOf(false)
    private var amapConsent by mutableStateOf(false)
    private var showAmapConsent by mutableStateOf(false)
    private val amapController by lazy { AmapMapController(this) }
    private val amapActive get() = BuildConfig.AMAP_KEY.isNotBlank() && amapConsent
    private var showDiagnostics by mutableStateOf(false)
    private var diagnosticText by mutableStateOf("")
    private var confirmEnable by mutableStateOf(false)
    private var promptLocation by mutableStateOf(false)
    private var pendingSettingsEnable = false
    private val refreshExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
    private val refreshing = java.util.concurrent.atomic.AtomicBoolean(false)
    private var roadState by mutableStateOf(RoadState())
    private var mapRoadState by mutableStateOf(RoadState())
    private var mapTrackState by mutableStateOf(TrackState())
    private var selectedDay by mutableStateOf<String?>(null)
    private var roadAreaHint by mutableStateOf("")
    private var boundaryCatalog: RoadCatalog?=null
    private var boundaryRoads: List<RoadSegment>?=null
    private fun clearDaySelection() { selectedDay=null;lastRevision=-1;updateUi() }
    private var roadBusy by mutableStateOf(false)
    private var roadMessage by mutableStateOf("")
    private var showRoadPack by mutableStateOf(false)
    private var downloadArea by mutableStateOf<NearbyRoads.Area?>(null)
    private var roadDownload: RoadDownload? = null
    private var onlineMap by mutableStateOf(false)
    private var showOnlineConsent by mutableStateOf(false)
    private var paintedJson = RoadState().geoJson(true)
    private var roadJson = RoadState().geoJson(false)
    private var realGeoJson = geoJson(emptyList())
    private var demoGeoJson = geoJson(emptyList())
    private var pendingEnable by mutableStateOf(false)
    private var pendingCenter by mutableStateOf(false)
    private var lastRevision = -1L

    private var mapInstance: MapLibreMap? = null
    private var mapWidget: MapView? = null
    private var firstMapCenter = true
    // A tap on a historical trip must survive the tab switch / asynchronous style load.
    private var pendingTripFocus: Triple<Double, Double, Double>? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var locationManager: LocationManager? = null

    private val visibleLocationListener = LocationListener { fix ->
        if (fix.hasAccuracy() && fix.accuracy in 1f..200f) {
            updatePosition(fix, pendingCenter)
            pendingCenter = false
        }
    }
    private val refreshTask = object : Runnable {
        override fun run() {
            updateUi()
            mainHandler.postDelayed(this, 2000L)
        }
    }

    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ ->
            if (AmbientTracking.hasFineLocation(this)) {
                beginVisibleLocation()
                if (pendingEnable || AmbientTracking.enabled(this)) {
                    AmbientTracking.setEnabled(this, true)
                    AmbientTracking.start(this)
                    requestNotification()
                } else if (pendingCenter) centerUser()
            } else toast("需要精确定位才能看到当前位置和绘制足迹")
            if (AmbientTracking.hasFineLocation(this)) pendingSettingsEnable = false
            pendingEnable = false
            updateUi()
        }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ -> updateUi() }

    private val roadPackLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri != null) {
            roadBusy = true; roadMessage = "正在校验道路包并匹配历史足迹…"
            refreshExecutor.execute {
                runCatching {
                    val memory = ByteArrayOutputStream()
                    contentResolver.openInputStream(uri)?.use { input ->
                        val buffer = ByteArray(16384)
                        while(true) {
                            val n = input.read(buffer); if(n < 0) break
                            require(memory.size()+n <= RoadPackCodec.MAX_BYTES) { "道路包超过8MB" }
                            memory.write(buffer,0,n)
                        }
                    } ?: error("无法读取文件")
                    repository.importRoadPack(memory.toString("UTF-8"))
                }.onSuccess { n -> runOnUiThread {
                    roadBusy = false; roadMessage = "已导入 $n 条真实道路，历史足迹已重新匹配"
                    updateUi()
                } }.onFailure { e -> runOnUiThread {
                    roadBusy = false; roadMessage = "导入失败：" + (e.message?.take(120) ?: "文件格式无效")
                } }
            }
        }
    }

    private val exportLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri != null) Thread {
                runCatching {
                    val payload = repository.exportBackup().toByteArray(Charsets.UTF_8)
                    contentResolver.openOutputStream(uri)?.use { it.write(payload) }
                        ?: error("无法写入目标文件")
                }.onSuccess { runOnUiThread { toast("足迹备份已保存，可放入云盘") } }
                    .onFailure { runOnUiThread { toast("导出失败：请检查目标位置或空间") } }
            }.start()
        }

    private val importLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) Thread {
                runCatching {
                    val buffer = ByteArray(16 * 1024)
                    val memory = ByteArrayOutputStream()
                    val input = contentResolver.openInputStream(uri) ?: error("无法读取文件")
                    input.use { stream ->
                        while (true) {
                            val n = stream.read(buffer)
                            if (n < 0) break
                            require(memory.size() + n <= 80 * 1024 * 1024) { "备份超过80MB" }
                            memory.write(buffer, 0, n)
                        }
                    }
                    repository.importBackup(memory.toString("UTF-8"))
                }.onSuccess { count ->
                    runOnUiThread {
                        updateUi()
                        toast("已合并 $count 条历史足迹，原数据未删除")
                    }
                }.onFailure { runOnUiThread { toast("无法导入，请确认是有效的足迹备份") } }
            }.start()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingEnable = savedInstanceState?.getBoolean("pendingEnable") ?: false
        pendingSettingsEnable = savedInstanceState?.getBoolean("pendingSettingsEnable") ?: false
        MapLibre.getInstance(applicationContext)
        val mapPref = getSharedPreferences("ui_onboarding", Context.MODE_PRIVATE)
        onlineMap = mapPref.getBoolean("openfreemap_basemap_consent", false)
        amapConsent = mapPref.getBoolean("amap_sdk_consent", false)
        showAmapConsent = BuildConfig.AMAP_KEY.isNotBlank() &&
            !amapConsent && !mapPref.getBoolean("amap_sdk_decision_seen", false)
        if (!AmbientTracking.enabled(this) &&
            !getSharedPreferences("ui_onboarding", Context.MODE_PRIVATE).getBoolean("auto_notice_v3_seen", false)) {
            promptLocation = true
        }
        updateUi()
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Accent, onPrimary = Color.White,
                    background = PageBackground, surface = Color.White,
                    onSurface = Ink, secondary = Lilac, surfaceContainerLow = PageBackground
                )
            ) {
                AppShell(
                    selectedTab = currentTab,
                    onTab = { currentTab = it },
                    state = snapshot,
                    fix = userFix,
                    automatic = trackingEnabled,
                    running = trackingRunning,
                    trackingStatus = trackingStatus,
                    recordingTitle = recordingTitle,
                    recordingHealthy = recordingHealthy,
                    needsBackground = trackingEnabled && !backgroundGranted,
                    selectedDay = selectedDay,
                    mapRoadState = mapRoadState,
                    roadAreaHint = roadAreaHint,
                    onShowAll = { clearDaySelection() },
                    onLocate = { clearDaySelection(); centerUser() },
                    onToggleTracking = { requested ->
                        if (requested) confirmEnable = true
                        else {
                            AmbientTracking.stop(this)
                            updateUi()
                        }
                    },
                    onDemo = {
                        runCatching {
                            repository.setDemo(!snapshot.tracks.any { it.demo })
                            updateUi()
                            val demo = snapshot.tracks.firstOrNull { it.demo }
                            if (demo != null) focusCoordinate(31.2318, 121.4742, 15.0)
                        }.onFailure { toast("演示数据不可写入，请检查存储") }
                    },
                    onTrip = { trip ->
                        selectedDay=DailyFootprints.key(trip.started)
                        lastRevision=-1
                        val p = trip.points.lastOrNull()
                        if (p != null) {
                            val zoom = when {
                                Geo.distance(trip.points) < 100.0 -> 18.0
                                Geo.distance(trip.points) < 800.0 -> 16.5
                                else -> 14.5
                            }
                            pendingTripFocus = Triple(p.lat, p.lon, zoom)
                            currentTab = 0
                        }
                    },
                    onExport = {
                        val title = "WalkPaint-${SimpleDateFormat("yyyyMMdd-HHmm", Locale.ROOT).format(Date())}.json"
                        exportLauncher.launch(title)
                    },
                    onImport = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                    onBackgroundSettings = { openAppSettings() },
                    onCopyDiagnostics = { diagnosticText = trackingDiagnostics(); showDiagnostics = true },
                    internalBackupCount = repository.backupFiles().size,
                    mapProviderLabel = if (amapActive) "高德地图" else if(onlineMap) "OpenFreeMap" else "本地离线道路",
                    onChooseAmap = { showAmapConsent = true },
                    onOnlineMap = { showOnlineConsent = true },
                    roadState = roadState,
                    onRoadPack = { showRoadPack = true },
                    mapContent = { if (amapActive) AmapNativeView() else NativeMapView() }
                )

                if(showOnlineConsent) AlertDialog(
                    onDismissRequest={ showOnlineConsent=false },
                    title={ Text("联网显示底图？") },
                    text={ Text("OpenFreeMap 将收到当前及浏览地图区域、IP 和地图请求；区域可能反映私人位置。同意后会记住选择，今后打开地图也会联网；可随时在此选择离线。不上传足迹文件，也不从瓦片采集道路。默认离线，拒绝仍可记录、匹配和涂色。") },
                    confirmButton={ Button(onClick={
                        onlineMap=true; showOnlineConsent=false
                        getSharedPreferences("ui_onboarding",Context.MODE_PRIVATE).edit()
                            .putBoolean("openfreemap_basemap_consent",true).apply()
                        // Recreate only the map view after explicit consent.
                        currentTab=2
                    }) { Text("同意联网并记住") } },
                    dismissButton={ TextButton(onClick={
                        onlineMap=false; showOnlineConsent=false; currentTab=2
                        getSharedPreferences("ui_onboarding",Context.MODE_PRIVATE).edit()
                            .putBoolean("openfreemap_basemap_consent",false).apply()
                    }) { Text("使用离线道路") } }
                )

                if(showRoadPack) AlertDialog(
                    onDismissRequest = { if(!roadBusy) showRoadPack = false },
                    title = { Text("附近道路") },
                    text = { Column(Modifier.verticalScroll(rememberScrollState())) {
                        Text(roadState.summary)
                        Spacer(Modifier.height(12.dp))
                        Text("先下载附近约1.2公里范围的道路，之后离线记录和上色；走到新区域时再下载。路口、精度差或道路缺失时可能少涂。也可以导入已有道路文件。")
                        Spacer(Modifier.height(12.dp))
                        Button(enabled=!roadBusy, onClick={ prepareNearbyRoads() }, modifier=Modifier.fillMaxWidth()) {
                            Text("下载附近道路")
                        }
                        if(roadBusy) {
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(modifier=Modifier.fillMaxWidth())
                            if(roadDownload != null) TextButton(onClick={ roadDownload?.cancel() }) { Text("取消下载") }
                        }
                        Text("© OpenStreetMap contributors · ODbL-1.0", fontSize=12.sp)
                        if(roadMessage.isNotBlank()) Text(roadMessage,color=Ink)
                    } },
                    confirmButton = { Button(enabled=!roadBusy,onClick={
                        roadPackLauncher.launch(arrayOf("application/geo+json","application/json","text/plain","*/*"))
                    }) { Text("选择道路包文件") } },
                    dismissButton = { TextButton(enabled=!roadBusy,onClick={
                        showRoadPack=false
                        val fix=userFix?.takeIf {
                            NearbyRoads.usableFix(android.os.SystemClock.elapsedRealtimeNanos()-it.elapsedRealtimeNanos,it.accuracy)
                        }?.let { TrackPoint(it.latitude,it.longitude,it.time,it.accuracy) }
                        RoadMapFocus.choose(snapshot,fix,roadState)?.let { p ->
                            pendingTripFocus=Triple(p.lat,p.lon,17.0)
                            currentTab=0
                            focusCoordinate(p.lat,p.lon,17.0)
                        }
                    }) { Text("查看地图") } }
                )

                downloadArea?.let { area -> AlertDialog(
                    onDismissRequest={ downloadArea=null },
                    title={ Text("下载附近道路") },
                    text={ Text("本次向 Overpass 道路服务（overpass-api.de）发送附近约1.2公里范围和 IP。区域中心约按100米取整，不发送完整足迹或历史路线。仅本次下载，不会自动联网更新；完成后道路保存在本机并匹配已有足迹。网络失败可重试，原足迹和道路保留。\n\n© OpenStreetMap contributors · ODbL-1.0") },
                    confirmButton={ Button(onClick={ downloadArea=null; downloadNearbyRoads(area) }) { Text("同意并下载本次") } },
                    dismissButton={ TextButton(onClick={ downloadArea=null }) { Text("暂不下载") } }
                ) }

                if (showDiagnostics) AlertDialog(
                    onDismissRequest = { showDiagnostics = false },
                    title = { Text("记录诊断 · 不含坐标") },
                    text = { Column(Modifier.verticalScroll(rememberScrollState())) {
                        Text(diagnosticText, fontSize = 13.sp)
                    } },
                    confirmButton = { TextButton(onClick = { copyTrackingDiagnostics() }) { Text("复制诊断") } },
                    dismissButton = { TextButton(onClick = { showDiagnostics = false }) { Text("关闭") } }
                )
                if (promptLocation) AlertDialog(
                    onDismissRequest = {
                        promptLocation = false
                        getSharedPreferences("ui_onboarding", Context.MODE_PRIVATE)
                            .edit().putBoolean("auto_notice_v3_seen", true).apply()
                    },
                    title = { Text("自动记录走过的足迹", fontWeight = FontWeight.Bold, color = Ink) },
                    text = { Text("允许精确定位后即启动自动足迹，使用前台服务、持续 GPS 和持续通知，会增加电耗，可在“我的”里随时暂停。定位点只保存在本机；系统省电策略可能中断记录。") },
                    confirmButton = {
                        Button(onClick = {
                            promptLocation = false
                            getSharedPreferences("ui_onboarding", Context.MODE_PRIVATE)
                                .edit().putBoolean("auto_notice_v3_seen", true).apply()
                            enableFromChoice()
                        }) { Text("同意并开启自动足迹") }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            promptLocation = false
                            getSharedPreferences("ui_onboarding", Context.MODE_PRIVATE)
                                .edit().putBoolean("auto_notice_v3_seen", true).apply()
                        }) { Text("稍后再说") }
                    }
                )
                if (showAmapConsent && !promptLocation && !confirmEnable) AlertDialog(
                    onDismissRequest = {
                        showAmapConsent = false
                        getSharedPreferences("ui_onboarding", Context.MODE_PRIVATE).edit()
                            .putBoolean("amap_sdk_decision_seen", true).apply()
                    },
                    title = { Text("是否使用高德地图？", fontWeight = FontWeight.Bold) },
                    text = { Column {
                        Text("高德开放平台地图 SDK 由北京高德图强科技有限公司提供，用于地图展示；" +
                            "可能收集经纬度、IP/网络、GNSS、设备参数、系统和传感器信息。" +
                            "足迹数据库仍保留本机；拒绝使用高德不影响自动记录。")
                        TextButton(onClick = {
                            startActivity(Intent(Intent.ACTION_VIEW,
                                Uri.parse("https://lbs.amap.com/home/privacy/")))
                        }) { Text("阅读高德隐私政策") }
                    } },
                    confirmButton = {
                        Button(onClick = {
                            getSharedPreferences("ui_onboarding", Context.MODE_PRIVATE).edit()
                                .putBoolean("amap_sdk_consent", true)
                                .putBoolean("amap_sdk_decision_seen", true).apply()
                            amapConsent = true
                            showAmapConsent = false
                        }) { Text("同意使用高德") }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            showAmapConsent = false
                            getSharedPreferences("ui_onboarding", Context.MODE_PRIVATE).edit()
                                .putBoolean("amap_sdk_decision_seen", true).apply()
                        }) { Text("暂不用") }
                    }
                )
                if (confirmEnable) AlertDialog(
                    onDismissRequest = { confirmEnable = false },
                    title = { Text("开启自动足迹？", color = Ink, fontWeight = FontWeight.Bold) },
                    text = {
                        Text(
                            "允许精确定位后即启动自动足迹，使用前台服务、持续 GPS 和持续通知，会增加电耗，可在我的里随时暂停。" +
                                "系统省电或强制停止可能中断记录；重启恢复通常需要“始终允许定位”。"
                        )
                    },
                    confirmButton = {
                        Button(onClick = {
                            confirmEnable = false
                            enableFromChoice()
                        }) { Text("启用自动足迹") }
                    },
                    dismissButton = { TextButton(onClick = { confirmEnable = false }) { Text("暂不开启") } }
                )
            }
        }
        mainHandler.post(refreshTask)
    }

    private fun updateUi() {
        val now = System.currentTimeMillis()
        trackingEnabled = AmbientTracking.enabled(this)
        trackingRunning = AmbientTracking.running
        backgroundGranted = AmbientTracking.hasBackgroundLocation(this)
        userFix = chooseMapFix(listOfNotNull(AmbientTracking.lastGps, AmbientTracking.lastFix,userFix))
        val latestGps = AmbientTracking.lastGps
        val recentGps = latestGps?.takeIf { it.hasAccuracy() && it.accuracy >= 1f && now - it.time in 0L..60_000L }
        val diagnostic = RecordingDiagnostics.snapshot(this)
        fun recordedAt(key: String) = (diagnostic[key] as? Number)?.toLong() ?: 0L
        val savedAt = maxOf(AmbientTracking.lastSaved, recordedAt("saved_at")).takeIf { it > 0 }
        val savedAge = savedAt?.let { now - it }
        val savedLabel = when {
            savedAge == null -> "还没有足迹"
            savedAge in 0L..30_000L -> "刚刚"
            savedAge in 0L..59_999L -> (savedAge / 1_000L).toString() + " 秒前"
            savedAge in 0L..3_599_999L -> (savedAge / 60_000L).toString() + " 分钟前"
            else -> SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(savedAt))
        }
        val gpsEnabled = getSystemService(LocationManager::class.java)
            .isProviderEnabled(LocationManager.GPS_PROVIDER)
        val status = RecordingStatus.assess(RecordingDiagnostics.signals(this, repository.lastError))
        recordingTitle=status.title; recordingHealthy=status.healthy
        val diagnosis = status.title + " · " + status.hint
        val gpsLabel = when {
            recentGps != null -> "GPS ±" + recentGps.accuracy.toInt() + "米"
            latestGps != null && latestGps.accuracy < 1f -> "GPS 精度尚不可用"
            latestGps != null -> "GPS 信号已过期"
            else -> "正在搜寻 GPS"
        }
        trackingStatus = diagnosis + "\n" + gpsLabel + " · 已存 " +
            snapshot.realPointCount + " 点 · 上次实际入库 " + savedLabel
        roadAreaHint = when {
            roadState.error != null -> roadState.error!!
            roadState.roads.isEmpty() -> "还没有附近道路，GPS 记录独立保存；下载需单次同意"
            userFix==null -> ""
            else -> {
                if(boundaryRoads !== roadState.roads) {
                    boundaryRoads=roadState.roads
                    boundaryCatalog=runCatching { RoadCatalog(roadState.roads) }.getOrNull()
                }
                val fix=userFix!!
                if(boundaryCatalog?.near(TrackPoint(fix.latitude,fix.longitude,fix.time,fix.accuracy))?.isEmpty()==true)
                    "当前位置附近没有已导入道路 · 已存足迹保留，新区域暂不上色"
                else ""
            }
        }
        if (lastRevision != repository.revision && refreshing.compareAndSet(false, true)) {
            val day=selectedDay
            refreshExecutor.execute {
                try {
                    val revision = repository.revision
                    val next = repository.snapshot()
                    // Compute expensive history/geometry work away from the UI thread.
                    next.totalMeters; FootprintInsights.traveledDays(next); FootprintInsights.exploredCells(next)
                    val road = repository.roadSnapshot()
                    val roadBase = road.geoJson(false)
                    val displayedRoad = if(day==null) road else repository.roadSnapshotForDay(day)
                    val displayedTrack = DailyFootprints.select(next,day)
                    val painted = displayedRoad.geoJson(true)
                    val real = geoJson(displayedTrack.realTracks)
                    val demo = geoJson(next.tracks.filter { it.demo })
                    runOnUiThread {
                        if(selectedDay!=day) return@runOnUiThread
                        snapshot = next; lastRevision = revision
                        mapTrackState=displayedTrack; mapRoadState=displayedRoad
                        realGeoJson = real; demoGeoJson = demo
                        roadState = road; roadJson = roadBase; paintedJson = painted
                        updateMapLayers()
                    }
                } finally { refreshing.set(false) }
            }
        }
        updateLocationLayer()
    }

    private fun enableFromChoice() {
        getSharedPreferences("ui_onboarding", Context.MODE_PRIVATE).edit()
            .putBoolean("auto_notice_v3_seen", true).apply()
        if (AmbientTracking.hasFineLocation(this)) {
            AmbientTracking.setEnabled(this, true)
            AmbientTracking.start(this)
            requestNotification()
            updateUi()
        } else {
            pendingEnable = true
            pendingSettingsEnable = true
            askLocation()
        }
    }

    private fun requestNotification() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun askLocation() {
        locationPermissionLauncher.launch(arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION
        ))
    }

    private fun chooseMapFix(fixes: List<Location>): Location? {
        val valid=fixes.filter { it.hasAccuracy() && it.latitude.isFinite() && it.longitude.isFinite() &&
            it.latitude in -90.0..90.0 && it.longitude in -180.0..180.0 }
        val now=android.os.SystemClock.elapsedRealtimeNanos()
        val choice=MapFixChoice.choose(valid.map { MapFixQuality(it.provider,(now-it.elapsedRealtimeNanos)/1_000_000,it.accuracy) })
        return choice?.let { valid[it] }
    }

    private fun centerUser() {
        if (!AmbientTracking.hasFineLocation(this)) {
            pendingCenter = true
            askLocation()
            return
        }
        val fix = userFix ?: latestSystemLocation()
        if (fix != null) {
            updatePosition(fix, true)
            pendingCenter = false
        } else {
            pendingCenter = true
            val savedPoint = snapshot.realTracks.lastOrNull()?.points?.lastOrNull()
            if (savedPoint != null) {
                focusCoordinate(savedPoint.lat, savedPoint.lon, 16.0)
                toast("暂时没有当前 GPS，已定位到最近足迹，正在继续寻找卫星信号")
            } else {
                toast("正在获取当前定位，请检查系统 GPS 并到室外稍等")
            }
            beginVisibleLocation()
        }
    }

    private fun latestSystemLocation(): Location? {
        if (!AmbientTracking.hasFineLocation(this)) return null
        val manager = locationManager ?: getSystemService(LocationManager::class.java).also { locationManager = it }
        return try {
            chooseMapFix(listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
                .filter { manager.isProviderEnabled(it) }
                .mapNotNull { manager.getLastKnownLocation(it) } + listOfNotNull(AmbientTracking.lastGps))
        } catch (_: Exception) { null }
    }

    private fun beginVisibleLocation() {
        if (!AmbientTracking.hasFineLocation(this)) return
        val lm = locationManager ?: getSystemService(LocationManager::class.java).also { locationManager = it }
        runCatching {
            latestSystemLocation()?.let { updatePosition(it, firstMapCenter || pendingCenter) }
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2_000, 0f, visibleLocationListener, Looper.getMainLooper())
            }
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 20_000, 8f, visibleLocationListener, Looper.getMainLooper())
            }
        }
    }

    private fun prepareNearbyRoads() {
        val fix = userFix ?: latestSystemLocation()
        val age = fix?.let { android.os.SystemClock.elapsedRealtimeNanos() - it.elapsedRealtimeNanos }
        if(fix == null || age == null || !fix.hasAccuracy() || !NearbyRoads.usableFix(age, fix.accuracy)) {
            roadMessage = "请先点地图定位按钮，取得当前位置后再下载附近道路"
            return
        }
        runCatching { NearbyRoads.area(fix.latitude,fix.longitude) }
            .onSuccess { downloadArea=it }
            .onFailure { roadMessage="当前位置不支持附近下载，请导入道路文件" }
    }

    private fun downloadNearbyRoads(area: NearbyRoads.Area) {
        if(roadBusy) return
        val job=RoadDownload()
        roadDownload=job; roadBusy=true; roadMessage="正在下载附近道路，足迹记录继续…"
        Thread {
            runCatching {
                val pack=job.fetch(area)
                repository.importRoadPack(pack)
            }.onSuccess { n -> runOnUiThread {
                if(!isDestroyed) {
                    roadBusy=false; roadDownload=null
                    roadMessage="已保存 $n 条附近道路，历史足迹已匹配。现在可以离线走路上色"
                    updateUi()
                }
            } }.onFailure { e -> runOnUiThread {
                if(!isDestroyed) {
                    roadBusy=false; roadDownload=null
                    android.util.Log.w("WalkPaintRoads", "failure="+e.javaClass.simpleName+";cause="+(e.cause?.javaClass?.simpleName ?: "none"))
                    roadMessage=when(e) {
                        is java.net.SocketTimeoutException -> "下载超时，请稍后重试；原足迹和道路保留"
                        is java.io.IOException -> "下载未完成，请检查网络后重试；原足迹和道路保留"
                        is IllegalArgumentException, is IllegalStateException -> (e.message?.take(100) ?: "道路下载失败") + "；原足迹和道路保留"
                        else -> "道路数据暂不可用，请稍后重试；原足迹和道路保留"
                    }
                }
            } }
        }.start()
    }

    private fun updatePosition(fix: Location, center: Boolean) {
        val chosen=chooseMapFix(listOfNotNull(fix,userFix,AmbientTracking.lastGps)) ?: return
        userFix = chosen
        AmbientTracking.lastFix = chosen
        updateLocationLayer()
        if (center || firstMapCenter) {
            focusCoordinate(chosen.latitude, chosen.longitude, 18.0)
            firstMapCenter = false
        }
    }

    private fun trackingDiagnostics(): String {
        // No coordinates, route identifiers or exact street names leave the app.
        val now = System.currentTimeMillis()
        fun age(whenAt: Long?): String =
            if (whenAt == null || whenAt <= 0L) "无"
            else ((now - whenAt).coerceAtLeast(0L) / 1_000L).toString() + " 秒前"
        val lastFix = AmbientTracking.lastGps
        val diagnostic=RecordingDiagnostics.snapshot(this)
        val lastSaved=maxOf(AmbientTracking.lastSaved,(diagnostic["saved_at"] as? Number)?.toLong() ?: 0).takeIf { it>0 }
        val gpsEnabled = getSystemService(LocationManager::class.java)
            .isProviderEnabled(LocationManager.GPS_PROVIDER)
        val power = getSystemService(android.os.PowerManager::class.java)
        val notifications = getSystemService(android.app.NotificationManager::class.java)
        val rows = listOf(
            "WalkPaint " + BuildConfig.VERSION_NAME + " / Android " + Build.VERSION.RELEASE,
            "自动记录开关：" + AmbientTracking.enabled(this),
            "前台服务实际运行：" + AmbientTracking.running,
            "精确位置授权：" + AmbientTracking.hasFineLocation(this),
            "后台定位授权：" + AmbientTracking.hasBackgroundLocation(this),
            "系统 GPS 开启：" + gpsEnabled,
            "通知已允许：" + notifications.areNotificationsEnabled(),
            "系统省电白名单：" + power.isIgnoringBatteryOptimizations(packageName),
            "当前屏幕亮起：" + power.isInteractive,
            "当前 Doze 待机：" + power.isDeviceIdleMode,
            "当前省电模式：" + power.isPowerSaveMode,
            "最近 GPS 信号：" + age(lastFix?.time),
            "最近 GPS 精度：" + (lastFix?.accuracy?.toInt()?.toString() ?: "未知") + "米",
            "地图蓝点来源：" + (userFix?.provider?.takeIf { it in setOf("gps","network") } ?: "无"),
            "上次足迹写入：" + age(lastSaved),
            "本地足迹点：" + snapshot.realPointCount,
            "状态：" + trackingStatus.substringBefore("\n")
        )
        return rows.joinToString("\n") + "\n\n" + RecordingDiagnostics.summary(this) +
            "\n\n道路包：${roadState.roads.size} 条；确认段 ${roadState.matched}；未确认段 ${roadState.unmapped}。本机匹配，无瓦片数据采集。" +
            "\n\n持续 GPS 已启用，不长期持有 CPU 唤醒锁；常规诊断每30秒批量保存，异常退出最多损失末尾30秒诊断（不影响 SQLite 足迹）。vivo 等厂商需允许后台运行；强制停止后须手动打开 App，不能保证永不断录。"
    }

    private fun copyTrackingDiagnostics() {
        val clip = ClipData.newPlainText("WalkPaint 定位诊断", trackingDiagnostics())
        getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
        toast("诊断已复制，不包含经纬度和个人路线")
    }

    private fun openAppSettings() {
        toast("在定位权限中选择“始终允许”，再返回 App")
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        })
    }

    private fun focusCoordinate(lat: Double, lon: Double, zoom: Double = 16.0) {
        if (amapActive) amapController.focus(lat, lon, zoom)
        else mapInstance?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lon), zoom))
    }

    private fun makeMapView(): MapView {
        val view = MapView(this)
        view.onCreate(null)
        mapWidget = view
        view.getMapAsync { loaded ->
            if (mapWidget !== view) return@getMapAsync
            mapInstance = loaded
            val builder = if(onlineMap) Style.Builder().fromUri("https://tiles.openfreemap.org/styles/liberty")
                else Style.Builder().fromJson("""{"version":8,"sources":{},"layers":[{"id":"local-background","type":"background","paint":{"background-color":"#f4f8f6"}}]}""")
            loaded.setStyle(builder) { style ->
                if (mapWidget !== view) return@setStyle
                style.addSource(GeoJsonSource("wp-real", realGeoJson))
                style.addSource(GeoJsonSource("wp-demo", demoGeoJson))
                style.addSource(GeoJsonSource("wp-user", userJson(userFix)))
                style.addSource(GeoJsonSource("wp-roads", roadJson))
                style.addSource(GeoJsonSource("wp-painted", paintedJson))
                style.addLayer(LineLayer("wp-real-outline", "wp-real").withProperties(
                    PropertyFactory.lineColor(android.graphics.Color.WHITE),
                    PropertyFactory.lineWidth(2f),
                    PropertyFactory.lineOpacity(0.25f),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND)
                ))
                style.addLayer(LineLayer("wp-real-line", "wp-real").withProperties(
                    PropertyFactory.lineColor(android.graphics.Color.GRAY),
                    PropertyFactory.lineWidth(1.5f),
                    PropertyFactory.lineOpacity(0.45f),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND)
                ))
                style.addLayer(CircleLayer("wp-real-seeds", "wp-real").withProperties(
                    PropertyFactory.circleColor(android.graphics.Color.GRAY),
                    PropertyFactory.circleRadius(2f),
                    PropertyFactory.circleOpacity(0.45f),
                    PropertyFactory.circleStrokeColor(android.graphics.Color.WHITE),
                    PropertyFactory.circleStrokeWidth(1.5f)
                ))
                style.addLayer(LineLayer("wp-road-base","wp-roads").withProperties(
                    PropertyFactory.lineColor(android.graphics.Color.rgb(132,146,150)),
                    PropertyFactory.lineWidth(3f),PropertyFactory.lineOpacity(0.85f)))
                style.addLayer(LineLayer("wp-painted-outline","wp-painted").withProperties(
                    PropertyFactory.lineColor(android.graphics.Color.WHITE),PropertyFactory.lineWidth(9f),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)))
                style.addLayer(LineLayer("wp-painted-line","wp-painted").withProperties(
                    PropertyFactory.lineColor(android.graphics.Color.rgb(0,137,115)),PropertyFactory.lineWidth(6f),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)))
                style.addLayer(LineLayer("wp-demo-line", "wp-demo").withProperties(
                    PropertyFactory.lineColor(android.graphics.Color.rgb(133, 104, 197)),
                    PropertyFactory.lineWidth(6f),
                    PropertyFactory.lineOpacity(0.8f),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND)
                ))
                style.addLayer(CircleLayer("wp-user-halo", "wp-user").withProperties(
                    PropertyFactory.circleColor(android.graphics.Color.rgb(29, 132, 211)),
                    PropertyFactory.circleRadius(16f),
                    PropertyFactory.circleOpacity(0.18f)
                ))
                style.addLayer(CircleLayer("wp-user-dot", "wp-user").withProperties(
                    PropertyFactory.circleColor(android.graphics.Color.rgb(23, 119, 205)),
                    PropertyFactory.circleRadius(7f),
                    PropertyFactory.circleStrokeColor(android.graphics.Color.WHITE),
                    PropertyFactory.circleStrokeWidth(1.5f)
                ))
                val selected = pendingTripFocus
                val fix = userFix ?: latestSystemLocation()
                if (selected != null) {
                    loaded.moveCamera(CameraUpdateFactory.newLatLngZoom(
                        LatLng(selected.first, selected.second), selected.third))
                    pendingTripFocus = null
                    firstMapCenter = false
                } else if (fix != null) {
                    updatePosition(fix, true)
                } else {
                    val recent = snapshot.realTracks.flatMap { it.points }.lastOrNull()
                    if (recent != null) loaded.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(recent.lat, recent.lon), 18.0))
                    else loaded.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(20.0, 0.0), 2.5))
                }
            }
        }
        return view
    }

    private fun updateMapLayers() {
        if (amapActive) {
            amapController.render(mapTrackState,mapRoadState)
            return
        }
        val style = mapInstance?.style ?: return
        style.getSourceAs<GeoJsonSource>("wp-real")?.setGeoJson(realGeoJson)
        style.getSourceAs<GeoJsonSource>("wp-demo")?.setGeoJson(demoGeoJson)
        style.getSourceAs<GeoJsonSource>("wp-roads")?.setGeoJson(roadJson)
        style.getSourceAs<GeoJsonSource>("wp-painted")?.setGeoJson(paintedJson)
    }

    private fun updateLocationLayer() {
        if (amapActive) amapController.updateUser(userFix)
        else mapInstance?.style?.getSourceAs<GeoJsonSource>("wp-user")?.setGeoJson(userJson(userFix))
    }

    private fun geoJson(tracks: List<Track>): String = TrackMapJson.encode(tracks)

    private fun userJson(fix: Location?): String {
        val features = JSONArray()
        if (fix != null) {
            features.put(JSONObject().put("type", "Feature")
                .put("geometry", JSONObject().put("type", "Point")
                    .put("coordinates", JSONArray().put(fix.longitude).put(fix.latitude)))
                .put("properties", JSONObject()))
        }
        return JSONObject().put("type", "FeatureCollection").put("features", features).toString()
    }

    @Composable
    private fun AmapNativeView() {
        val controller = remember { amapController }
        val view = remember { controller.create(mapTrackState, userFix,mapRoadState) }
        pendingTripFocus?.let {
            controller.focus(it.first, it.second, it.third)
            pendingTripFocus = null
        }
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        DisposableEffect(view, lifecycle) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> view.onResume()
                    Lifecycle.Event.ON_PAUSE -> view.onPause()
                    else -> Unit
                }
            }
            lifecycle.addObserver(observer)
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) view.onResume()
            onDispose {
                lifecycle.removeObserver(observer)
                runCatching { controller.release(view) }
            }
        }
        AndroidView(factory = { view }, modifier = Modifier.fillMaxSize())
    }

    @Composable
    private fun NativeMapView() {
        val view = remember { makeMapView() }
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        DisposableEffect(view, lifecycle) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> view.onStart()
                    Lifecycle.Event.ON_RESUME -> view.onResume()
                    Lifecycle.Event.ON_PAUSE -> view.onPause()
                    Lifecycle.Event.ON_STOP -> view.onStop()
                    Lifecycle.Event.ON_DESTROY -> Unit
                    else -> Unit
                }
            }
            lifecycle.addObserver(observer)
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) view.onStart()
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) view.onResume()
            onDispose {
                lifecycle.removeObserver(observer)
                runCatching { view.onPause(); view.onStop(); view.onDestroy() }
                if (mapWidget === view) { mapWidget = null; mapInstance = null }
            }
        }
        AndroidView(factory = { view }, modifier = Modifier.fillMaxSize())
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("pendingEnable", pendingEnable)
        outState.putBoolean("pendingSettingsEnable", pendingSettingsEnable)
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        if (pendingSettingsEnable && AmbientTracking.hasFineLocation(this)) {
            pendingSettingsEnable = false
            AmbientTracking.setEnabled(this, true)
            requestNotification()
        }
        updateUi()
        beginVisibleLocation()
        if (AmbientTracking.enabled(this) && AmbientTracking.hasFineLocation(this) && !AmbientTracking.running) {
            AmbientTracking.start(this)
        }
    }
    override fun onPause() {
        locationManager?.let { runCatching { it.removeUpdates(visibleLocationListener) } }
        super.onPause()
    }
    override fun onDestroy() {
        roadDownload?.cancel()
        mainHandler.removeCallbacksAndMessages(null)
        refreshExecutor.shutdown()
        super.onDestroy()
    }
    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()
}
