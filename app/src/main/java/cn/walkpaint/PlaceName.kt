package cn.walkpaint

import android.content.Context
import com.amap.api.services.geocoder.GeocodeResult
import com.amap.api.services.geocoder.GeocodeSearch
import com.amap.api.services.geocoder.RegeocodeAddress
import com.amap.api.services.geocoder.RegeocodeQuery
import com.amap.api.services.geocoder.RegeocodeResult
import com.amap.api.services.core.LatLonPoint

/**
 * Turns a coordinate into the name of the place it is in.
 *
 * The name is what makes the progress line readable: "原阳 · 解锁 27%" says something, "78 块" does
 * not. Every failure path returns null rather than an error, because a missing name must degrade to
 * a plain label and never to a blank line or a dialog: geocoding needs the network and the user's
 * AMap consent, and neither is guaranteed.
 */
internal object PlaceName {
    /** Township or street first, then district, city, province: the most local name available. */
    internal fun mostLocal(address: RegeocodeAddress?): String? =
        listOfNotNull(address?.township, address?.district, address?.city, address?.province)
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() }

    fun resolve(context: Context, lat: Double, lon: Double, onResult: (String?) -> Unit) {
        if (!lat.isFinite() || !lon.isFinite()) { onResult(null); return }
        runCatching {
            val search = GeocodeSearch(context)
            search.setOnGeocodeSearchListener(object : GeocodeSearch.OnGeocodeSearchListener {
                override fun onRegeocodeSearched(result: RegeocodeResult?, code: Int) {
                    onResult(if (code == 1000) mostLocal(result?.regeocodeAddress) else null)
                }
                override fun onGeocodeSearched(result: GeocodeResult?, code: Int) = Unit
            })
            search.getFromLocationAsyn(RegeocodeQuery(LatLonPoint(lat, lon), 200f, GeocodeSearch.AMAP))
        }.onFailure { onResult(null) }
    }
}
