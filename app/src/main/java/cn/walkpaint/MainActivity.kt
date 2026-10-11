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

class MainActivity : ComponentActivity() {
    private val repository by lazy { (application as WalkPaintApp).repository }
    private var currentTab by mutableIntStateOf(0)
    private val roadAppearance by lazy { RoadAppearance(this) }
    private var roadColor by mutableIntStateOf(ExploredRoadArgb)
    /** Name of the place the progress is measured around; null until resolved, never an error. */
    private var placeName by mutableStateOf<String?>(null)
    private var placeAnchor: TrackPoint? = null

    /**
     * Resolves the current place name, but only after moving far enough to matter. Geocoding on
     * every fix would hammer the service for a name that has not changed.
     */
    private fun refreshPlaceName(fix: Location?) {
        val last = snapshot.realTracks.lastOrNull()?.points?.lastOrNull()
        val lat = fix?.latitude ?: last?.lat ?: return
        val lon = fix?.longitude ?: last?.lon ?: return
        val anchor = TrackPoint(lat, lon, 0L, 5f)
        placeAnchor?.let { if (Geo.meters(it, anchor) < 500.0) return }
        placeAnchor = anchor
        PlaceName.resolve(this, lat, lon) { name -> runOnUiThread { if (name != null) placeName = name } }
    }
    private var showRoadAppearance by mutableStateOf(false)
    private var retainedMapCamera:org.maplibre.android.camera.CameraPosition?=null
    private var snapshot by mutableStateOf(TrackState())
    private var userFix by mutableStateOf<Location?>(null)
    private var trackingStatus by mutableStateOf("正在检查位置状态")
    private var recordingTitle by mutableStateOf("正在检查记录状态")
    private var recordingHealthy by mutableStateOf(false)
    private var mapNotice by mutableStateOf<String?>(null)
    private var dailyRoads by mutableStateOf<Map<String,RoadState>>(emptyMap())
    private val dailyRoadCache=DailyRoadCache()
    private var dailyRoadDate = ""
    private var trackingEnabled by mutableStateOf(false)
    private var trackingRunning by mutableStateOf(false)
    private var backgroundGranted by mutableStateOf(false)
    private var amapConsent by mutableStateOf(false)
    private var showAmapConsent by mutableStateOf(false)
    private val follow=MapFollowState()
    private var activityResumed=false
    private var mapHeading=0f
    private var lastCameraPoint:TrackPoint?=null
    private var lastCameraBearing=0f
    private val headingSensor by lazy { HeadingSensor(this,
        { @Suppress("DEPRECATION") windowManager.defaultDisplay.rotation },
        { bearing -> mapHeading=bearing ?: 0f;syncFollowCamera() }) }
    private val amapController by lazy { AmapMapController(this,::pauseMapFollowing,
        { syncFollowCamera(force=true) }) }
    private val amapSupported get() = AmapPlatform.supported(Build.SUPPORTED_ABIS.toList())
    private val amapActive get() = amapSupported && BuildConfig.AMAP_KEY.isNotBlank() && amapConsent
    private var showDiagnostics by mutableStateOf(false)
    private var diagnosticText by mutableStateOf("")
    private var feedbackHistory by mutableStateOf(RecordingDiagnostics.HistoryView(emptyList(),true,false))
    private var feedbackReport by mutableStateOf<String?>(null)
    private var feedbackBusy by mutableStateOf(false)
    private var privateUpdateState by mutableStateOf(PrivateUpdateState())
    private val privateUpdater by lazy { PrivateUpdater(this) { privateUpdateState=it } }
    private val currentRelease by lazy {
        CurrentRelease.decode(assets.open("release-notes.json").bufferedReader().use { it.readText() })
    }
    private var confirmEnable by mutableStateOf(false)
    private var showBackgroundPrompt by mutableStateOf(false)
    private var showFineSettings by mutableStateOf(false)
    private var backgroundDeferred = false
    private var notificationAttempted = false
    private var profileNotice by mutableStateOf<String?>(null)
    private var pendingSettingsEnable = false
    private var pendingBackgroundSettings = false
    private var recordingCode = "PAUSED"
    private val refreshExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
    private val refreshing = java.util.concurrent.atomic.AtomicBoolean(false)
    private var roadState by mutableStateOf(RoadState())
    private var mapRoadState by mutableStateOf(RoadState())
    private var mapTrackState by mutableStateOf(TrackState())
    private var selectedDay by mutableStateOf<String?>(null)
    private var roadAreaHint by mutableStateOf("")
    private var boundaryCatalog: RoadCatalog?=null
    private var boundaryRoads: List<RoadSegment>?=null
    private fun clearDaySelection() {
        selectedDay=null;pendingTripFocus=null;follow.locate();lastCameraPoint=null
        lastRevision=-1;updateUi();syncFollowCamera(force=true)
    }
    private var roadBusy by mutableStateOf(false)
    private var roadMessage by mutableStateOf("")
    private var showRoadPack by mutableStateOf(false)
    private var downloadArea by mutableStateOf<NearbyRoads.Area?>(null)
    private var roadDownload: RoadDownload? = null
    private var onlineMap by mutableStateOf(false)
    private var showOnlineConsent by mutableStateOf(false)
    private var paintedJson = RoadState().geoJson(true)
    /** Lit ground as merged strips, so the map never has to carry one shape per cell. */
    private var exploredJson = Explorer.stripsGeoJson(TrackState())
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
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            if (AmbientTracking.hasFineLocation(this)) {
                beginVisibleLocation()
                if(pendingEnable) continueEnable()
                else if(pendingCenter) centerUser()
            } else {
                RecordingDiagnostics.problem(this,ProblemCode.PERMISSION_DENIED)
                if(pendingEnable) { pendingEnable=false; showFineSettings=true }
                else toast("需要允许精确定位，才能查看当前位置")
                pendingCenter=false
            }
            updateUi()
        }

    private val backgroundPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            backgroundDeferred=!AmbientTracking.hasBackgroundLocation(this)
            if(backgroundDeferred) RecordingDiagnostics.problem(this,ProblemCode.PERMISSION_DENIED)
            if(pendingEnable) continueEnable()
        }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            if(!notificationsGranted()) RecordingDiagnostics.problem(this,ProblemCode.PERMISSION_DENIED)
            if(pendingEnable) continueEnable()
            updateUi()
        }

    private val permissionSettingsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            val waiting=pendingSettingsEnable
            pendingSettingsEnable=false
            if(waiting && pendingEnable) {
                if(AmbientTracking.hasFineLocation(this)) {
                    // Returning without "always allow" is a valid decline, never a prompt loop.
                    if(pendingBackgroundSettings) {
                        backgroundDeferred=!AmbientTracking.hasBackgroundLocation(this)
                        if(backgroundDeferred) RecordingDiagnostics.problem(this,ProblemCode.PERMISSION_DENIED)
                    }
                    pendingBackgroundSettings=false
                    continueEnable()
                } else { pendingEnable=false; toast("尚未允许精确定位，自动记录未开启") }
            }
            updateUi()
        }

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
                    RecordingDiagnostics.problem(this,ProblemCode.ROAD_IMPORT_FAILED)
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
                    .onFailure { runOnUiThread { RecordingDiagnostics.problem(this,ProblemCode.BACKUP_FAILED);toast("导出失败：请检查目标位置或空间") } }
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
                }.onFailure { runOnUiThread { RecordingDiagnostics.problem(this,ProblemCode.BACKUP_FAILED);toast("无法导入，请确认是有效的足迹备份") } }
            }.start()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        roadColor=roadAppearance.color
        pendingEnable = savedInstanceState?.getBoolean("pendingEnable") ?: false
        pendingSettingsEnable = savedInstanceState?.getBoolean("pendingSettingsEnable") ?: false
        pendingBackgroundSettings = savedInstanceState?.getBoolean("pendingBackgroundSettings") ?: false
        backgroundDeferred = savedInstanceState?.getBoolean("backgroundDeferred") ?: false
        notificationAttempted = savedInstanceState?.getBoolean("notificationAttempted") ?: false
        confirmEnable = savedInstanceState?.getBoolean("confirmEnable") ?: false
        showBackgroundPrompt = savedInstanceState?.getBoolean("showBackgroundPrompt") ?: false
        showFineSettings = savedInstanceState?.getBoolean("showFineSettings") ?: false
        MapLibre.getInstance(applicationContext)
        val mapPref = getSharedPreferences("ui_onboarding", Context.MODE_PRIVATE)
        onlineMap = mapPref.getBoolean("openfreemap_basemap_consent", false)
        amapConsent = mapPref.getBoolean("amap_sdk_consent", false)
        showAmapConsent = amapSupported && BuildConfig.AMAP_KEY.isNotBlank() &&
            !amapConsent && !mapPref.getBoolean("amap_sdk_decision_seen", false)
        updateUi()
        setContent {
            WalkPaintTheme {
                AppShell(
                    selectedTab = currentTab,
                    onTab = {
                        if(it!=2 && privateUpdateState.busy) privateUpdater.cancel()
                        if(it==0 && currentTab!=0 && selectedDay==null) { follow.locate();lastCameraPoint=null }
                        currentTab=it;syncHeadingLifecycle()
                    },
                    state = snapshot,
                    fix = userFix,
                    placeName = placeName,
                    automatic = trackingEnabled,
                    running = trackingRunning,
                    trackingStatus = trackingStatus,
                    recordingTitle = recordingTitle,
                    recordingHealthy = recordingHealthy,
                    profileNotice = profileNotice, onResolveTracking = { resolveTracking() },
                    mapNotice = mapNotice, dailyRoads = dailyRoads,
                    needsBackground = trackingEnabled && !backgroundGranted,
                    selectedDay = selectedDay,
                    mapRoadState = mapRoadState,
                    roadAreaHint = roadAreaHint,
                    onShowAll = { clearDaySelection() },
                    onLocate = { clearDaySelection(); centerUser() },
                    onToggleTracking = { requested ->
                        if (requested) beginEnable()
                        else {
                            pendingEnable=false;pendingSettingsEnable=false
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
                        follow.history();syncHeadingLifecycle()
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
                    onPowerSettings = { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) },
                    onRevoke = { AmbientTracking.stop(this); updateUi() },
                    onCopyDiagnostics = { openRecordingDiagnostics() },
                    internalBackupCount = repository.backupFiles().size,
                    mapProviderLabel = if (amapActive) "高德联网地图" else if(onlineMap) "OpenFreeMap 联网备用地图" else "离线足迹视图",
                    roadColor = roadColor,
                    onRoadAppearance = { showRoadAppearance=true },
                    onChooseAmap = {
                        if(amapSupported) showAmapConsent = true
                        else { RecordingDiagnostics.problem(this,ProblemCode.MAP_UNAVAILABLE);toast("此设备暂不支持高德地图，请使用离线地图") }
                    },
                    onOnlineMap = { showOnlineConsent = true },
                    roadState = roadState,
                    onRoadPack = { showRoadPack = true },
                    feedbackContent = { onBack -> FeedbackPage(feedbackHistory,feedbackReport,feedbackBusy,
                        onBack={feedbackReport=null;onBack()},onPreview=::prepareFeedback,
                        onClosePreview={feedbackReport=null},onCopy={copyFeedback()},onShare={shareFeedback()},
                        onCollection={RecordingDiagnostics.collectHistory(this,it)},
                        onClear={RecordingDiagnostics.clearHistory(this)}) },
                    updatesContent = { onBack -> UpdatesPage(currentRelease,privateUpdateState,
                        { if(privateUpdateState.busy) privateUpdater.cancel();onBack() },::checkUpdates,
                        { privateUpdater.authorize() },{ openUpdateBrowser(PrivateUpdatePolicy.VERIFY) },
                        { privateUpdater.download() },::installUpdate,{ privateUpdater.cancel() },
                        { privateUpdater.logout() },{ openUpdateBrowser(UpdateDestination.URL) }) },
                    mapContent = { if (amapActive) AmapNativeView() else NativeMapView() }
                )

                if(showRoadAppearance) RoadColorDialog(roadColor,::chooseRoadColor) { showRoadAppearance=false }
                if(showOnlineConsent) AlertDialog(
                    onDismissRequest={ showOnlineConsent=false },
                    title={ Text("联网显示底图？") },
                    text={ Text("OpenFreeMap 备用地图将收到当前及浏览地图区域、IP 和地图请求；区域可能反映私人位置。同意后会记住选择，今后打开地图也会联网；可随时撤回联网授权。不上传足迹文件，不需要下载道路包。拒绝仍可记录并显示已保存的足迹。") },
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
                    }) { Text("使用离线足迹视图") } }
                )

                if(showRoadPack) AlertDialog(
                    onDismissRequest = { if(!roadBusy) showRoadPack = false },
                    title = { Text("地图数据") },
                    text = { Column(Modifier.verticalScroll(rememberScrollState())) {
                        Text(roadState.summary)
                        Spacer(Modifier.height(12.dp))
                        Text("先下载附近约1.2公里范围的道路，之后离线记录和上色；走到新区域时再下载。路口、精度差或道路缺失时可能少涂。也可以导入已有道路文件。")
                        Spacer(Modifier.height(12.dp))
                        Button(enabled=!roadBusy, onClick={ prepareNearbyRoads() }, modifier=Modifier.fillMaxWidth()) {
                            Text("准备这里的地图")
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
                    }) { Text("导入地图文件") } },
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
                    title={ Text("准备这里的地图") },
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
                if (showAmapConsent && !confirmEnable && !pendingEnable && !showBackgroundPrompt && !showFineSettings) AlertDialog(
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
                            amapConsent=false; onlineMap=false
                            com.amap.api.maps.MapsInitializer.updatePrivacyAgree(applicationContext,false)
                            getSharedPreferences("ui_onboarding", Context.MODE_PRIVATE).edit()
                                .putBoolean("amap_sdk_decision_seen", true)
                                .putBoolean("amap_sdk_consent", false)
                                .putBoolean("openfreemap_basemap_consent", false).apply()
                        }) { Text(if(amapConsent) "撤回并用离线地图" else "暂不用") }
                    }
                )
                if (confirmEnable) AlertDialog(
                    onDismissRequest = { confirmEnable=false;pendingEnable=false },
                    title = { Text("开启自动记录") },
                    text = { Text("为了在出门和锁屏后记录走过的路，需要允许精确定位。足迹保存在本机，记录时会保留通知，可能增加耗电；你随时可以关闭。地图联网另行征求同意。") },
                    confirmButton = { Button(onClick={
                        confirmEnable=false
                        getSharedPreferences("ui_onboarding",Context.MODE_PRIVATE).edit()
                            .putBoolean("auto_notice_v3_seen",true).apply()
                        continueEnable()
                    }) { Text("同意并继续") } },
                    dismissButton = { TextButton(onClick={confirmEnable=false;pendingEnable=false}) { Text("暂不开启") } }
                )
                if(showBackgroundPrompt) AlertDialog(
                    onDismissRequest={showBackgroundPrompt=false;pendingEnable=false},
                    title={Text("允许后台定位")},
                    text={Text("为了锁屏后继续记录，以及在中断后尝试恢复，请在系统位置权限中选择“"+
                        (if(Build.VERSION.SDK_INT>=30) packageManager.backgroundPermissionOptionLabel else "始终允许")+
                        "”。也可以先使用已有授权；后台恢复可能受限。")},
                    confirmButton={Button(onClick={
                        showBackgroundPrompt=false
                        if(Build.VERSION.SDK_INT==29) backgroundPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                        else { pendingSettingsEnable=true;pendingBackgroundSettings=true;permissionSettingsLauncher.launch(appSettingsIntent()) }
                    }) {Text("去允许")}},
                    dismissButton={TextButton(onClick={
                        showBackgroundPrompt=false;backgroundDeferred=true;continueEnable()
                    }) {Text("先这样记录")}}
                )
                if(showFineSettings) AlertDialog(
                    onDismissRequest={showFineSettings=false;pendingEnable=false},
                    title={Text("需要允许精确定位")},
                    text={Text("目前自动记录未开启。你可以在系统位置权限中开启精确定位；不授权也能查看原有足迹。")},
                    confirmButton={Button(onClick={
                        showFineSettings=false;pendingEnable=true;pendingSettingsEnable=true;pendingBackgroundSettings=false
                        permissionSettingsLauncher.launch(appSettingsIntent())
                    }) {Text("去设置")}},
                    dismissButton={TextButton(onClick={showFineSettings=false;pendingEnable=false}) {Text("稍后")}}
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
        userFix = if(AmbientTracking.hasFineLocation(this)) chooseMapFix(listOfNotNull(AmbientTracking.lastGps, AmbientTracking.lastFix,userFix)) else null
        val latestGps = AmbientTracking.lastGps
        val recentGps = latestGps?.takeIf { it.hasAccuracy() && it.accuracy >= 1f && now - it.time in 0L..60_000L }
        val diagnostic = RecordingDiagnostics.snapshot(this)
        feedbackHistory=RecordingDiagnostics.recent(this)
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
        recordingCode=status.code
        profileNotice=TrackingSetup.notice(status.code,backgroundGranted,notificationsGranted())
        mapNotice=MapNotice.from(status,recordedAt("gps_callback_at") > 0 && recordedAt("gps_callback_at") >= recordedAt("service_start_at"))
        val diagnosis = status.title + " · " + status.hint
        val gpsLabel = when {
            recentGps != null -> "GPS ±" + recentGps.accuracy.toInt() + "米"
            latestGps != null && latestGps.accuracy < 1f -> "GPS 精度尚不可用"
            latestGps != null -> "GPS 信号已过期"
            else -> "正在搜寻 GPS"
        }
        trackingStatus = diagnosis + "\n" + gpsLabel + " · 已存 " +
            snapshot.realPointCount + " 点 · 上次实际入库 " + savedLabel
        roadAreaHint = ""
        if ((lastRevision != repository.revision || dailyRoadDate != DailyFootprints.key(System.currentTimeMillis())) && refreshing.compareAndSet(false, true)) {
            val day=selectedDay
            refreshExecutor.execute {
                try {
                    val revision = repository.revision
                    val next = repository.snapshot()
                    // Compute expensive history/geometry work away from the UI thread.
                    next.totalMeters; FootprintInsights.traveledDays(next); FootprintInsights.exploredCells(next)
                    val road = repository.roadSnapshot()
                    val roadBase = geoJson(emptyList())
                    val today = DailyFootprints.key(System.currentTimeMillis())
                    val daily = emptyMap<String,RoadState>()
                    val displayedRoad = RoadState()
                    val displayedTrack = DailyFootprints.select(next,day)
                    val painted = geoJson(displayedTrack.realTracks)
                    val real = geoJson(displayedTrack.realTracks)
                    val demo = geoJson(next.tracks.filter { it.demo })
                    // Explored ground is cumulative, so it reads the whole history rather than the
                    // selected day: the point is to see everything you have ever lit up at once.
                    val explored = Explorer.stripsGeoJson(next)
                    runOnUiThread {
                        if(selectedDay!=day) return@runOnUiThread
                        snapshot = next; lastRevision = revision
                        dailyRoads=daily; dailyRoadDate=today
                        mapTrackState=displayedTrack; mapRoadState=displayedRoad
                        realGeoJson = real; demoGeoJson = demo
                        roadState = road; roadJson = roadBase; paintedJson = painted
                        exploredJson = explored
                        updateMapLayers()
                    }
                } finally { refreshing.set(false) }
            }
        }
        updateLocationLayer()
    }

    private fun notificationsGranted() = Build.VERSION.SDK_INT < 33 ||
        checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun appSettingsIntent() = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:$packageName"))

    private fun beginEnable() {
        if(pendingEnable) return
        pendingEnable=true;backgroundDeferred=false;notificationAttempted=false
        if(!AmbientTracking.hasFineLocation(this) &&
            !getSharedPreferences("ui_onboarding",Context.MODE_PRIVATE).getBoolean("auto_notice_v3_seen",false)) {
            confirmEnable=true
        } else continueEnable()
    }

    private fun continueEnable() {
        if(!pendingEnable) return
        when(TrackingSetup.next(Build.VERSION.SDK_INT,AmbientTracking.hasFineLocation(this),
            AmbientTracking.hasBackgroundLocation(this),notificationsGranted(),backgroundDeferred,notificationAttempted)) {
            TrackingSetup.Step.LOCATION -> {
                val prefs=getSharedPreferences("ui_onboarding",Context.MODE_PRIVATE)
                if(prefs.getBoolean("fine_request_seen",false) &&
                    !shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
                    pendingEnable=false;showFineSettings=true
                } else {
                    prefs.edit().putBoolean("fine_request_seen",true).apply()
                    askLocation()
                }
            }
            TrackingSetup.Step.BACKGROUND -> showBackgroundPrompt=true
            TrackingSetup.Step.NOTIFICATIONS -> {
                notificationAttempted=true
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            TrackingSetup.Step.START -> {
                pendingEnable=false;pendingSettingsEnable=false
                AmbientTracking.setEnabled(this,true)
                AmbientTracking.start(this)
                updateUi()
            }
        }
    }

    private fun resolveTracking() {
        if(!AmbientTracking.hasFineLocation(this)) beginEnable()
        else if(!getSystemService(LocationManager::class.java).isProviderEnabled(LocationManager.GPS_PROVIDER))
            startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        else if(recordingCode in setOf("SAVE_FAILED","REGISTER_FAILED","SERVICE_STOPPED")) openRecordingDiagnostics()
        else if(!AmbientTracking.hasBackgroundLocation(this)) beginEnable()
        else if(!notificationsGranted()) openAppSettings()
        else openRecordingDiagnostics()
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
        val fix = chooseMapFix(listOfNotNull(userFix,latestSystemLocation(),AmbientTracking.lastGps))
            ?.takeIf { follow.accepts(fixQuality(it)) }
        if (fix != null) {
            updatePosition(fix, true, streetZoom = true)
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
                if(!job.isCancelled) RecordingDiagnostics.problem(this,ProblemCode.ROAD_DOWNLOAD_FAILED)
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

    private fun updatePosition(fix: Location, center: Boolean, streetZoom: Boolean = false) {
        val chosen=chooseMapFix(listOfNotNull(fix,userFix,AmbientTracking.lastGps)) ?: return
        userFix = chosen
        AmbientTracking.lastFix = chosen
        updateLocationLayer()
        refreshPlaceName(chosen)
        syncFollowCamera(force=center || firstMapCenter, streetZoom=streetZoom)
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
        val facts = DiagnosisFacts(
            enabled = AmbientTracking.enabled(this),
            running = AmbientTracking.running,
            fineLocation = AmbientTracking.hasFineLocation(this),
            backgroundLocation = AmbientTracking.hasBackgroundLocation(this),
            phoneLocationOn = gpsEnabled,
            batteryWhitelisted = power.isIgnoringBatteryOptimizations(packageName),
            callbackAgeMs = ((lastFix?.time ?: 0L).takeIf { it > 0L }
                ?: (diagnostic["gps_callback_at"] as? Number)?.toLong()?.takeIf { it > 0L })
                ?.let { (now - it).coerceAtLeast(0L) },
            savedAgeMs = lastSaved?.let { (now - it).coerceAtLeast(0L) },
            savedCount = (diagnostic["saved_count"] as? Number)?.toLong() ?: snapshot.realPointCount.toLong(),
            movingThisSession = (diagnostic["session_moving_count"] as? Number)?.toLong() ?: 0L,
            savedThisSession = (diagnostic["session_saved_count"] as? Number)?.toLong() ?: 0L,
            filteredCount = (diagnostic["filtered_count"] as? Number)?.toLong() ?: 0L,
            stationaryFiltered = (diagnostic["session_filter_stationary_count"] as? Number)?.toLong() ?: 0L,
            jumpFiltered = (diagnostic["session_filter_jump_count"] as? Number)?.toLong() ?: 0L,
            accuracyFiltered = (diagnostic["session_filter_accuracy_count"] as? Number)?.toLong() ?: 0L,
            longestGapMs = (diagnostic["max_gap_ms"] as? Number)?.toLong() ?: 0L,
            serviceStartCount = (diagnostic["start_count"] as? Number)?.toLong() ?: 0L,
            databaseError = repository.lastError,
            dozeIdle = power.isDeviceIdleMode
        )
        return Diagnosis.report(facts) + "\n\n—— 原始计数（给开发者看） ——\n" +
            rows.joinToString("\n") + "\n\n" + RecordingDiagnostics.summary(this) +
            "\n\n绘制来源：已保存的定位直接涂色。连续轨迹 ${RecordedTrail.paths(snapshot.realTracks).count { it.size>=2 }} 段；累计记录 ${snapshot.totalMeters.toLong()} 米（重复出行累计，不是去重道路里程）。" +
            "\n\n持续定位已启用，不长期持有 CPU 唤醒锁；常规诊断每30秒批量保存，异常退出最多损失末尾30秒诊断（不影响 SQLite 足迹）。vivo 等厂商需允许后台运行；强制停止后须手动打开 App，不能保证永不断录。"
    }

    private fun openRecordingDiagnostics() {
        val base=trackingDiagnostics()
        val state=snapshot; val roads=roadState
        diagnosticText=base
        showDiagnostics=true
        refreshExecutor.execute {
            val details=runCatching { RoadMatchDiagnostics.report(state,roads) }
                .getOrDefault("道路复算暂不可用，原始记录保留")
            runOnUiThread { diagnosticText=base + "\n\n旧道路数据只读诊断（不控制直接轨迹涂色）\n" + details }
        }
    }

    private fun copyTrackingDiagnostics() {
        val clip = ClipData.newPlainText("WalkPaint 定位诊断", diagnosticText)
        getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
        toast("诊断已复制，不包含经纬度和个人路线")
    }

    private fun prepareFeedback(category:String,description:String) {
        if(feedbackBusy) return
        feedbackBusy=true
        val application=applicationContext
        val enabled=AmbientTracking.enabled(this);val running=AmbientTracking.running
        val fine=AmbientTracking.hasFineLocation(this);val background=AmbientTracking.hasBackgroundLocation(this)
        val notifications=notificationsGranted()
        refreshExecutor.execute {
            val text=runCatching {
                val state=repository.snapshot();val roads=repository.roadSnapshot()
                val counts=RoadMatchDiagnostics.counts(state,roads);val recent=RecordingDiagnostics.recent(application)
                FeedbackReport.build(FeedbackFacts(BuildConfig.VERSION_NAME,BuildConfig.VERSION_CODE,Build.VERSION.SDK_INT,
                    Build.BRAND,Build.MODEL,enabled,running,fine,background,notifications,
                    state.realPointCount,roads.roads.size,roads.matched,roads.unmapped,roads.meters.toLong(),
                    counts.confirmable,counts.noCandidates,counts.unavailable,RecordingDiagnostics.snapshot(application),
                    recent.entries,recent.enabled,recent.saveFailed,true,
                    RecordedTrail.paths(state.realTracks).count { it.size>=2 },state.totalMeters.toLong()),category,description,System.currentTimeMillis())
            }.getOrElse { "报告暂时无法整理，足迹保留。请稍后重试。" }
            runOnUiThread { feedbackBusy=false;feedbackReport=text }
        }
    }
    private fun copyFeedback() {
        feedbackReport?.let {
            getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("足迹涂色反馈",it))
            toast("报告已复制")
        }
    }
    private fun shareFeedback() {
        val text=feedbackReport ?: return
        val intent=Intent(Intent.ACTION_SEND).apply { type="text/plain";putExtra(Intent.EXTRA_TEXT,text) }
        try { startActivity(Intent.createChooser(intent,"分享反馈报告")) }
        catch(_:android.content.ActivityNotFoundException) { toast("没有可用分享应用，可复制报告") }
    }
    private fun checkUpdates() {
        val connectivity=getSystemService(android.net.ConnectivityManager::class.java)
        val capabilities=connectivity.getNetworkCapabilities(connectivity.activeNetwork)
        val available=capabilities?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)==true &&
            capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        if(!available) { privateUpdater.notice(UpdateDestination.OFFLINE);return }
        privateUpdateState=privateUpdater.snapshot();privateUpdater.check()
    }
    private fun openUpdateBrowser(url:String) {
        try { startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)) }
        catch(_:android.content.ActivityNotFoundException) { privateUpdater.notice(UpdateDestination.NO_BROWSER) }
    }
    private fun installUpdate() {
        privateUpdater.prepareInstall { file ->
            try {
                if(!packageManager.canRequestPackageInstalls()) {
                    privateUpdater.notice("请允许足迹涂色安装更新，返回后再点击安装；拒绝不会影响原版本")
                    startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:$packageName")))
                } else {
                    val uri=androidx.core.content.FileProvider.getUriForFile(this,"$packageName.updates",file)
                    startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                    privateUpdater.notice("请在系统窗口确认覆盖安装；取消后仍可使用原版本")
                }
            } catch(_:Exception) { privateUpdater.notice("暂时无法打开系统安装，请重新下载后重试") }
        }
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
        view.addOnDidFailLoadingMapListener { RecordingDiagnostics.problem(this,ProblemCode.MAP_UNAVAILABLE) }
        mapWidget = view
        view.getMapAsync { loaded ->
            if (mapWidget !== view) return@getMapAsync
            mapInstance = loaded
            loaded.addOnCameraMoveStartedListener { reason ->
                if(reason==MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) pauseMapFollowing()
            }
            val builder = if(onlineMap) Style.Builder().fromUri("https://tiles.openfreemap.org/styles/liberty")
                else Style.Builder().fromJson("""{"version":8,"sources":{},"layers":[{"id":"local-background","type":"background","paint":{"background-color":"#F4F7F5"}}]}""")
            loaded.setStyle(builder) { style ->
                if (mapWidget !== view) return@setStyle
                style.addSource(GeoJsonSource("wp-user", userJson(userFix)))
                style.addSource(GeoJsonSource("wp-roads", roadJson))
                style.addSource(GeoJsonSource("wp-painted", paintedJson))
                style.addSource(GeoJsonSource("wp-explored", exploredJson))
                // Same colour as the trail line below, so the ground and the line are one mark on
                // both renderers; the AMap path already follows the chosen colour.
                style.addLayer(org.maplibre.android.style.layers.FillLayer("wp-explored-fill","wp-explored")
                    .withProperties(
                        PropertyFactory.fillColor(roadColor),
                        PropertyFactory.fillOpacity(Explorer.EXPLORED_FILL_ALPHA)))
                style.addLayer(LineLayer("wp-road-base","wp-roads").withProperties(
                    PropertyFactory.lineColor(UnexploredRoadArgb),
                    PropertyFactory.lineWidth(3f),PropertyFactory.lineOpacity(0.85f)))
                style.addLayer(LineLayer("wp-painted-outline","wp-painted").withProperties(
                    PropertyFactory.lineColor(TrailOutlineArgb),PropertyFactory.lineWidth(9f),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)))
                style.addLayer(LineLayer("wp-painted-line","wp-painted").withProperties(
                    PropertyFactory.lineColor(roadColor),PropertyFactory.lineWidth(6f),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)))
                style.addLayer(CircleLayer("wp-trail-seed","wp-painted").withProperties(
                    PropertyFactory.circleColor(roadColor),PropertyFactory.circleRadius(4f),
                    PropertyFactory.circleStrokeColor(TrailOutlineArgb),PropertyFactory.circleStrokeWidth(1.5f)))
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
                } else if(retainedMapCamera!=null) {
                    loaded.moveCamera(CameraUpdateFactory.newCameraPosition(retainedMapCamera!!))
                    if(fix!=null && follow.mode==MapFollowState.Mode.FOLLOWING) updatePosition(fix,true)
                    else firstMapCenter=false
                } else if (fix != null && follow.mode==MapFollowState.Mode.FOLLOWING) {
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

    private fun chooseRoadColor(color:Int) {
        roadAppearance.save(color)
        roadColor=color
        amapController.setExploredColor(color)
        mapInstance?.style?.getLayerAs<LineLayer>("wp-painted-line")
            ?.setProperties(PropertyFactory.lineColor(color))
        mapInstance?.style?.getLayerAs<CircleLayer>("wp-trail-seed")
            ?.setProperties(PropertyFactory.circleColor(color))
    }

    private fun updateMapLayers() {
        if (amapActive) {
            amapController.render(mapTrackState,mapRoadState)
            return
        }
        val style = mapInstance?.style ?: return
        style.getSourceAs<GeoJsonSource>("wp-roads")?.setGeoJson(roadJson)
        style.getSourceAs<GeoJsonSource>("wp-painted")?.setGeoJson(paintedJson)
        style.getSourceAs<GeoJsonSource>("wp-explored")?.setGeoJson(exploredJson)
    }

    private fun updateLocationLayer() {
        if (amapActive) amapController.updateUser(userFix)
        else mapInstance?.style?.getSourceAs<GeoJsonSource>("wp-user")?.setGeoJson(userJson(userFix))
        syncHeadingLifecycle()
        syncFollowCamera()
    }

    private fun fixQuality(fix:Location)=MapFixQuality(fix.provider,
        (android.os.SystemClock.elapsedRealtimeNanos()-fix.elapsedRealtimeNanos)/1_000_000L,fix.accuracy)

    private fun syncHeadingLifecycle() {
        follow.visible=activityResumed && currentTab==0
        headingSensor.setActive(follow.following && userFix?.let { follow.accepts(fixQuality(it)) }==true)
    }

    private fun pauseMapFollowing() {
        follow.gesture();amapController.cancelFollowingAnimation();mapInstance?.cancelTransitions()
        syncHeadingLifecycle()
    }

    private fun syncFollowCamera(force:Boolean=false, streetZoom:Boolean=false) {
        if(!follow.following) return
        val fix=userFix?.takeIf { follow.accepts(fixQuality(it)) } ?: return
        val point=TrackPoint(fix.latitude,fix.longitude,fix.elapsedRealtimeNanos/1_000_000L,fix.accuracy)
        if(!force && !CameraFixPolicy.plausible(lastCameraPoint,point)) return
        if(!force && lastCameraPoint?.let { Geo.meters(it,point)<1.0 }==true &&
            kotlin.math.abs(HeadingFilter.delta(lastCameraBearing,mapHeading))<1f) return
        val animate=Settings.Global.getFloat(contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)>0f
        // Only an explicit "where am I" lifts a wide camera to street level.
        val minZoom=if(streetZoom) StreetZoom else null
        if(amapActive) {
            if(amapController.view()==null) return
            amapController.followCamera(fix,mapHeading,animate,minZoom)
        } else {
            val map=mapInstance ?: return
            val camera=map.cameraPosition
            val zoom=minZoom?.let { maxOf(camera.zoom,it.toDouble()) }
                ?: if(camera.zoom<5.0) 18.0 else camera.zoom
            val target=org.maplibre.android.camera.CameraPosition.Builder(camera)
                .target(LatLng(fix.latitude,fix.longitude)).bearing(mapHeading.toDouble())
                .zoom(zoom).build()
            val update=CameraUpdateFactory.newCameraPosition(target)
            if(animate) map.easeCamera(update,200) else map.moveCamera(update)
        }
        lastCameraPoint=point;lastCameraBearing=mapHeading;firstMapCenter=false
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
        val view = remember { controller.create(mapTrackState, userFix,mapRoadState,roadColor) }
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
                headingSensor.stop()
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
                headingSensor.stop()
                retainedMapCamera=mapInstance?.cameraPosition
                runCatching { view.onPause(); view.onStop(); view.onDestroy() }
                if (mapWidget === view) { mapWidget = null; mapInstance = null }
            }
        }
        AndroidView(factory = { view }, modifier = Modifier.fillMaxSize())
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("pendingEnable", pendingEnable)
        outState.putBoolean("pendingSettingsEnable", pendingSettingsEnable)
        outState.putBoolean("pendingBackgroundSettings",pendingBackgroundSettings)
        outState.putBoolean("backgroundDeferred",backgroundDeferred)
        outState.putBoolean("notificationAttempted",notificationAttempted)
        outState.putBoolean("confirmEnable",confirmEnable)
        outState.putBoolean("showBackgroundPrompt",showBackgroundPrompt)
        outState.putBoolean("showFineSettings",showFineSettings)
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        activityResumed=true;syncHeadingLifecycle()
        updateUi()
        beginVisibleLocation()
        if (AmbientTracking.enabled(this) && AmbientTracking.hasFineLocation(this) && !AmbientTracking.running) {
            AmbientTracking.start(this)
        }
    }
    override fun onPause() {
        activityResumed=false;syncHeadingLifecycle()
        locationManager?.let { runCatching { it.removeUpdates(visibleLocationListener) } }
        super.onPause()
    }
    override fun onDestroy() {
        privateUpdater.close()
        headingSensor.stop()
        roadDownload?.cancel()
        mainHandler.removeCallbacksAndMessages(null)
        refreshExecutor.shutdown()
        super.onDestroy()
    }
    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()
}
