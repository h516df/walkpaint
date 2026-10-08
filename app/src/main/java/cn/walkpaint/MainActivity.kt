package cn.walkpaint

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource

class MainActivity : Activity() {
    private lateinit var mapView: MapView
    private lateinit var distanceView: TextView
    private lateinit var tripsView: TextView
    private lateinit var statusView: TextView
    private lateinit var recordButton: Button
    private lateinit var demoButton: Button
    private var map: MapLibreMap? = null
    private val handler = Handler(Looper.getMainLooper())
    private var lastState: TrackState? = null
    private val app: WalkPaintApp get() = application as WalkPaintApp

    private val refreshTask = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, 1800)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(applicationContext)
        val root = FrameLayout(this)
        root.setBackgroundColor(Color.rgb(243, 248, 247))
        mapView = MapView(this)
        root.addView(mapView, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
        mapView.onCreate(savedInstanceState)

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(14))
            background = pill(Color.WHITE, 22)
            elevation = dp(5).toFloat()
        }
        val title = label("足迹涂色", 24f, Color.rgb(25, 72, 73), true)
        top.addView(title)
        top.addView(label("走过的每一步，都为城市添一点颜色", 12f, Color.rgb(111, 135, 133)))
        val numbers = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(12), 0, dp(4))
        }
        val dCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        distanceView = label("0.00 km", 23f, Color.rgb(28, 117, 111), true)
        dCol.addView(distanceView)
        dCol.addView(label("累计里程", 12f, Color.rgb(105, 126, 124)))
        numbers.addView(dCol, LinearLayout.LayoutParams(0, -2, 1f))
        val tCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        tripsView = label("0", 23f, Color.rgb(28, 117, 111), true)
        tCol.addView(tripsView)
        tCol.addView(label("探索次数", 12f, Color.rgb(105, 126, 124)))
        numbers.addView(tCol, LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(numbers)
        root.addView(top, FrameLayout.LayoutParams(-1, -2).apply {
            gravity = Gravity.TOP
            setMargins(dp(14), dp(16), dp(14), 0)
        })

        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(14))
            background = pill(Color.WHITE, 22)
            elevation = dp(8).toFloat()
        }
        statusView = label("准备好开始你的城市探索", 13f, Color.rgb(71, 104, 101))
        bottom.addView(statusView)
        recordButton = actionButton("开始记录", Color.rgb(43, 145, 135))
        bottom.addView(recordButton, LinearLayout.LayoutParams(-1, dp(50)).apply {
            topMargin = dp(12)
        })
        recordButton.setOnClickListener { onRecordClicked() }
        val actionRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val locateButton = actionButton("定位我", Color.rgb(233, 243, 241), Color.rgb(29, 99, 97))
        demoButton = actionButton("查看演示", Color.rgb(234, 238, 249), Color.rgb(72, 88, 146))
        actionRow.addView(locateButton, LinearLayout.LayoutParams(0, dp(46), 1f))
        actionRow.addView(demoButton, LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginStart = dp(10) })
        bottom.addView(actionRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        locateButton.setOnClickListener { centerOnUser() }
        demoButton.setOnClickListener { toggleDemo() }
        bottom.addView(label("轨迹仅保存在本机，卸载会丢失 · 地图需联网\n© OpenStreetMap contributors / OpenFreeMap", 10f, Color.rgb(116, 131, 131)).apply {
            setPadding(0, dp(10), 0, 0)
        })
        root.addView(bottom, FrameLayout.LayoutParams(-1, -2).apply {
            gravity = Gravity.BOTTOM
            setMargins(dp(14), 0, dp(14), dp(28))
        })

        mapView.getMapAsync { loaded ->
            map = loaded
            loaded.setStyle("https://tiles.openfreemap.org/styles/liberty") { style ->
                style.addSource(GeoJsonSource("walkpaint-real", geoJson(app.store.snapshot().realTracks)))
                style.addSource(GeoJsonSource("walkpaint-demo", geoJson(app.store.snapshot().tracks.filter { it.demo })))
                style.addLayer(LineLayer("walkpaint-real-layer", "walkpaint-real").withProperties(
                    PropertyFactory.lineColor(Color.rgb(27, 164, 150)),
                    PropertyFactory.lineWidth(6f),
                    PropertyFactory.lineOpacity(0.88f),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND)
                ))
                style.addLayer(LineLayer("walkpaint-demo-layer", "walkpaint-demo").withProperties(
                    PropertyFactory.lineColor(Color.rgb(124, 112, 211)),
                    PropertyFactory.lineWidth(6f),
                    PropertyFactory.lineOpacity(0.78f),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND)
                ))
                val first = app.store.snapshot().tracks.flatMap { it.points }.firstOrNull()
                if (first != null) loaded.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(first.lat, first.lon), 15.0))
                else loaded.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(20.0, 0.0), 2.5))
                refresh()
            }
        }
        handler.post(refreshTask)
    }

    private fun label(s: String, sp: Float, color: Int, bold: Boolean = false): TextView =
        TextView(this).apply {
            text = s
            textSize = sp
            setTextColor(color)
            if (bold) typeface = Typeface.create("sans-serif", Typeface.BOLD)
            gravity = Gravity.CENTER_VERTICAL
        }

    private fun actionButton(text: String, bg: Int, fg: Int = Color.WHITE): Button =
        Button(this).apply {
            this.text = text
            isAllCaps = false
            textSize = 15f
            setTextColor(fg)
            background = pill(bg, 14)
            elevation = 0f
            minHeight = dp(46)
        }

    private fun pill(color: Int, radius: Int): GradientDrawable =
        GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun onRecordClicked() {
        if (app.store.snapshot().activeId != null) {
            startService(Intent(this, RecordingService::class.java).setAction("STOP"))
            handler.postDelayed({ refresh() }, 350)
            return
        }
        if (app.store.loadError != null) {
            toast(app.store.loadError!!)
            return
        }
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 100)
            return
        }
        startRecording()
    }

    private fun startRecording() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
        }
        try {
            startForegroundService(Intent(this, RecordingService::class.java).setAction("START"))
            statusView.text = "正在启动记录，请保持定位开启"
        } catch (e: Exception) {
            toast("无法开始记录，请检查定位权限与系统设置")
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100) {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) startRecording()
            else toast("需要精确定位才能记录走过的路径")
        }
    }

    private fun toggleDemo() {
        try {
            val current = app.store.snapshot()
            val hasDemo = current.tracks.any { it.demo }
            if (hasDemo) {
                app.store.update { it.copy(tracks = it.tracks.filterNot { t -> t.demo }) }
                toast("演示路线已清除，真实足迹保留")
            } else {
                val baseLat = 31.2304
                val baseLon = 121.4737
                val now = System.currentTimeMillis()
                val steps = listOf(
                    0.0000 to 0.0000, 0.0009 to 0.0011, 0.0017 to 0.0021,
                    0.0025 to 0.0019, 0.0030 to 0.0007, 0.0022 to -0.0004,
                    0.0014 to -0.0010, 0.0005 to -0.0004, 0.0000 to 0.0000
                )
                val points = steps.mapIndexed { i, pair ->
                    TrackPoint(baseLat + pair.first, baseLon + pair.second, now + i * 10000L, 5f)
                }
                app.store.update { it.copy(tracks = it.tracks + Track("demo-preview", now, true, points)) }
                map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(baseLat + 0.0014, baseLon + 0.0005), 15.5))
                toast("紫色为演示足迹，不计入真实里程")
            }
            refresh()
        } catch (e: Exception) { toast("演示操作失败，检查存储空间") }
    }

    private fun centerOnUser() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 100)
            return
        }
        val lm = getSystemService(LocationManager::class.java)
        val location: Location? = try {
            listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
                .filter { lm.isProviderEnabled(it) }
                .mapNotNull { lm.getLastKnownLocation(it) }
                .maxByOrNull { it.time }
        } catch (e: SecurityException) { null }
        if (location != null) {
            map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(location.latitude, location.longitude), 16.0))
        } else toast("暂时没有定位结果，请到开阔处尝试开始记录")
    }

    private fun refresh() {
        val state = app.store.snapshot()
        distanceView.text = String.format(java.util.Locale.CHINA, "%.2f km", state.totalMeters / 1000)
        tripsView.text = state.realTracks.size.toString()
        recordButton.text = if (state.activeId != null) "结束并保存" else "开始记录"
        recordButton.background = pill(if (state.activeId != null) Color.rgb(189, 112, 100) else Color.rgb(43, 145, 135), 14)
        statusView.text = app.store.loadError ?: app.recordingMessage
        demoButton.text = if (state.tracks.any { it.demo }) "清除演示" else "查看演示"
        if (lastState != state) {
            lastState = state
            map?.style?.getSourceAs<GeoJsonSource>("walkpaint-real")?.setGeoJson(geoJson(state.realTracks))
            map?.style?.getSourceAs<GeoJsonSource>("walkpaint-demo")?.setGeoJson(geoJson(state.tracks.filter { it.demo }))
        }
    }

    private fun geoJson(tracks: List<Track>): String {
        val features = JSONArray()
        tracks.forEach { track ->
            track.points.groupBy { it.segment }.values.forEach { points ->
                if (points.size >= 2) {
                    val coordinates = JSONArray()
                    points.forEach { p -> coordinates.put(JSONArray().put(p.lon).put(p.lat)) }
                    val line = JSONObject().put("type", "LineString").put("coordinates", coordinates)
                    features.put(JSONObject().put("type", "Feature").put("properties", JSONObject()).put("geometry", line))
                }
            }
        }
        return JSONObject().put("type", "FeatureCollection").put("features", features).toString()
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    override fun onStart() { super.onStart(); mapView.onStart() }
    override fun onResume() { super.onResume(); mapView.onResume(); refresh() }
    override fun onPause() { mapView.onPause(); super.onPause() }
    override fun onStop() { mapView.onStop(); super.onStop() }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }
    override fun onSaveInstanceState(outState: Bundle) { mapView.onSaveInstanceState(outState); super.onSaveInstanceState(outState) }
    override fun onDestroy() { handler.removeCallbacksAndMessages(null); mapView.onDestroy(); super.onDestroy() }
}
