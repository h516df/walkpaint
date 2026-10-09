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
internal class AmapMapController(private val context: Context) {
    private var currentView: MapView? = null
    private var map: AMap? = null
    private var currentMarker: Marker? = null
    private var latestPosition: Location? = null
    private var initialFocusPending = true
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

    fun create(state: TrackState, location: Location?, roads: RoadState): MapView {
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
        map?.uiSettings?.isRotateGesturesEnabled = false
        map?.uiSettings?.isTiltGesturesEnabled = false
        latestPosition = location
        initialFocusPending = true
        render(state,roads)
        return view
    }

    fun render(state: TrackState, roads: RoadState) {
        val a = map ?: return
        a.clear()
        currentMarker = null
        roads.roads.forEach { road ->
            a.addPolyline(PolylineOptions().addAll(road.points.map { toAmap(it.lat,it.lon) }).width(2.5f*density).color(UnexploredRoadArgb))
            roads.coverage.highlighted(road).forEach { line ->
                a.addPolyline(PolylineOptions().addAll(line.map { toAmap(it.lat,it.lon) }).width(7f*density).color(Color.argb(220,244,247,245)))
                a.addPolyline(PolylineOptions().addAll(line.map { toAmap(it.lat,it.lon) }).width(5f*density).color(ExploredRoadArgb))
            }
        }
        updateUser(latestPosition)
        if(initialFocusPending) {
            val current=latestPosition?.let { TrackPoint(it.latitude,it.longitude,it.time,it.accuracy) }
            RoadMapFocus.choose(state,current,roads)?.let { point ->
                a.moveCamera(CameraUpdateFactory.newLatLngZoom(toAmap(point.lat,point.lon),17f))
                initialFocusPending=false
            }
        }
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

    fun view(): MapView? = currentView

    fun release(view: MapView) {
        if (view !== currentView) return
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
