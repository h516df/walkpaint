package cn.walkpaint

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.location.Location
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.CoordinateConverter
import com.amap.api.maps.MapView
import com.amap.api.maps.MapsInitializer
import com.amap.api.maps.model.BitmapDescriptorFactory
import com.amap.api.maps.model.CameraPosition
import com.amap.api.maps.model.GroundOverlay
import com.amap.api.maps.model.GroundOverlayOptions
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.LatLngBounds
import com.amap.api.maps.model.Marker
import com.amap.api.maps.model.MarkerOptions
import com.amap.api.maps.model.Polyline
import com.amap.api.maps.model.PolylineOptions

/**
 * Optional official AMap renderer. Native GPS data ALWAYS stays WGS84 in SQLite/backup.
 * Conversion is performed exactly once at the display boundary.
 * Do not call create() until the user has explicitly accepted the AMap SDK privacy notice.
 */
internal class AmapMapController(private val context: Context, private val onGesture:()->Unit,
                                 private val onReady:()->Unit, private val onCamera:()->Unit,
                                 private val onCameraSettled:()->Unit) {
    private var currentView: MapView? = null
    private var map: AMap? = null
    private var currentMarker: Marker? = null
    private var latestPosition: Location? = null
    private var initialFocusPending = true
    private var exploredColor = ExploredRoadArgb
    /**
     * Overlays are kept and their geometry replaced, never cleared and rebuilt. A full rebuild on
     * every fix recreated every line the person had ever walked; on the phone that is what the
     * dragging and zooming stutter was.
     */
    private val casings = mutableListOf<Polyline>()
    private val cores = mutableListOf<Polyline>()
    private var ground: GroundOverlay? = null
    private var coverageGuard: android.graphics.drawable.Drawable? = null
    private val guardPath = android.graphics.Path()
    private val guardPaint = Paint().apply { color = Color.BLACK }
    private fun updateCoverageGuard() { coverageGuard?.invalidateSelf() }
    private fun installCoverageGuard(view: MapView) {
        val guard = object : android.graphics.drawable.Drawable() {
            override fun draw(canvas: Canvas) {
                val frame = groundFrame
                val projection = map?.projection
                guardPath.reset()
                guardPath.fillType = android.graphics.Path.FillType.EVEN_ODD
                guardPath.addRect(0f,0f,view.width.toFloat(),view.height.toFloat(),android.graphics.Path.Direction.CW)
                if(frame != null && projection != null) {
                    val corners = listOf(toAmap(frame.latMax,frame.lonMin),toAmap(frame.latMax,frame.lonMax),
                        toAmap(frame.latMin,frame.lonMax),toAmap(frame.latMin,frame.lonMin)).map { projection.toScreenLocation(it) }
                    // Degenerate projection must stay covered until the map is ready.
                    if(corners.distinctBy { it.x to it.y }.size == 4) {
                        guardPath.moveTo(corners[0].x.toFloat(),corners[0].y.toFloat())
                        corners.drop(1).forEach { guardPath.lineTo(it.x.toFloat(),it.y.toFloat()) }
                        guardPath.close()
                    }
                }
                canvas.drawPath(guardPath,guardPaint)
            }
            override fun setAlpha(alpha: Int) {}
            override fun setColorFilter(filter: android.graphics.ColorFilter?) {}
            @Suppress("DEPRECATION") override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
        }
        coverageGuard=guard
        view.addOnLayoutChangeListener { _,_,_,_,_,_,_,_,_ -> guard.setBounds(0,0,view.width,view.height);guard.invalidateSelf() }
        view.overlay.add(guard)
    }
    private var paintedRevision = -1
    /** Kept only so an exported field report can say what the phone's own engine ended up with. */
    private var groundPixels: String? = null
    private var groundFrame: PaintFrame? = null
    private var mapLoaded = false
    /** Display coordinates are converted once per point, not once per point per redraw. */
    private val converted = HashMap<Long, LatLng>()
    private var retainedCamera: CameraPosition?=null
    private val density = context.resources.displayMetrics.density
    private val blueDot by lazy {
        val bitmap = Bitmap.createBitmap((32*density).toInt(),(32*density).toInt(),Bitmap.Config.ARGB_8888)
        val canvas=Canvas(bitmap)
        val paint=Paint(Paint.ANTI_ALIAS_FLAG)
        val center=bitmap.width/2f
        paint.color=Color.argb(40,48,139,211); canvas.drawCircle(center,center,14*density,paint)
        paint.color=Color.WHITE;canvas.drawCircle(center,center,6*density,paint)
        paint.color=Color.rgb(48,139,211);canvas.drawCircle(center,center,4.5f*density,paint)
        BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    private var gestureActive = false
    private var pendingState: TrackState? = null
    private var pendingRoads: RoadState? = null
    private var pendingPaths: List<List<TrackPoint>> = emptyList()
    private var pendingFog: Bitmap? = null
    private var pendingFrame: PaintFrame? = null
    private var pendingRevision = -1

    fun create(state: TrackState, location: Location?, roads: RoadState, color:Int=ExploredRoadArgb): MapView {
        exploredColor=color
        check(BuildConfig.AMAP_KEY.isNotBlank()) { "高德地图 Key 尚未设置" }
        MapsInitializer.updatePrivacyShow(context.applicationContext, true, true)
        MapsInitializer.updatePrivacyAgree(context.applicationContext, true)
        MapsInitializer.setApiKey(BuildConfig.AMAP_KEY)
        val view = MapView(context)
        view.onCreate(null)
        currentView = view
        map = view.map
        groundFrame = null
        installCoverageGuard(view)
        casings.clear()
        cores.clear()
        ground = null
        paintedRevision = -1
        groundPixels = null
        mapLoaded = false
        converted.clear()
        map?.uiSettings?.isZoomControlsEnabled = false
        map?.uiSettings?.isCompassEnabled = false
        map?.uiSettings?.isRotateGesturesEnabled = true
        var downX=0f;var downY=0f
        val slop=android.view.ViewConfiguration.get(context).scaledTouchSlop
        val taps=android.view.GestureDetector(context,object:android.view.GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e:android.view.MotionEvent)=true
            override fun onDoubleTap(e:android.view.MotionEvent):Boolean { onGesture();return false }
        })
        map?.setOnMapTouchListener { event ->
            taps.onTouchEvent(event)
            when(event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> { downX=event.x;downY=event.y;gestureActive=true }
                android.view.MotionEvent.ACTION_POINTER_DOWN -> onGesture()
                android.view.MotionEvent.ACTION_MOVE -> if(kotlin.math.hypot(event.x-downX,event.y-downY)>slop) onGesture()
                android.view.MotionEvent.ACTION_UP,
                android.view.MotionEvent.ACTION_CANCEL -> endGesture()
            }
        }
        map?.setOnMapLoadedListener {
            mapLoaded = true
            // Camera moves issued before the SDK finishes loading may be replaced by its default
            // camera. Restore only after loading, or leave the initial focus pending for render.
            retainedCamera?.let {
                map?.moveCamera(CameraUpdateFactory.newCameraPosition(it))
                initialFocusPending = false
            }
            onReady()
        }
        // The veil follows the map: a pan or a zoom asks for a redraw for the new view.
        map?.setOnCameraChangeListener(object : AMap.OnCameraChangeListener {
            override fun onCameraChange(position: CameraPosition?) { updateCoverageGuard(); onCamera() }
            override fun onCameraChangeFinish(position: CameraPosition?) { updateCoverageGuard(); onCameraSettled() }
        })
        map?.uiSettings?.isTiltGesturesEnabled = false
        latestPosition = location
        initialFocusPending = true
        render(state,roads,emptyList(),null,null,-1)
        return view
    }

    /**
     * A replace of every overlay lands in the middle of a pinch or a drag as a visible hitch, so a
     * request raised during a gesture is held and applied once the finger lifts.
     */
    fun render(state: TrackState, roads: RoadState, paths: List<List<TrackPoint>>,
               fog: Bitmap?, fogFrame: PaintFrame?, fogRevision: Int) {
        if (gestureActive) {
            map?.let { syncGround(it, fog, fogFrame, fogRevision) }
            pendingState = state; pendingRoads = roads; pendingPaths = paths
            pendingFog = fog; pendingFrame = fogFrame; pendingRevision = fogRevision
            return
        }
        renderNow(state, roads, paths, fog, fogFrame, fogRevision)
    }

    private fun endGesture() {
        gestureActive = false
        val state = pendingState
        val roads = pendingRoads
        val paths = pendingPaths
        val fog = pendingFog
        val frame = pendingFrame
        val revision = pendingRevision
        pendingState = null
        pendingRoads = null
        pendingPaths = emptyList()
        pendingFog = null
        pendingFrame = null
        pendingRevision = -1
        if (state != null && roads != null) renderNow(state, roads, paths, fog, frame, revision)
    }

    private fun renderNow(state: TrackState, roads: RoadState, paths: List<List<TrackPoint>>,
                          fog: Bitmap?, fogFrame: PaintFrame?, fogRevision: Int) {
        val a = map ?: return
        // The veil paints the corridor; this line is the record of where the person walked, so it
        // has to survive on top of an opaque veil. The ground overlay carries zIndex 9, so anything
        // under that would be invisible.
        syncLines(a, casings, paths, 4f * density, TrailOutlineArgb, 10f)
        syncLines(a, cores, paths, 2.5f * density,
            (exploredColor and 0x00FFFFFF) or (0xB0 shl 24), 11f)
        syncGround(a, fog, fogFrame, fogRevision)
        updateUser(latestPosition)
        if(initialFocusPending && mapLoaded) {
            val current=latestPosition?.let { TrackPoint(it.latitude,it.longitude,it.time,it.accuracy) }
            RoadMapFocus.choose(state,current,RoadState())?.let { point ->
                a.moveCamera(CameraUpdateFactory.newLatLngZoom(toAmap(point.lat,point.lon),17f))
                initialFocusPending=false
            }
        }
    }

    /** Grows or shrinks the overlay list to match, then replaces points in place. */
    private fun syncLines(a: AMap, target: MutableList<Polyline>, paths: List<List<TrackPoint>>,
                          width: Float, color: Int, zIndex: Float) {
        while (target.size > paths.size) target.removeAt(target.size - 1).remove()
        while (target.size < paths.size) {
            target.add(a.addPolyline(PolylineOptions().width(width).color(color).zIndex(zIndex)))
        }
        paths.forEachIndexed { index, path ->
            val line = target[index]
            line.points = path.map { toAmap(it.lat, it.lon) }
            line.width = width
            line.color = color
        }
    }

    /**
     * The veil is one overlay for as long as it lives: handing AMap a new five megabyte image on
     * every fix is what made the map stutter, so it is only replaced when the veil really changed.
     */
    private fun syncGround(a: AMap, fog: Bitmap?, frame: PaintFrame?, revision: Int) {
        if (fog == null || frame == null || revision < 0 || revision == paintedRevision) return
        paintedRevision = revision
        val handoverStart = System.nanoTime()
        val descriptor = BitmapDescriptorFactory.fromBitmap(fog)
        val bounds = LatLngBounds(toAmap(frame.latMin, frame.lonMin), toAmap(frame.latMax, frame.lonMax))
        val existing = ground
        if (existing == null) {
            ground = a.addGroundOverlay(GroundOverlayOptions().image(descriptor)
                .positionFromBounds(bounds).zIndex(9f))
        } else {
            existing.setImage(descriptor)
            existing.setPositionFromBounds(bounds)
        }
        groundPixels = "${fog.width}x${fog.height}"
        groundFrame = frame
        updateCoverageGuard()
        if (BuildConfig.DEBUG) PerfTrace.upload(System.nanoTime() - handoverStart)
    }

    fun setExploredColor(color:Int) {
        exploredColor=color
        cores.forEach { it.color = (color and 0x00FFFFFF) or (0xB0 shl 24) }
    }

    fun updateUser(location: Location?) {
        latestPosition = location
        val a = map ?: return
        val fix = location ?: run { currentMarker?.remove(); currentMarker = null; return }
        val coordinate = toAmap(fix.latitude, fix.longitude)
        val marker = currentMarker
        if (marker == null) {
            currentMarker = a.addMarker(
                MarkerOptions().position(coordinate)
                    .icon(blueDot)
                    .anchor(0.5f, 0.5f)
            )
        } else {
            marker.position = coordinate
        }
    }

    fun zoomBy(delta: Int) {
        map?.animateCamera(CameraUpdateFactory.zoomBy(delta.toFloat()))
    }

    fun focus(lat: Double, lon: Double, zoom: Double) {
        initialFocusPending=false
        map?.animateCamera(CameraUpdateFactory.newLatLngZoom(toAmap(lat, lon), zoom.toFloat()))
    }

    /**
     * [minZoom] is supplied only when the person explicitly asked to be located: it lifts a wide
     * camera to street level. Following a moving fix leaves the camera's own zoom alone.
     */
    fun followCamera(fix: Location, bearing: Float, animate: Boolean, minZoom: Float? = null) {
        if (!mapLoaded || initialFocusPending) return
        val a=map ?: return
        val current=a.cameraPosition
        val zoom=when {
            minZoom!=null -> maxOf(current.zoom,minZoom)
            current.zoom<5f -> 18f
            else -> current.zoom
        }
        val target=CameraPosition.builder(current)
            .target(toAmap(fix.latitude,fix.longitude)).bearing(bearing)
            .zoom(zoom).build()
        val update=CameraUpdateFactory.newCameraPosition(target)
        if(animate) a.animateCamera(update,200,null) else a.moveCamera(update)
    }
    fun cancelFollowingAnimation() { map?.stopAnimation() }

    fun view(): MapView? = currentView

    /** Shows a whole walked area, used when the person asks to see all of their exploration. */
    fun focusBounds(latMin: Double, lonMin: Double, latMax: Double, lonMax: Double) {
        val bounds = com.amap.api.maps.model.LatLngBounds(
            toAmap(latMin, lonMin), toAmap(latMax, lonMax))
        map?.moveCamera(com.amap.api.maps.CameraUpdateFactory.newLatLngBounds(bounds, 80))
    }

    /** One line describing what the camera is showing, for the on-demand field export. */
    fun cameraSummary(): String {
        val camera = map?.cameraPosition ?: return "高德地图尚未就绪"
        val source = fromAmap(camera.target.latitude, camera.target.longitude)
        return String.format(java.util.Locale.ROOT, "缩放=%.2f 中心=%.5f,%.5f",
            camera.zoom, source.latitude, source.longitude)
    }

    fun cameraSource(): Triple<Double, Double, Float>? {
        val camera = map?.cameraPosition ?: return null
        val source = fromAmap(camera.target.latitude, camera.target.longitude)
        return Triple(source.latitude, source.longitude, camera.zoom)
    }

    /** What the AMap view is showing, so the veil can follow it. */
    fun viewport(): ViewBounds? {
        val bounds = map?.projection?.visibleRegion?.latLngBounds ?: return null
        val southwest = fromAmap(bounds.southwest.latitude, bounds.southwest.longitude)
        val northeast = fromAmap(bounds.northeast.latitude, bounds.northeast.longitude)
        return ViewBounds(southwest.latitude, southwest.longitude,
            northeast.latitude, northeast.longitude)
    }

    private fun fromAmap(lat: Double, lon: Double): LatLng {
        if (!CoordinateConverter.isAMapDataAvailable(lat, lon)) return LatLng(lat, lon)
        val source = CoordinateInverse.toSource(lat, lon) { sourceLat, sourceLon ->
            val display = toAmap(sourceLat, sourceLon)
            display.latitude to display.longitude
        }
        return LatLng(source.first, source.second)
    }

    /**
     * What the AMap side of the veil actually ended up doing, for the on-demand field export.
     *
     * Report the actual engine on both the phone and an ARM64 native-bridge AVD. This distinguishes
     * "the SDK never loaded", "the SDK loaded but the ground overlay was never
     * created", and "the overlay exists and the map is drawing the basemap over it" apart. Only the
     * third of those would need a different way of hiding the map, so guessing between them is what
     * produced a fix that could not be trusted.
     */
    fun describe(): String {
        if (map == null) return "高德=视图未创建"
        val pixels = groundPixels
        val layer = when {
            ground != null -> "有(位图=$pixels 图层版本=$paintedRevision 边界=" +
                (groundFrame?.let { String.format(java.util.Locale.ROOT, "%.5f,%.5f~%.5f,%.5f",
                    it.latMin, it.lonMin, it.latMax, it.lonMax) } ?: "未记录") + ")"
            pixels != null -> "没有（位图已交给高德 $pixels，但地面图层没建起来）"
            else -> "没有（还没有位图）"
        }
        return "高德=视图已建 地图加载完成=$mapLoaded 地面图层$layer " +
            "折线=描边${casings.size}条/芯${cores.size}条(zIndex 10/11，地面图层zIndex 9) " +
            "坐标换算缓存=${converted.size} 手势挂起=$gestureActive"
    }

    fun release(view: MapView) {
        if (view !== currentView) return
        retainedCamera=map?.cameraPosition
        casings.clear()
        cores.clear()
        ground = null
        groundPixels = null
        groundFrame = null
        mapLoaded = false
        paintedRevision = -1
        currentMarker = null
        map = null
        currentView = null
        view.onPause()
        view.onDestroy()
    }

    /**
     * WGS84 to the display datum, remembered per point. The conversion used to run twice for every
     * point of every trip on every redraw, which is a large share of the dragging stutter.
     */
    private fun toAmap(lat: Double, lon: Double): LatLng {
        val key = (Math.round(lat * 1_000_000.0) shl 32) xor
            (Math.round(lon * 1_000_000.0) and 0xFFFFFFFFL)
        converted[key]?.let { return it }
        val point = LatLng(lat, lon)
        val result = if (!CoordinateConverter.isAMapDataAvailable(lat, lon)) point
        else CoordinateConverter(context)
            .from(CoordinateConverter.CoordType.GPS)
            .coord(point)
            .convert() ?: point
        if (converted.size > 20_000) converted.clear()
        converted[key] = result
        return result
    }
}
