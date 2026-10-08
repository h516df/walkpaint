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

class MainActivity : ComponentActivity() {
    private val repository by lazy { (application as WalkPaintApp).repository }
    private var currentTab by mutableIntStateOf(0)
    private var snapshot by mutableStateOf(TrackState())
    private var userFix by mutableStateOf<Location?>(null)
    private var trackingStatus by mutableStateOf("正在检查位置状态")
    private var trackingEnabled by mutableStateOf(false)
    private var trackingRunning by mutableStateOf(false)
    private var backgroundGranted by mutableStateOf(false)
    private var confirmEnable by mutableStateOf(false)
    private var promptLocation by mutableStateOf(false)
    private var pendingEnable by mutableStateOf(false)
    private var pendingCenter by mutableStateOf(false)
    private var lastRevision = -1L

    private var mapInstance: MapLibreMap? = null
    private var mapWidget: MapView? = null
    private var firstMapCenter = true
    private val mainHandler = Handler(Looper.getMainLooper())
    private var locationManager: LocationManager? = null

    private val visibleLocationListener = LocationListener { fix ->
        if (fix.hasAccuracy() && fix.accuracy < 200f) {
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
                if (pendingEnable) {
                    AmbientTracking.setEnabled(this, true)
                    AmbientTracking.start(this)
                    requestNotification()
                } else if (pendingCenter) centerUser()
            } else toast("需要精确定位才能看到当前位置和绘制足迹")
            pendingEnable = false
            updateUi()
        }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ -> updateUi() }

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
        MapLibre.getInstance(applicationContext)
        if (!AmbientTracking.hasFineLocation(this) &&
            !getSharedPreferences("ui_onboarding", Context.MODE_PRIVATE).getBoolean("location_prompt_seen", false)) {
            promptLocation = true
        }
        snapshot = repository.snapshot()
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
                    needsBackground = trackingEnabled && !backgroundGranted,
                    onLocate = { centerUser() },
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
                        currentTab = 0
                        val p = trip.points.firstOrNull()
                        if (p != null) {
                            mainHandler.postDelayed({ focusCoordinate(p.lat, p.lon, 15.5) }, 500)
                        }
                    },
                    onExport = {
                        val title = "WalkPaint-${SimpleDateFormat("yyyyMMdd-HHmm", Locale.ROOT).format(Date())}.json"
                        exportLauncher.launch(title)
                    },
                    onImport = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                    onBackgroundSettings = { openAppSettings() },
                    internalBackupCount = repository.backupFiles().size,
                    mapContent = { NativeMapView() }
                )

                if (promptLocation) AlertDialog(
                    onDismissRequest = {
                        promptLocation = false
                        getSharedPreferences("ui_onboarding", Context.MODE_PRIVATE)
                            .edit().putBoolean("location_prompt_seen", true).apply()
                    },
                    title = { Text("先找到你的位置", fontWeight = FontWeight.Bold, color = Ink) },
                    text = { Text("允许精确定位后，地图就能显示你所在的位置。是否持续记录足迹由你另外决定，不会偷偷开启。") },
                    confirmButton = {
                        Button(onClick = {
                            promptLocation = false
                            getSharedPreferences("ui_onboarding", Context.MODE_PRIVATE)
                                .edit().putBoolean("location_prompt_seen", true).apply()
                            pendingCenter = true
                            askLocation()
                        }) { Text("显示我的位置") }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            promptLocation = false
                            getSharedPreferences("ui_onboarding", Context.MODE_PRIVATE)
                                .edit().putBoolean("location_prompt_seen", true).apply()
                        }) { Text("稍后再说") }
                    }
                )
                if (confirmEnable) AlertDialog(
                    onDismissRequest = { confirmEnable = false },
                    title = { Text("开启自动足迹？", color = Ink, fontWeight = FontWeight.Bold) },
                    text = {
                        Text(
                            "只需要启用一次。之后会在后台持续尝试记录行走路线，并显示常驻通知。" +
                                "为省电，静止时会降低 GPS 频率，因此短途可能遗漏。" +
                                "你可以随时暂停。系统省电或强制停止可能中断记录；重启后自动恢复通常还需要“始终允许定位”。"
                        )
                    },
                    confirmButton = {
                        Button(onClick = {
                            confirmEnable = false
                            if (AmbientTracking.hasFineLocation(this)) {
                                AmbientTracking.setEnabled(this, true)
                                AmbientTracking.start(this)
                                requestNotification()
                                updateUi()
                            } else {
                                pendingEnable = true
                                askLocation()
                            }
                        }) { Text("启用自动足迹") }
                    },
                    dismissButton = { TextButton(onClick = { confirmEnable = false }) { Text("暂不开启") } }
                )
            }
        }
        mainHandler.post(refreshTask)
    }

    private fun updateUi() {
        trackingEnabled = AmbientTracking.enabled(this)
        trackingRunning = AmbientTracking.running
        trackingStatus = repository.lastError ?: AmbientTracking.status
        backgroundGranted = AmbientTracking.hasBackgroundLocation(this)
        userFix = AmbientTracking.lastFix ?: userFix
        if (lastRevision != repository.revision) {
            snapshot = repository.snapshot()
            lastRevision = repository.revision
            updateMapLayers()
        } else updateLocationLayer()
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
            toast("正在获取当前定位，请确保系统位置服务已开启")
            beginVisibleLocation()
        }
    }

    private fun latestSystemLocation(): Location? {
        if (!AmbientTracking.hasFineLocation(this)) return null
        val manager = locationManager ?: getSystemService(LocationManager::class.java).also { locationManager = it }
        return try {
            listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
                .filter { manager.isProviderEnabled(it) }
                .mapNotNull { manager.getLastKnownLocation(it) }
                .filter { System.currentTimeMillis() - it.time < 2 * 60 * 60 * 1000 }
                .maxByOrNull { it.time }
        } catch (_: Exception) { null }
    }

    private fun beginVisibleLocation() {
        if (!AmbientTracking.hasFineLocation(this)) return
        val lm = locationManager ?: getSystemService(LocationManager::class.java).also { locationManager = it }
        runCatching {
            latestSystemLocation()?.let { updatePosition(it, firstMapCenter || pendingCenter) }
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 8_000, 1f, visibleLocationListener, Looper.getMainLooper())
            }
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 20_000, 8f, visibleLocationListener, Looper.getMainLooper())
            }
        }
    }

    private fun updatePosition(fix: Location, center: Boolean) {
        userFix = fix
        AmbientTracking.lastFix = fix
        updateLocationLayer()
        if (center || firstMapCenter) {
            focusCoordinate(fix.latitude, fix.longitude, 16.0)
            firstMapCenter = false
        }
    }

    private fun openAppSettings() {
        toast("在定位权限中选择“始终允许”，再返回 App")
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        })
    }

    private fun focusCoordinate(lat: Double, lon: Double, zoom: Double = 16.0) {
        mapInstance?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lon), zoom))
    }

    private fun makeMapView(): MapView {
        val view = MapView(this)
        view.onCreate(null)
        mapWidget = view
        view.getMapAsync { loaded ->
            if (mapWidget !== view) return@getMapAsync
            mapInstance = loaded
            loaded.setStyle("https://tiles.openfreemap.org/styles/liberty") { style ->
                if (mapWidget !== view) return@setStyle
                style.addSource(GeoJsonSource("wp-real", geoJson(snapshot.realTracks)))
                style.addSource(GeoJsonSource("wp-demo", geoJson(snapshot.tracks.filter { it.demo })))
                style.addSource(GeoJsonSource("wp-user", userJson(userFix)))
                style.addLayer(LineLayer("wp-real-line", "wp-real").withProperties(
                    PropertyFactory.lineColor(android.graphics.Color.rgb(18, 145, 134)),
                    PropertyFactory.lineWidth(7f),
                    PropertyFactory.lineOpacity(0.88f),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND)
                ))
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
                    PropertyFactory.circleStrokeWidth(3f)
                ))
                val fix = userFix ?: latestSystemLocation()
                if (fix != null) {
                    updatePosition(fix, true)
                } else {
                    val recent = snapshot.realTracks.flatMap { it.points }.lastOrNull()
                    if (recent != null) loaded.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(recent.lat, recent.lon), 14.5))
                    else loaded.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(20.0, 0.0), 2.5))
                }
            }
        }
        return view
    }

    private fun updateMapLayers() {
        val style = mapInstance?.style ?: return
        style.getSourceAs<GeoJsonSource>("wp-real")?.setGeoJson(geoJson(snapshot.realTracks))
        style.getSourceAs<GeoJsonSource>("wp-demo")?.setGeoJson(geoJson(snapshot.tracks.filter { it.demo }))
    }

    private fun updateLocationLayer() {
        mapInstance?.style?.getSourceAs<GeoJsonSource>("wp-user")?.setGeoJson(userJson(userFix))
    }

    private fun geoJson(tracks: List<Track>): String {
        val features = JSONArray()
        tracks.forEach { trip ->
            trip.points.groupBy { it.segment }.values.forEach { points ->
                if (points.size >= 2) {
                    val positions = JSONArray()
                    points.forEach { p -> positions.put(JSONArray().put(p.lon).put(p.lat)) }
                    val geometry = JSONObject().put("type", "LineString").put("coordinates", positions)
                    features.put(JSONObject().put("type", "Feature")
                        .put("properties", JSONObject()).put("geometry", geometry))
                }
            }
        }
        return JSONObject().put("type", "FeatureCollection").put("features", features).toString()
    }

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

    override fun onResume() {
        super.onResume()
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
        mainHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()
}
