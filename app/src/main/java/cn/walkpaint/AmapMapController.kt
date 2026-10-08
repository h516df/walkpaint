package cn.walkpaint

import android.content.Context
import android.location.Location
import android.os.Bundle
import android.graphics.Color
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
    private val accent = Color.rgb(18, 145, 134)
    private val demo = Color.rgb(133, 104, 197)

    fun create(state: TrackState, location: Location?): MapView {
        check(BuildConfig.AMAP_KEY.isNotBlank()) { "高德地图 Key 尚未设置" }
        MapsInitializer.updatePrivacyShow(context.applicationContext, true, true)
        MapsInitializer.updatePrivacyAgree(context.applicationContext, true)
        MapsInitializer.setApiKey(BuildConfig.AMAP_KEY)
        val view = MapView(context)
        view.onCreate(null)
        currentView = view
        map = view.map
        map?.uiSettings?.isZoomControlsEnabled = false
        latestPosition = location
        render(state)
        val p = location?.let { toAmap(it.latitude, it.longitude) }
            ?: state.realTracks.lastOrNull()?.points?.lastOrNull()?.let { toAmap(it.lat, it.lon) }
        if (p != null) map?.moveCamera(CameraUpdateFactory.newLatLngZoom(p, 16f))
        return view
    }

    fun render(state: TrackState) {
        val a = map ?: return
        a.clear()
        currentMarker = null
        fun draw(tracks: List<Track>, color: Int) {
            tracks.forEach { trip ->
                trip.points.groupBy { it.segment }.values.forEach { segment ->
                    if (segment.isEmpty()) return@forEach
                    val converted = segment.map { toAmap(it.lat, it.lon) }
                    a.addCircle(
                        CircleOptions().center(converted.first()).radius(6.0)
                            .strokeColor(Color.WHITE).strokeWidth(2f).fillColor(color)
                    )
                    if (converted.size >= 2) {
                        a.addPolyline(PolylineOptions().addAll(converted).width(13f).color(color))
                    }
                }
            }
        }
        draw(state.realTracks, accent)
        draw(state.tracks.filter { it.demo }, demo)
        updateUser(latestPosition)
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
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
                    .anchor(0.5f, 0.85f).title("我的位置")
            )
        } else {
            marker.position = coordinate
        }
    }

    fun focus(lat: Double, lon: Double, zoom: Double) {
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
