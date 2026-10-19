package cn.walkpaint

import android.Manifest
import android.annotation.SuppressLint
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
import android.os.SystemClock
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
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * How often the veil may be redrawn while the map is moving. The frame carries a margin, so the
 * fog still covers the screen between redraws; this only keeps a long drag from redrawing the
 * whole history on every frame.
 */
private const val VEIL_REFRESH_MS = 120L
/** Shortest gap between two redraws once the map has settled; a redraw that lands inside it is
 * coalesced into one shortly afterwards rather than dropped, so the last view is always drawn. */
private const val VEIL_REFRESH_SETTLE_MS = 100L

/**
 * A viewport wider than this is not a view of the map; it is the engine before it is ready. It was
 * five degrees, which also threw away a legitimately wide zoom-out; sixty still threw away a real
 * wide zoom-out as well, and rejecting a view the person is actually looking at is worse than
 * framing it: the veil then falls back to a person-centred frame that may not cover the screen, and
 * the basemap shows around its edge. So the limit is now only wide enough to catch the degenerate
 * whole-world region the engines report at launch, and the frame follows whatever real view it gets.
 * A frame too coarse to draw the corridor no longer widens it (see TrailPaint.corridorVisible), so a
 * wide view is safe to accept: it stays fully veiled and the walked route is drawn as a line.
 */
private const val MAX_SANE_VIEW_DEGREES = 170.0

/**
 * Shortest gap between two handovers of the veil image to the map.
 *
 * The handover has to rebuild the image the map reads, which costs a few tens of milliseconds on
 * the UI thread.
 * A walking fix arrives every couple of seconds, so coalescing means at most one rebuild per gap
 * while the picture on screen lags a walker by well under a metre.
 */
private const val VEIL_HANDOVER_MS = 600L

class MainActivity : ComponentActivity() {
    private val repository by lazy { (application as WalkPaintApp).repository }
    private var currentTab by mutableIntStateOf(0)
    private var appearanceMode by mutableIntStateOf(0)
    private var visualSkin by mutableStateOf(VisualSkin.CITY)
    private var showSkinPicker by mutableStateOf(false)
    private var showIntro by mutableStateOf(false)
    private val roadAppearance by lazy { RoadAppearance(this) }
    private var roadColor by mutableIntStateOf(ExploredRoadArgb)
    /** Name of the place the progress is measured around; null until resolved, never an error. */
    private var placeName by mutableStateOf<String?>(null)
    private var placeAnchor: TrackPoint? = null
    private var placeRequestRevision = 0

    /**
     * Resolves the current place name, but only after moving far enough to matter. Geocoding on
     * every fix would hammer the service for a name that has not changed.
     */
    private fun refreshPlaceName(fix: Location?) {
        if (!amapConsent || BuildConfig.AMAP_KEY.isBlank()) {
            placeRequestRevision++
            placeAnchor = null
            placeName = null
            return
        }
        val last = snapshot.realTracks.lastOrNull()?.points?.lastOrNull()
        val lat = fix?.latitude ?: last?.lat ?: return
        val lon = fix?.longitude ?: last?.lon ?: return
        val anchor = TrackPoint(lat, lon, 0L, 5f)
        placeAnchor?.let { if (Geo.meters(it, anchor) < 500.0) return }
        placeAnchor = anchor
        val requestRevision = ++placeRequestRevision
        PlaceName.resolve(this, lat, lon) { name -> runOnUiThread {
            if (amapConsent && requestRevision == placeRequestRevision && placeAnchor == anchor && name != null) placeName = name
        } }
    }
    private var showRoadAppearance by mutableStateOf(false)
    private var snapshot by mutableStateOf(TrackState())
    private var backupCount by mutableStateOf(0)
    private var backupStatus by mutableStateOf<String?>(null)
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
    /** First-run map disclosure, shown as a full screen so it cannot be dismissed by accident. */
    private var showMapConsent by mutableStateOf(false)
    private val follow=MapFollowState()
    private var activityResumed=false
    private val headingContinuity=HeadingContinuity()
    private var mapHeading=0f
    private var mapViewReady by mutableStateOf(false)
    private var lastCameraPoint:TrackPoint?=null
    private var lastCameraBearing=0f
    private val locateZoom=LocateZoomRequest()
    private val headingSensor by lazy { HeadingSensor(this,
        { @Suppress("DEPRECATION") windowManager.defaultDisplay.rotation },
        { bearing -> mapHeading=headingContinuity.resolve(bearing);syncFollowCamera() }) }
    private val amapController by lazy { AmapMapController(this,::pauseMapFollowing,
        { mapViewReady=true;refreshVeilForViewport(force=true);syncFollowCamera(force=true) }, ::onMapCameraMoved, ::onMapCameraSettled) }
    private val amapSupported get() = AmapPlatform.supported(Build.SUPPORTED_ABIS.toList(), BuildConfig.ARM64_ONLY)
    private val amapActive get() = amapSupported && BuildConfig.AMAP_KEY.isNotBlank() && amapConsent
    private var showDiagnostics by mutableStateOf(false)
    private var diagnosticText by mutableStateOf("")
    private var diagnosticFacts by mutableStateOf<DiagnosisFacts?>(null)
    private var shortGpsText by mutableStateOf("")
    private var shortGpsActive by mutableStateOf(false)
    private var shortProbe:FreshGpsProbe?=null
    private var shortGnss:android.location.GnssStatus.Callback?=null
    private val finishShortGps=Runnable { stopShortGps("60秒短测完成") }
    private var feedbackHistory by mutableStateOf(RecordingDiagnostics.HistoryView(emptyList(),true,false))
    private var feedbackReport by mutableStateOf<String?>(null)
    private var feedbackBusy by mutableStateOf(false)
    /** Recognized only by the separate developer debug build. */
    private val EXTRA_EXPORT_FIELD_REPORT = "cn.walkpaint.extra.EXPORT_FIELD_REPORT"
    private var privateUpdateState by mutableStateOf(PrivateUpdateState())
    private val privateUpdater by lazy { PrivateUpdater(this) { privateUpdateState=it } }
    private val currentRelease by lazy {
        CurrentRelease.decode(assets.open("release-notes.json").bufferedReader().use { it.readText() })
    }
    private var confirmEnable by mutableStateOf(false)
    private var showBackgroundPrompt by mutableStateOf(false)
    private var showFineSettings by mutableStateOf(false)
    private var backgroundDeferred = false
    private var profileNotice by mutableStateOf<String?>(null)
    private var pendingSettingsEnable = false
    private var pendingBackgroundSettings = false
    private var recordingCode = "PAUSED"
    private val refreshExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
    private val refreshing = java.util.concurrent.atomic.AtomicBoolean(false)
    private var roadState by mutableStateOf(RoadState())
    private var mapRoadState by mutableStateOf(RoadState())
    private var mapTrackState by mutableStateOf(TrackState())
    /**
     * The line drawn for one selected day, and nothing at all for the default view: there the veil
     * paints the ground, which is both the point of the app and the only way a long history cannot
     * pile up as a braid of overlapping passes.
     */
    private var mapTrailPaths: List<List<TrackPoint>> = emptyList()
    // 回放节奏：40 毫秒一帧（约 25fps），每帧推进 0.8%，一整天大约 5 秒走完。
    private val REPLAY_FRAME_MS = 40L
    private val REPLAY_STEP = 0.008f
    /**
     * 历史回放：把选中那天的原始轨迹按时间切成一段段，播放时只画到进度位置。
     * 回放不改数据库、不改当天选择，只替换喂给地图的折线，退出即恢复原状。
     */
    private var replayTrips: List<List<TrackPoint>> = emptyList()
    private var replayPaths: List<List<TrackPoint>> = emptyList()
    private var replayActive by mutableStateOf(false)
    private var replayPlaying by mutableStateOf(false)
    private var replayProgress by mutableStateOf(0f)
    private val replayTick = object : Runnable {
        override fun run() {
            if (!replayActive || !replayPlaying) return
            val next = replayProgress + REPLAY_STEP
            applyReplayProgress(next)
            if (next < 1f) mainHandler.postDelayed(this, REPLAY_FRAME_MS) else replayPlaying = false
        }
    }
    private var selectedDay by mutableStateOf<String?>(null)
    private var roadAreaHint by mutableStateOf("")
    private var boundaryCatalog: RoadCatalog?=null
    private var boundaryRoads: List<RoadSegment>?=null
    private fun clearDaySelection(showExtent: Boolean = true) {
        stopReplay()
        mapSceneGeneration++;pendingVeil=null;fogPaths=emptyList()
        selectedDay=null;pendingTripFocus=null;follow.locate();lastCameraPoint=null
        lastRevision=-1;updateUi()
        // "Back to all exploration" shows what has actually been explored. Following the person at
        // street zoom cannot do that once the history is bigger than a few streets: ground walked
        // days ago is kilometres away, and dragging there by hand at street zoom is not something a
        // person can do. The person's own position is one tap away with 定位我.
        if (!showExtent || !showExploredExtent()) syncFollowCamera(force=true)
    }

    /**
     * 开始回放选中那天的足迹。轨迹按行程、再按时间排序，所以画面上的线一定沿着当时的走法长出来，
     * 不会因为数据库里的 id 顺序把两段路连成一条直线。回放期间不写任何表。
     */
    private fun startReplay() {
        val day = selectedDay ?: return
        val trips = DailyFootprints.select(snapshot, day).realTracks
            .sortedBy { it.started }
            .map { track -> track.points.sortedBy { it.time } }
            .filter { it.size >= 2 }
        if (trips.isEmpty()) { toast("这一天还没有可以回放的轨迹"); return }
        pauseMapFollowing()
        replayTrips = trips
        replayActive = true
        replayPlaying = true
        replayProgress = 0f
        applyReplayProgress(0f)
        mainHandler.removeCallbacks(replayTick)
        mainHandler.postDelayed(replayTick, REPLAY_FRAME_MS)
    }

    private fun toggleReplay() {
        if (!replayActive) return
        if (replayPlaying) { replayPlaying = false; mainHandler.removeCallbacks(replayTick); return }
        if (replayProgress >= 1f) applyReplayProgress(0f)
        replayPlaying = true
        mainHandler.removeCallbacks(replayTick)
        mainHandler.postDelayed(replayTick, REPLAY_FRAME_MS)
    }

    private fun stopReplay() {
        mainHandler.removeCallbacks(replayTick)
        if (replayActive || replayPaths.isNotEmpty()) {
            replayActive = false; replayPlaying = false
            replayTrips = emptyList(); replayPaths = emptyList(); replayProgress = 0f
            updateMapLayers()
        }
    }

    /** 进度按“一共多少个点”折算，长走和短走都走完同样的时间，但线永远画到该到的位置。 */
    private fun applyReplayProgress(raw: Float) {
        val progress = raw.coerceIn(0f, 1f)
        replayProgress = progress
        replayPaths = replaySlices(replayTrips, progress)
        updateMapLayers()
    }

    /**
     * Fits the camera to the ground that has been explored, when that is more than a couple of
     * streets. Returns false when the history is small enough that following the person is the
     * more useful view.
     */
    private fun showExploredExtent(): Boolean {
        val points = snapshot.realTracks.flatMap { it.points }
        if (points.size < 2) return false
        val latMin = points.minOf { it.lat }
        val latMax = points.maxOf { it.lat }
        val lonMin = points.minOf { it.lon }
        val lonMax = points.maxOf { it.lon }
        val metres = maxOf(
            (latMax - latMin) * 111194.9,
            (lonMax - lonMin) * 111194.9 * kotlin.math.cos(Math.toRadians((latMin + latMax) / 2)))
        if (!metres.isFinite() || metres < 800.0) return false
        follow.gesture()
        if (amapActive) amapController.focusBounds(latMin, lonMin, latMax, lonMax)
        return true
    }
    private var roadBusy by mutableStateOf(false)
    private var roadMessage by mutableStateOf("")
    private var showRoadPack by mutableStateOf(false)
    private var downloadArea by mutableStateOf<NearbyRoads.Area?>(null)
    private var roadDownload: RoadDownload? = null
    /**
     * The veil, one image shared by both renderers. Painting it is real work over the whole
     * history, so it happens away from the UI thread and only reports a new [TrailPainter.revision]
     * when the ground under the person actually changed.
     */
    private val fogPainter = TrailPainter()
    private var fogBitmap: android.graphics.Bitmap? = null
    private var fogFrame: PaintFrame? = null
    private var fogRevision = -1
    /** The whole-history geometry the veil is drawn from, reused when only the view moves. */
    private var fogPaths: List<List<TrackPoint>> = emptyList()
    private var mapSceneGeneration=0L
    /** What the map is showing, so the veil follows it instead of following the person. */
    private var currentViewport: ViewBounds? = null
    private var lastVeilRefresh = 0L
    private var veilRefreshPending = false
    private var lastVeilHandover = 0L
    private var veilHandoverPending = false
    private var pendingVeil: Triple<android.graphics.Bitmap?, PaintFrame?, Int>? = null
    private var pendingEnable by mutableStateOf(false)
    private var pendingCenter by mutableStateOf(false)
    private var lastRevision = -1L

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
            if(!activityResumed) return
            updateUi()
            backupCount=repository.backupCountCached
            backupStatus=repository.backupStatus
            shortGpsText=ShortGpsSurvey.report();shortGpsActive=ShortGpsSurvey.active()
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
                    val output=contentResolver.openOutputStream(uri) ?: error("无法写入目标文件")
                    output.bufferedWriter(Charsets.UTF_8).use {repository.exportBackup(it)}
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
                }.onSuccess { result ->
                    runOnUiThread {
                        updateUi()
                        toast(result.message())
                    }
                }.onFailure { runOnUiThread { RecordingDiagnostics.problem(this,ProblemCode.BACKUP_FAILED);toast("无法导入，请确认是有效的足迹备份") } }
            }.start()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Local-only snapshot: legacy authorization cleanup must be reachable before any network request.
        privateUpdateState=privateUpdater.snapshot()
        roadColor=roadAppearance.color
        pendingEnable = savedInstanceState?.getBoolean("pendingEnable") ?: false
        pendingSettingsEnable = savedInstanceState?.getBoolean("pendingSettingsEnable") ?: false
        pendingBackgroundSettings = savedInstanceState?.getBoolean("pendingBackgroundSettings") ?: false
        backgroundDeferred = savedInstanceState?.getBoolean("backgroundDeferred") ?: false
        confirmEnable = savedInstanceState?.getBoolean("confirmEnable") ?: false
        showBackgroundPrompt = savedInstanceState?.getBoolean("showBackgroundPrompt") ?: false
        showFineSettings = savedInstanceState?.getBoolean("showFineSettings") ?: false
        val mapPref = getSharedPreferences("ui_onboarding", Context.MODE_PRIVATE)
        amapConsent = mapPref.getBoolean("amap_sdk_consent", false)
        // The first map decision is asked on a dedicated screen after the intro. It used to be a
        // dismissible dialog, and tapping outside it (or pressing back) recorded the decision
        // without any map, leaving a first-time user in front of an empty map area for good.
        showMapConsent = amapSupported && BuildConfig.AMAP_KEY.isNotBlank() &&
            !amapConsent && !mapPref.getBoolean("amap_sdk_decision_seen", false)
        updateUi()
        appearanceMode=getSharedPreferences("ui_appearance",Context.MODE_PRIVATE).getInt("mode",0).coerceIn(0,2)
        visualSkin=VisualSkin.stored(getSharedPreferences("ui_appearance",Context.MODE_PRIVATE).getString("skin",null))
        showIntro=!trackingEnabled && !mapPref.getBoolean("mobile_intro_seen",false)
        setContent {
            WalkPaintTheme(appearanceMode,currentTab==0,visualSkin) {
                AppShell(
                    selectedTab = currentTab,
                    onTab = {
                        if(it!=2 && privateUpdateState.busy) privateUpdater.cancel()
                        currentTab=it;syncHeadingLifecycle()
                        // The veil is opaque now, so entering the map tab without one shows a map the
                        // person has not explored. The camera may not move at all on this switch, so
                        // nothing else would ask for a redraw.
                        if(it==0) refreshVeilForViewport(force=true)
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
                    onLocate = {
                        clearDaySelection(showExtent=false)
                        centerUser()
                    },
                    onZoom = { delta ->
                        pauseMapFollowing()
                        if(amapActive) amapController.zoomBy(delta)
                    },
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
                        if(replayActive) stopReplay()
                        follow.history();syncHeadingLifecycle()
                        mapSceneGeneration++;pendingVeil=null;fogPaths=emptyList()
                        selectedDay=DailyFootprints.key(trip.started)
                        mapTrackState=DailyFootprints.select(snapshot,selectedDay)
                        updateLocationLayer()
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
                    internalBackupCount = backupCount,
                    backupStatus=backupStatus,
                    mapProviderLabel = if (amapActive) "高德联网地图" else "高德地图 · 待启用",
                    appearanceMode=appearanceMode,
                    onAppearanceMode={ mode ->
                        appearanceMode=mode
                        getSharedPreferences("ui_appearance",Context.MODE_PRIVATE).edit().putInt("mode",mode).apply()
                    },
                    onShowIntro={showIntro=true},
                    roadColor = roadColor,
                    onRoadAppearance = { showRoadAppearance=true },
                    onChooseAmap = {
                        if(amapSupported) showAmapConsent = true
                        else { RecordingDiagnostics.problem(this,ProblemCode.MAP_UNAVAILABLE);toast("此设备暂不支持当前高德地图版本，足迹记录仍会保留") }
                    },
                    roadState = roadState,
                    onRoadPack = { showRoadPack = true },
                    feedbackContent = { onBack -> FeedbackPage(feedbackHistory,feedbackReport,feedbackBusy,
                        onBack={feedbackReport=null;onBack()},onPreview=::prepareFeedback,
                        onClosePreview={feedbackReport=null},onCopy={copyFeedback()},onShare={shareFeedback()},
                        onCollection={RecordingDiagnostics.collectHistory(this,it)},
                        onClear={RecordingDiagnostics.clearHistory(this)},gpsSurvey=shortGpsText,
                        gpsSurveyActive=shortGpsActive,onGpsSurvey=::startShortGps) },
                    updatesContent = { onBack -> UpdatesPage(currentRelease,privateUpdateState,
                        { if(privateUpdateState.busy) privateUpdater.cancel();onBack() },::checkUpdates,
                        { privateUpdater.download() },::installUpdate,{ privateUpdater.cancel() },
                        { privateUpdater.logout() },{ openUpdateBrowser(UpdateDestination.URL) },
                        { openUpdateBrowser("https://github.com/h516df/walkpaint#隐私与许可") }) },
                    mapContent = { if (amapActive) {
                        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
                            AmapNativeView()
                            if(selectedDay==null && snapshot.realTracks.all {it.points.isEmpty()}) {
                                InitialExplorationHint(mapViewReady,automatic=trackingEnabled,
                                    hasFix=userFix?.let {follow.accepts(fixQuality(it))}==true,
                                    onLocate={clearDaySelection(showExtent=false);centerUser()},
                                    onStart={beginEnable()})
                            }
                        }
                    } else {
                        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize(),contentAlignment=androidx.compose.ui.Alignment.Center) {
                            Column(Modifier.padding(32.dp),horizontalAlignment=androidx.compose.ui.Alignment.CenterHorizontally) {
                                Text("地图还没打开",color=MaterialTheme.colorScheme.onSurface,fontSize=24.sp)
                                Text(if(!amapSupported) "此设备暂不支持当前地图版本，已有足迹仍保留。" else if(BuildConfig.AMAP_KEY.isBlank()) "地图配置暂不可用，请检查更新。" else "开启后会显示道路底图和地名。足迹照常记录，无需注册或填写密钥。",color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(vertical=16.dp))
                                if(amapSupported && BuildConfig.AMAP_KEY.isNotBlank()) Button(onClick={showAmapConsent=true}){Text("开启高德地图")}
                            }
                        }
                    } },
                    replay = ReplayControls(replayActive,replayPlaying,{replayProgress},
                        ::startReplay,::toggleReplay,::stopReplay),
                    onSkin={showSkinPicker=true},
                    onNotifications=::openRecordingNotificationSettings,
                    onBackupEntry={ refreshExecutor.execute {
                        val count=repository.refreshBackupCount()
                        runOnUiThread { backupCount=count }
                    } }
                )

                if(showSkinPicker) SkinPicker(visualSkin,{ skin ->
                    visualSkin=skin
                    appearanceMode=if(skin==VisualSkin.NIGHT) 2 else 0
                    getSharedPreferences("ui_appearance",Context.MODE_PRIVATE).edit()
                        .putString("skin",skin.id).putInt("mode",appearanceMode).apply()
                    chooseRoadColor(skin.paint)
                    showSkinPicker=false
                },{showSkinPicker=false})
                if(showRoadAppearance) RoadColorDialog(roadColor,::chooseRoadColor) { showRoadAppearance=false }
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

                if (showDiagnostics) RecordingDiagnosisSheet(diagnosticFacts,diagnosticText,
                    { copyTrackingDiagnostics() },{ showDiagnostics=false })
                // Reachable from settings and from the "地图还没打开" screen. Not gated on the intro,
                // so the explicit "开启高德地图" button always works.
                if (showAmapConsent && !showMapConsent && !confirmEnable && !pendingEnable && !showBackgroundPrompt && !showFineSettings) AlertDialog(
                    onDismissRequest = {
                        showAmapConsent = false
                        getSharedPreferences("ui_onboarding", Context.MODE_PRIVATE).edit()
                            .putBoolean("amap_sdk_decision_seen", true).apply()
                    },
                    title = { Text("是否使用高德地图？", fontWeight = FontWeight.Bold) },
                    text = { Column {
                        Text("高德开放平台地图与搜索 SDK 由北京高德图强科技有限公司提供，用于地图展示和地名查询；" +
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
                            placeAnchor = null
                            refreshPlaceName(userFix)
                            showAmapConsent = false
                        }) { Text("同意使用高德") }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            showAmapConsent = false
                            amapConsent=false
                            placeRequestRevision++
                            placeAnchor=null; placeName=null
                            com.amap.api.maps.MapsInitializer.updatePrivacyAgree(applicationContext,false)
                            com.amap.api.services.core.ServiceSettings.updatePrivacyAgree(applicationContext,false)
                            getSharedPreferences("ui_onboarding", Context.MODE_PRIVATE).edit()
                                .putBoolean("amap_sdk_decision_seen", true)
                                .putBoolean("amap_sdk_consent", false).apply()
                        }) { Text(if(amapConsent) "撤回地图授权" else "暂不用") }
                    }
                )
                if (showMapConsent && !showIntro) DesignMapConsent(
                    onAgree = {
                        getSharedPreferences("ui_onboarding", Context.MODE_PRIVATE).edit()
                            .putBoolean("amap_sdk_consent", true)
                            .putBoolean("amap_sdk_decision_seen", true).apply()
                        amapConsent = true
                        showMapConsent = false
                        placeAnchor = null
                        refreshPlaceName(userFix)
                    },
                    onDecline = {
                        getSharedPreferences("ui_onboarding", Context.MODE_PRIVATE).edit()
                            .putBoolean("amap_sdk_consent", false)
                            .putBoolean("amap_sdk_decision_seen", true).apply()
                        amapConsent = false
                        placeRequestRevision++
                        placeAnchor = null; placeName = null
                        com.amap.api.maps.MapsInitializer.updatePrivacyAgree(applicationContext, false)
                        com.amap.api.services.core.ServiceSettings.updatePrivacyAgree(applicationContext, false)
                        showMapConsent = false
                    },
                    onOpenPolicy = { startActivity(Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://lbs.amap.com/home/privacy/"))) }
                )
                if (showIntro || confirmEnable) DesignOnboarding(
                    onEnable={
                        showIntro=false;confirmEnable=false
                        getSharedPreferences("ui_onboarding",Context.MODE_PRIVATE).edit()
                            .putBoolean("mobile_intro_seen",true).putBoolean("auto_notice_v3_seen",true).apply()
                        if(pendingEnable) continueEnable() else beginEnable()
                    },
                    onSkip={
                        showIntro=false;confirmEnable=false;pendingEnable=false
                        getSharedPreferences("ui_onboarding",Context.MODE_PRIVATE).edit().putBoolean("mobile_intro_seen",true).apply()
                    })
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
        handleAutomaticFieldReport(intent)
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
        profileNotice=TrackingSetup.notice(status.code,backgroundGranted)
        mapNotice=MapNotice.from(status,recordedAt("gps_callback_at") > 0 && recordedAt("gps_callback_at") >= recordedAt("service_start_at"),snapshot.realPointCount>0)
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
        // Keep the veil's idea of "what the map shows" current, whatever moved it.
        currentViewport = mapViewport()
        if ((lastRevision != repository.revision || dailyRoadDate != DailyFootprints.key(System.currentTimeMillis())) && refreshing.compareAndSet(false, true)) {
            val day=selectedDay
            val generation=mapSceneGeneration
            // Read on the UI thread: the worker must not race a fix arriving while it paints.
            val fogColour = roadColor
            val fogAnchor = userFix?.let { TrackPoint(it.latitude, it.longitude, it.time, it.accuracy) }
            val fogViewport = currentViewport
            refreshExecutor.execute {
                try {
                    val updateStart = System.nanoTime()
                    val revision = repository.revision
                    val next = repository.snapshot()
                    // Compute expensive history/geometry work away from the UI thread.
                    next.totalMeters
                    DailyFootprints.days(next).forEach { it.points; it.meters; it.segments; it.focusTrack() }
                    val road = repository.roadSnapshot()
                    val today = DailyFootprints.key(System.currentTimeMillis())
                    val daily = emptyMap<String,RoadState>()
                    val displayedRoad = RoadState()
                    val displayedTrack = DailyFootprints.select(next,day)
                    // Day review has its own ground, not later exploration or today's GPS dot.
                    val veilPaths = RecordedTrail.exploredPaths(displayedTrack.realTracks)
                    val veilStart = System.nanoTime()
                    // The painter reuses its two buffers. A deferred handover and the SDK's GL
                    // thread can still be reading either one after the next paint starts.
                    val fogImage = fogPainter.update(veilPaths, fogColour, MapHistory.anchor(displayedTrack,day) ?: fogAnchor, fogViewport)
                        ?.copy(android.graphics.Bitmap.Config.ARGB_8888,false)
                    val veilNanos = System.nanoTime() - veilStart
                    if (BuildConfig.DEBUG) {
                        PerfTrace.update(veilStart - updateStart, veilNanos, fogPainter.lastUpdateRebuilt)
                    }
                    val fogBounds = fogPainter.frame()
                    val fogStamp = fogPainter.revision
                    // The line the map draws itself exists for the views the veil cannot cover: the
                    // veil's image is capped at a few hundred pixels, so once the corridor falls
                    // under a couple of them it stops being a corridor at all, and the record of
                    // where the person has been would vanish on exactly the screen where it matters
                    // most. Above that the cleared ground is plainly visible, so sending a line too
                    // would only cost the UI thread a GeoJSON rebuild per update while walking.
                    // The predicate is TrailPaint's, not a copy of it: the veil leaves the corridor
                    // out exactly when this line is drawn, and the two must not be able to disagree.
                    val veilShowsCorridor = fogBounds?.let { TrailPaint.corridorVisible(it) } == true
                    val trailPaths = veilPaths
                    val trail = when {
                        // A chosen day is always drawn: that is the route the person asked to see.
                        day != null -> TrailShape.network(trailPaths, fogBounds)
                        veilShowsCorridor -> emptyList()
                        else -> TrailShape.network(trailPaths, fogBounds)
                    }
                    runOnUiThread {
                        try {
                            if(selectedDay!=day || generation!=mapSceneGeneration) return@runOnUiThread
                            fogPaths=veilPaths
                            val uiStart = System.nanoTime()
                            snapshot = next; lastRevision = revision
                            dailyRoads=daily; dailyRoadDate=today
                            mapTrackState=displayedTrack; mapRoadState=displayedRoad
                            mapTrailPaths = trail
                            roadState = road
                            val assignNanos = System.nanoTime() - uiStart
                            handOverVeil(fogImage, fogBounds, fogStamp)
                            updateLocationLayer()
                            if (BuildConfig.DEBUG) {
                                android.util.Log.d("WalkPaintPerf", String.format(java.util.Locale.ROOT,
                                    "UI parts assign_ms=%.1f handover_ms=%.1f total_ms=%.1f",
                                    assignNanos / 1e6,
                                    (System.nanoTime() - uiStart - assignNanos) / 1e6,
                                    (System.nanoTime() - uiStart) / 1e6))
                            }
                            if (BuildConfig.DEBUG) PerfTrace.ui(System.nanoTime() - uiStart)
                        } finally {
                            // Released after the handover, so the painter never draws into the bitmap
                            // while the map is uploading it.
                            refreshing.set(false)
                        }
                    }
                } catch (error: Throwable) {
                    refreshing.set(false)
                    throw error
                }
            }
        }
        updateLocationLayer()
    }

    private fun notificationsGranted() = Build.VERSION.SDK_INT < 33 ||
        checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun appSettingsIntent() = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:$packageName"))

    private fun beginEnable() {
        if(pendingEnable) return
        pendingEnable=true;backgroundDeferred=false
        if(!AmbientTracking.hasFineLocation(this) &&
            !getSharedPreferences("ui_onboarding",Context.MODE_PRIVATE).getBoolean("auto_notice_v3_seen",false)) {
            confirmEnable=true
        } else continueEnable()
    }

    private fun continueEnable() {
        if(!pendingEnable) return
        when(TrackingSetup.next(Build.VERSION.SDK_INT,AmbientTracking.hasFineLocation(this),
            AmbientTracking.hasBackgroundLocation(this),backgroundDeferred)) {
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
        else openRecordingDiagnostics()
    }

    private fun openRecordingNotificationSettings() {
        val settings=Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE,packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID,"ambient-footprints")
        runCatching { startActivity(settings) }.onFailure {
            runCatching { startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE,packageName)) }
                .onFailure { toast("无法打开通知设置，请在手机设置中管理足迹涂色的通知") }
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
        locateZoom.request()
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
                follow.locate();lastCameraPoint=null;syncHeadingLifecycle()
                toast("暂时没有当前 GPS，已定位到最近足迹，正在继续寻找卫星信号")
            } else {
                toast("正在获取当前定位，请检查系统 GPS 并到室外稍等")
            }
            beginVisibleLocation()
        }
    }

    @SuppressLint("MissingPermission") // Fine permission is checked below; revocation exceptions return null.
    private fun latestSystemLocation(): Location? {
        if (!AmbientTracking.hasFineLocation(this)) return null
        val manager = locationManager ?: getSystemService(LocationManager::class.java).also { locationManager = it }
        return try {
            chooseMapFix(listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
                .filter { manager.isProviderEnabled(it) }
                .mapNotNull { manager.getLastKnownLocation(it) } + listOfNotNull(AmbientTracking.lastGps))
        } catch (_: Exception) { null }
    }

    @SuppressLint("MissingPermission") // Fine permission is checked below; runCatching also covers revocation.
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

    private fun trackingDiagnostics(onFacts:(DiagnosisFacts)->Unit={}): String {
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
            "本次持续 GPS 回调：" + (GpsReceipts.age(GpsReceipts.continuous,SystemClock.elapsedRealtime())?.let { "${it/1000} 秒前" } ?: "尚无"),
            "历史最近 GPS 回调：" + age((diagnostic["gps_callback_at"] as? Number)?.toLong()),
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
            callbackAgeMs = GpsReceipts.age(GpsReceipts.continuous,SystemClock.elapsedRealtime()),
            savedAgeMs = lastSaved?.let { (now - it).coerceAtLeast(0L) },
            savedCount = snapshot.realPointCount.toLong(),
            movingThisSession = (diagnostic["session_moving_count"] as? Number)?.toLong() ?: 0L,
            savedThisSession = (diagnostic["session_saved_count"] as? Number)?.toLong() ?: 0L,
            filteredCount = (diagnostic["session_filtered_count"] as? Number)?.toLong() ?: 0L,
            stationaryFiltered = (diagnostic["session_filter_stationary_count"] as? Number)?.toLong() ?: 0L,
            jumpFiltered = (diagnostic["session_filter_jump_count"] as? Number)?.toLong() ?: 0L,
            accuracyFiltered = (diagnostic["session_filter_accuracy_count"] as? Number)?.toLong() ?: 0L,
            longestGapMs = 0L, // historical maxima belong in folded developer evidence
            serviceStartCount = (diagnostic["start_count"] as? Number)?.toLong() ?: 0L,
            databaseError = repository.lastError,
            dozeIdle = power.isDeviceIdleMode,
            statusCode=RecordingStatus.assess(RecordingDiagnostics.signals(this,repository.lastError)).code,
            serviceAgeMs=GpsReceipts.age(GpsReceipts.started,SystemClock.elapsedRealtime()),
            pendingThisSession=(diagnostic["session_pending_count"] as? Number)?.toLong() ?: 0L
        )
        onFacts(facts)
        return Diagnosis.report(facts) + "\n\n—— 原始计数（给开发者看） ——\n" +
            rows.joinToString("\n") + "\n\n" + RecordingDiagnostics.summary(this) +
            "\n\n绘制来源：已保存的定位直接涂色。连续轨迹 ${RecordedTrail.paths(snapshot.realTracks).count { it.size>=2 }} 段；累计记录 ${snapshot.totalMeters.toLong()} 米（重复出行累计，不是去重道路里程）。" +
            "\n\n持续定位已启用，不长期持有 CPU 唤醒锁；常规诊断每30秒批量保存，异常退出最多损失末尾30秒诊断（不影响 SQLite 足迹）。vivo 等厂商需允许后台运行；强制停止后须手动打开 App，不能保证永不断录。"
    }

    private fun openRecordingDiagnostics() {
        val base=trackingDiagnostics { diagnosticFacts=it }
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

    @SuppressLint("MissingPermission") // Fine permission is checked below; GNSS registration failure is handled.
    private fun startShortGps() {
        if(!AmbientTracking.hasFineLocation(this)) { toast("请先在更多设置允许精确定位");return }
        stopShortGps("重新开始短测")
        val manager=getSystemService(LocationManager::class.java)
        ShortGpsSurvey.start(AmbientTracking.running)
        val callback=object:android.location.GnssStatus.Callback() {
            override fun onSatelliteStatusChanged(status:android.location.GnssStatus) { ShortGpsSurvey.satellites(status) }
        }
        shortGnss=callback
        runCatching { manager.registerGnssStatusCallback(callback,mainHandler) }
            .onFailure { ShortGpsSurvey.event("卫星订阅不可用") }
        shortProbe=FreshGpsProbe(manager,mainHandler).also { p -> p.start { fix,reason ->
            if(fix!=null) ShortGpsSurvey.fix(false)
            ShortGpsSurvey.event(reason)
        } }
        ShortGpsSurvey.event(if(AmbientTracking.running) "持续记录监听运行中" else "自动记录未运行，仅做短测")
        shortGpsText=ShortGpsSurvey.report();shortGpsActive=true
        mainHandler.postDelayed(finishShortGps,60_000)
    }
    private fun stopShortGps(reason:String) {
        mainHandler.removeCallbacks(finishShortGps)
        shortProbe?.cancel();shortProbe=null
        shortGnss?.let { runCatching { getSystemService(LocationManager::class.java).unregisterGnssStatusCallback(it) } }
        shortGnss=null
        ShortGpsSurvey.stop(reason)
        shortGpsText=ShortGpsSurvey.report();shortGpsActive=false
    }

    private fun prepareFeedback(category:String,description:String) {
        if(feedbackBusy) return
        feedbackBusy=true
        // The field export is a different artefact from a described problem: it hands over the raw
        // material, not a summary of it.
        if (category == FieldReport.CATEGORY) { prepareFieldReport(); return }
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
    /** One line about what the camera is showing, for the field export. */
    private fun cameraSummary(): String {
        if (!amapActive) return "地图尚未启用"
        return amapController.cameraSummary() + " 跟随=${follow.following}"
    }

    private fun fmt(value: Double, digits: Int) = String.format(java.util.Locale.ROOT, "%.${digits}f", value)

    /**
     * Which engine the phone is really running, and why.
     *
     * The record and the screen disagreed because the screen was never running the engine the
     * record was written against, and nothing in the export said so. The AMap SDK ships ARM-only
     * native libraries; a universal x86 process can fall back while an ARM64 artifact uses the
     * advertised native bridge. This line distinguishes the actual engine's behaviour from the
     * other's, on whichever engine the person happens to be holding.
     */
    private fun engineChoice(): String = VeilReport.engineChoice(
        supported = amapSupported,
        firstAbi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty(),
        keyConfigured = BuildConfig.AMAP_KEY.isNotBlank(),
        consent = amapConsent,
        active = amapActive,
    )

    /** What the active engine's overlay stack actually contains, read from the running engine. */
    private fun mapEngineDetail(): String =
        if (amapActive) amapController.describe() else "地图尚未启用"

    /**
     * The raw material, gathered on demand.
     *
     * This is the answer to "I can feel something is wrong and cannot describe it": every fix the
     * receiver delivered in the last half hour, every fix that was thrown away with the reason and
     * the accuracy it carried, the camera and veil the person was actually looking at, and the
     * database's own count. Nothing is uploaded; the person copies or shares the text themselves.
     */
    private fun prepareFieldReport(onReady: ((String) -> Unit)? = null) {
        if (feedbackBusy) return
        val application = applicationContext
        val enabled = AmbientTracking.enabled(this); val running = AmbientTracking.running
        val fine = AmbientTracking.hasFineLocation(this); val background = AmbientTracking.hasBackgroundLocation(this)
        val notifications = notificationsGranted()
        val gpsProvider = runCatching {
            getSystemService(android.location.LocationManager::class.java)
                .isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)
        }.getOrDefault(false)
        val whitelisted = runCatching {
            getSystemService(android.os.PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
        }.getOrDefault(false)
        val engine = if (amapActive) "高德联网地图" else "高德地图 · 待启用"
        val camera = cameraSummary()
        val viewport = currentViewport
        val frame = fogFrame
        val bitmap = fogBitmap
        // The engine is asked what it is showing here, on the UI thread: both map SDKs have to be
        // touched from it, and a report that described a different engine than the one on screen
        // would be worse than no report at all.
        val engineDetail = engineChoice() + "\n" + mapEngineDetail()
        val lineByMap = mapTrailPaths.isNotEmpty()
        refreshExecutor.execute {
            val text = runCatching {
                val state = repository.snapshot()
                val now = System.currentTimeMillis()
                val last = state.realTracks.flatMap { it.points }.maxByOrNull { it.time }
                // Counted here rather than on the map thread: it is a plain array walk, and the
                // painter cannot be redrawing the same image because this work is on its executor.
                val stats = bitmap?.let { image ->
                    val argb = IntArray(image.width * image.height)
                    image.getPixels(argb, 0, image.width, 0, 0, image.width, image.height)
                    VeilReport.of(argb)
                }
                FieldReport.build(
                    FieldFacts(
                        now = now,
                        version = "${BuildConfig.VERSION_NAME}（code${BuildConfig.VERSION_CODE}）",
                        device = "${Build.BRAND} ${Build.MODEL}",
                        android = Build.VERSION.SDK_INT.toString(),
                        engine = engine,
                        engineDetail = engineDetail,
                        permissions = "精确定位=$fine 后台定位=$background 通知=$notifications " +
                            "手机定位开关=$gpsProvider 电池白名单=$whitelisted 记录开关=$enabled 后台服务=$running",
                        recording = RecordingDiagnostics.summary(application),
                        camera = "$camera\n可见范围=" + (viewport?.let {
                            "纬度${fmt(it.latSpan, 4)}° 经度${fmt(it.lonSpan, 4)}°"
                        } ?: "地图还没报告（用兜底大帧）"),
                        veil = VeilReport.frame(frame, lineByMap) + "\n" +
                            (stats?.let { VeilReport.image(it, fogRevision) }
                                ?: "雾位图=无（还没画出来）"),
                        storage = "数据库点数=${state.realPointCount} 行程=${state.realTracks.size} " +
                            "记录里程=${state.totalMeters.toInt()}米" +
                            (last?.let { " 最后一点=${SimpleDateFormat("MM-dd HH:mm:ss", Locale.ROOT).format(Date(it.time))} 精度=${it.accuracy.toInt()}米" } ?: ""),
                        diagnosis = trackingDiagnostics(),
                    ),
                    FieldRecorder.snapshot(now),
                )
            }.getOrElse { "记录现场暂时无法整理，足迹保留。请稍后重试。" }
            runOnUiThread {
                feedbackBusy = false; feedbackReport = text
                // The command-driven export needs the same text the person would see, at the same
                // moment, so it is handed over here rather than assembled a second time.
                onReady?.invoke(text)
            }
        }
    }

    private fun handleAutomaticFieldReport(request: Intent?) {
        val file = java.io.File(getExternalFilesDir(null) ?: filesDir, "field-report.txt")
        AutomaticFieldExport(BuildConfig.DEBUG, BuildConfig.DEVELOPER_BUILD, file).handle(
            request?.getBooleanExtra(EXTRA_EXPORT_FIELD_REPORT, false) == true,
        ) { ready -> prepareFieldReport { text ->
            runCatching { ready(text) }.onFailure {
                android.util.Log.w("WalkPaintField", "developer field report write failed")
            }
        } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAutomaticFieldReport(intent)
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
    }

    /** What the map is showing right now, in WGS84. Null until the engine reports a real view. */
    private fun mapViewport(): ViewBounds? {
        val view: ViewBounds? = if (amapActive) amapController.viewport() else null
        // Before the engine has a real projection it reports a whole-world region (170° across was
        // measured at launch). A frame built from that is a globe-sized image, so it is treated as
        // "no view yet": the veil then falls back to a wide frame around the person, which still
        // covers the screen. Both engines are checked the same way, because on the phone the same
        // launch state is what produced a veil that only appeared after the person zoomed in.
        if (view == null) return null
        if (view.latSpan > MAX_SANE_VIEW_DEGREES || view.lonSpan > MAX_SANE_VIEW_DEGREES) return null
        return view
    }

    private fun onMapCameraMoved() { refreshVeilForViewport(force = false) }

    private fun onMapCameraSettled() {
        cameraTrace("settled")
        refreshVeilForViewport(force = true)
    }

    /**
     * Debug-only record of what the map is actually showing, so a screenshot can be tied to the
     * scene it was taken in instead of being judged on colour alone: the camera centre and zoom,
     * whether a single day is selected or the cumulative view, the person's own position, and the
     * visible bounds.
     */
    private fun cameraTrace(stage: String) {
        if (!BuildConfig.DEBUG) return
        val amapCamera = if (amapActive) amapController.cameraSource() else null
        val fix = userFix
        val view = currentViewport
        android.util.Log.d("WalkPaintCam", "$stage lat=${amapCamera?.first} lon=${amapCamera?.second} " +
            "zoom=${amapCamera?.third} day=${selectedDay ?: "cumulative"} " +
            "fixLat=${fix?.latitude} fixLon=${fix?.longitude} " +
            "viewLat=${view?.latSpan} viewLon=${view?.lonSpan} " +
            "frameLat=${fogFrame?.latMin}..${fogFrame?.latMax} " +
            "frameLon=${fogFrame?.lonMin}..${fogFrame?.lonMax}")
    }

    /**
     * Debug-only trace of the whole handover chain: the viewport, the frame the veil was drawn
     * into, the bitmap that came out of it (with a few sampled pixels as a content fingerprint) and
     * the style it is handed to. Written so one run says which stage is wrong instead of guessing.
     */
    private fun fogTrace(stage: String) {
        if (!BuildConfig.DEBUG) return
        val frame = fogFrame
        val bitmap = fogBitmap
        val view = currentViewport
        val fingerprint = bitmap?.let { image ->
            listOf(0.5f to 0.5f, 0.2f to 0.5f, 0.8f to 0.5f, 0.5f to 0.2f, 0.5f to 0.8f).joinToString(",") { (fx, fy) ->
                Integer.toHexString(image.getPixel(
                    (image.width * fx).toInt().coerceIn(0, image.width - 1),
                    (image.height * fy).toInt().coerceIn(0, image.height - 1)))
            }
        } ?: "none"
        android.util.Log.d("WalkPaintFog", "$stage rev=$fogRevision " +
            "viewLat=${view?.latSpan} viewLon=${view?.lonSpan} " +
            "framePx=${frame?.widthPx}x${frame?.heightPx} " +
            "frameLat=${frame?.latMin}..${frame?.latMax} frameLon=${frame?.lonMin}..${frame?.lonMax} " +
            "bmpPx=${bitmap?.width}x${bitmap?.height} fp=$fingerprint")
        runCatching {
            bitmap?.let { image ->
                java.io.File(cacheDir, "fog-$stage.png").outputStream().use { out ->
                    image.compress(android.graphics.Bitmap.CompressFormat.PNG, 90, out)
                }
            }
        }
    }

    /**
     * Redraws the veil for the view the map has moved to.
     *
     * The veil follows the map, so panning to ground walked days ago still finds it painted and the
     * ground in between stays behind fog. A redraw is only started when the view has moved far
     * enough to matter, and it runs on the same worker as the state updates so the two never
     * overlap; the finished image is handed over on the UI thread only if it really changed.
     */
    /**
     * Hands the veil image to the map, at most once every [VEIL_HANDOVER_MS].
     *
     * Must be called on the UI thread. A handover that lands inside the gap is remembered and
     * performed shortly afterwards, so the last change is always shown rather than dropped.
     */
    private fun handOverVeil(image: android.graphics.Bitmap?, frame: PaintFrame?, revision: Int) {
        val now = android.os.SystemClock.uptimeMillis()
        val elapsed = now - lastVeilHandover
        // AMap's immutable Android display list has no asynchronous texture/bounds handover.
        // Keeping the old SDK texture-upload throttle here delayed already completed fog by 600ms.
        val handoverGap=if(amapActive) 0L else VEIL_HANDOVER_MS
        if (elapsed < handoverGap) {
            pendingVeil = Triple(image, frame, revision)
            if (!veilHandoverPending) {
                veilHandoverPending = true
                mainHandler.postDelayed({
                    veilHandoverPending = false
                    val pending = pendingVeil
                    pendingVeil = null
                    if (pending != null) handOverVeil(pending.first, pending.second, pending.third)
                }, handoverGap - elapsed)
            }
            return
        }
        lastVeilHandover = now
        if (revision != fogRevision) {
            fogBitmap = image; fogFrame = frame; fogRevision = revision
            updateMapLayers()
        }
    }

    private fun refreshVeilForViewport(force: Boolean) {
        if (!activityResumed || currentTab != 0) return
        val now = android.os.SystemClock.uptimeMillis()
        val elapsed = now - lastVeilRefresh
        if (!force && elapsed < VEIL_REFRESH_MS) return
        if (force && elapsed < VEIL_REFRESH_SETTLE_MS) {
            // A map that keeps firing "settled" must not redraw on every event; the redraw it is
            // owed is coalesced into one, and it is always done, so the last view is drawn too.
            if (!veilRefreshPending) {
                veilRefreshPending = true
                mainHandler.postDelayed({
                    veilRefreshPending = false
                    refreshVeilForViewport(force = true)
                }, VEIL_REFRESH_SETTLE_MS - elapsed)
            }
            return
        }
        lastVeilRefresh = now
        currentViewport = mapViewport()
        val paths = fogPaths
        if (paths.isEmpty()) {
            // The veil is built from the stored tracks, and the first camera callback can arrive
            // before any state update has run. Without this the first "settled" event drew nothing
            // and the map stayed unveiled until something else happened to refresh the state.
            lastRevision = -1L
            updateUi()
            return
        }
        if (!refreshing.compareAndSet(false, true)) {
            // A state update is already running. Retry rather than drop: a camera that settles into
            // a new viewport exactly then would otherwise leave the veil drawn for the old view,
            // which shows as fog missing from the new one.
            mainHandler.postDelayed({ refreshVeilForViewport(force = true) }, 250)
            return
        }
        val viewport = currentViewport
        val colour = roadColor
        val handOver = true
        val day = selectedDay
        val generation=mapSceneGeneration
        val anchor = MapHistory.anchor(mapTrackState,day) ?: userFix?.let { TrackPoint(it.latitude, it.longitude, it.time, it.accuracy) }
        val previousFogRevision=fogRevision
        refreshExecutor.execute {
            try {
                val start = System.nanoTime()
                val paintedImage = fogPainter.update(paths, colour, anchor, viewport)
                val nanos = System.nanoTime() - start
                if (BuildConfig.DEBUG) PerfTrace.pan(nanos)
                val frame = fogPainter.frame()
                val revision = fogPainter.revision
                if(revision==previousFogRevision) { runOnUiThread {refreshing.set(false)};return@execute }
                val image=paintedImage?.copy(android.graphics.Bitmap.Config.ARGB_8888,false)
                val linePaths = paths
                val route = if (day == null && frame?.let { TrailPaint.corridorVisible(it) } == true)
                    emptyList() else TrailShape.network(linePaths, frame)
                runOnUiThread {
                    try {
                        // The cheap coverage mask remains in place until this actual ground
                        // frame is handed over, including during an active pinch.
                        if (handOver && day == selectedDay && generation==mapSceneGeneration) {
                            mapTrailPaths = route
                            handOverVeil(image, frame, revision)
                        }
                    } finally {
                        // Released here, not before the handover: the painter must not be drawing
                        // into the same bitmap while the map is reading it.
                        refreshing.set(false)
                    }
                }
            } catch (error: Throwable) {
                refreshing.set(false)
                throw error
            }
        }
    }

    private fun chooseRoadColor(color:Int) {
        roadAppearance.save(color)
        roadColor=color
        amapController.setExploredColor(color)
        // The veil carries the pigment as well, so it has to be repainted in the new colour. Same
        // single-threaded worker as the refresh, so the two can never overlap.
        refreshExecutor.execute { fogPainter.clear() }
        // Nothing else changed, so the refresh would not run; ask for it explicitly.
        lastRevision = -1
        updateUi()
    }

    private fun updateMapLayers() {
        if (!amapActive) return
        // 回放时画“长到一半”的那条线；停回放后自动回到当天完整足迹。
        amapController.render(mapTrackState,mapRoadState,if(replayActive) replayPaths else mapTrailPaths,fogBitmap,fogFrame,fogRevision)
    }

    private fun mapLocation():Location? = MapHistory.anchor(mapTrackState,selectedDay)?.let { p ->
        Location("history").apply { latitude=p.lat;longitude=p.lon;time=p.time;accuracy=p.accuracy }
    } ?: userFix?.takeIf {selectedDay==null}

    private fun updateLocationLayer() {
        val location=mapLocation()
        if (amapActive) amapController.updateUser(location)
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
        locateZoom.cancel()
        follow.gesture();amapController.cancelFollowingAnimation()
        syncHeadingLifecycle()
    }

    private fun syncFollowCamera(force:Boolean=false, streetZoom:Boolean=false) {
        if(streetZoom) locateZoom.request()
        if(!follow.following) return
        val fix=userFix?.takeIf { follow.accepts(fixQuality(it)) } ?: return
        val point=TrackPoint(fix.latitude,fix.longitude,fix.elapsedRealtimeNanos/1_000_000L,fix.accuracy)
        if(!force && !CameraFixPolicy.plausible(lastCameraPoint,point)) return
        if(!force && lastCameraPoint?.let { Geo.meters(it,point)<1.0 }==true &&
            kotlin.math.abs(HeadingFilter.delta(lastCameraBearing,mapHeading))<1f) return
        val animate=Settings.Global.getFloat(contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)>0f
        // Only an explicit "where am I" lifts a wide camera to street level.
        val minZoom=if(locateZoom.pending) StreetZoom else null
        if(amapActive) {
            if(amapController.view()==null) return
            if(!amapController.followCamera(fix,mapHeading,animate,minZoom)) return
        } else return
        locateZoom.delivered(true)
        lastCameraPoint=point;lastCameraBearing=mapHeading;firstMapCenter=false
    }

    @Composable
    private fun AmapNativeView() {
        val controller = remember { amapController }
        val view = remember { controller.create(mapTrackState, mapLocation(),mapRoadState,roadColor) }
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
                mapViewReady=false
                headingSensor.stop()
                runCatching { controller.release(view) }
            }
        }
        AndroidView(factory = { view }, modifier = Modifier.fillMaxSize())
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("pendingEnable", pendingEnable)
        outState.putBoolean("pendingSettingsEnable", pendingSettingsEnable)
        outState.putBoolean("pendingBackgroundSettings",pendingBackgroundSettings)
        outState.putBoolean("backgroundDeferred",backgroundDeferred)
        outState.putBoolean("confirmEnable",confirmEnable)
        outState.putBoolean("showBackgroundPrompt",showBackgroundPrompt)
        outState.putBoolean("showFineSettings",showFineSettings)
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume();GrowthSession.resume(this)
        activityResumed=true;syncHeadingLifecycle()
        mainHandler.removeCallbacks(refreshTask)
        mainHandler.post(refreshTask)
        updateUi()
        // Same reason as the tab switch: coming back to a resting camera would otherwise leave an
        // unveiled map on screen, and an unveiled map is indistinguishable from explored ground.
        refreshVeilForViewport(force = true)
        beginVisibleLocation()
        if (AmbientTracking.enabled(this) && AmbientTracking.hasFineLocation(this) && !AmbientTracking.running) {
            AmbientTracking.start(this)
        }
    }
    override fun onPause() {
        GrowthSession.pause()
        mainHandler.removeCallbacks(refreshTask)
        stopShortGps("离开前台，结束短测")
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
