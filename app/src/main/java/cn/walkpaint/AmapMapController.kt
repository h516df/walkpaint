package cn.walkpaint

import android.content.Context
import android.location.Location
import android.os.Bundle
import android.graphics.Color
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.CoordinateConverter
import com.amap.api.maps.MapsInitializer
import com.amap.api.maps.MapView
import com.amap.api.maps.model.BitmapDescriptorFactory
import com.amap.api.maps.model.CircleOptions
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.Marker
import com.amap.api.maps.model.MarkerOptions
import com.amap.api.maps.model.PolylineOptions

/**
 * Optional official AMap renderer. Native GPS data ALWAYS stays WGS84 in SQLite/backup.
 * Conversion is performed exactly once at the display boundary.
 * Do not call create() until the user has explicitly accepted the AMap SDK privacy notice.
 */
internal class AmapMapController(private val context: Context, private val onGesture:()->Unit,
                                 private val onReady:()->Unit) {
    private var currentView: MapView? = null
    private var map: AMap? = null
    private var currentMarker: Marker? = null
    private var latestPosition: Location? = null
    private var initialFocusPending = true
    private var exploredColor=ExploredRoadArgb
    private val paintedLines=mutableListOf<com.amap.api.maps.model.Polyline>()
    private val paintedSeeds=mutableListOf<com.amap.api.maps.model.Circle>()
    private var retainedCamera:com.amap.api.maps.model.CameraPosition?=null
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
        map?.setOnMapLoadedListener { onReady() }
        map?.uiSettings?.isTiltGesturesEnabled = false
        latestPosition = location
        initialFocusPending = true
        render(state,roads)
        retainedCamera?.let { map?.moveCamera(CameraUpdateFactory.newCameraPosition(it));initialFocusPending=false }
        return view
    }

    /**
     * A full rebuild clears the map and re-adds every overlay, so if one lands in the middle of a
     * pinch or a drag the gesture visibly hitches. A request raised during a gesture is held and
     * applied once the finger lifts.
     */
    fun render(state: TrackState, roads: RoadState) {
        if (gestureActive) { pendingState = state; pendingRoads = roads; return }
        renderNow(state, roads)
    }

    private fun endGesture() {
        gestureActive = false
        val state = pendingState
        val roads = pendingRoads
        pendingState = null
        pendingRoads = null
        if (state != null && roads != null) renderNow(state, roads)
    }

    private fun renderNow(state: TrackState, roads: RoadState) {
        val a = map ?: return
        a.clear()
        paintedLines.clear()
        paintedSeeds.clear()
        currentMarker = null
        val paths = RecordedTrail.paths(state.realTracks)
        // Every casing first, then every core: a later line's casing must not cover an earlier core.
        paths.filter { it.size > 1 }.forEach { line ->
            a.addPolyline(PolylineOptions().addAll(line.map { toAmap(it.lat, it.lon) })
                .width(9f * density).color(TrailOutlineArgb))
        }
        paths.forEach { line ->
            if(line.size==1) {
                val p=line.single()
                paintedSeeds+=a.addCircle(CircleOptions().center(toAmap(p.lat,p.lon)).radius(2.5)
                    .fillColor(exploredColor).strokeColor(TrailOutlineArgb).strokeWidth(1.5f*density))
            } else {
                paintedLines+=a.addPolyline(PolylineOptions().addAll(line.map { toAmap(it.lat,it.lon) })
                    .width(6f * density).color(exploredColor))
            }
        }
        updateUser(latestPosition)
        if(initialFocusPending) {
            val current=latestPosition?.let { TrackPoint(it.latitude,it.longitude,it.time,it.accuracy) }
            RoadMapFocus.choose(state,current,RoadState())?.let { point ->
                a.moveCamera(CameraUpdateFactory.newLatLngZoom(toAmap(point.lat,point.lon),17f))
                initialFocusPending=false
            }
        }
    }

    fun setExploredColor(color:Int) {
        exploredColor=color
        paintedLines.forEach { it.color=color }
        paintedSeeds.forEach { it.fillColor=color }
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

    fun focus(lat: Double, lon: Double, zoom: Double) {
        initialFocusPending=false
        map?.animateCamera(CameraUpdateFactory.newLatLngZoom(toAmap(lat, lon), zoom.toFloat()))
    }

    /**
     * [minZoom] is supplied only when the person explicitly asked to be located: it lifts a wide
     * camera to street level. Following a moving fix leaves the camera's own zoom alone.
     */
    fun followCamera(fix: Location, bearing: Float, animate: Boolean, minZoom: Float? = null) {
        val a=map ?: return
        val current=a.cameraPosition
        val zoom=when {
            minZoom!=null -> maxOf(current.zoom,minZoom)
            current.zoom<5f -> 18f
            else -> current.zoom
        }
        val target=com.amap.api.maps.model.CameraPosition.builder(current)
            .target(toAmap(fix.latitude,fix.longitude)).bearing(bearing)
            .zoom(zoom).build()
        val update=CameraUpdateFactory.newCameraPosition(target)
        if(animate) a.animateCamera(update,200,null) else a.moveCamera(update)
    }
    fun cancelFollowingAnimation() { map?.stopAnimation() }

    fun view(): MapView? = currentView

    fun release(view: MapView) {
        if (view !== currentView) return
        retainedCamera=map?.cameraPosition
        paintedLines.clear()
        paintedSeeds.clear()
        currentMarker = null
        map = null
        currentView = null
        view.onPause()
        view.onDestroy()
    }

    private fun toAmap(lat: Double, lon: Double): LatLng {
        val point = LatLng(lat, lon)
        if (!CoordinateConverter.isAMapDataAvailable(lat, lon)) return point
        return CoordinateConverter(context)
            .from(CoordinateConverter.CoordType.GPS)
            .coord(point)
            .convert() ?: point
    }
}
