package cn.walkpaint

import android.content.Context
import com.amap.api.services.geocoder.GeocodeResult
import com.amap.api.services.geocoder.GeocodeSearch
import com.amap.api.services.geocoder.RegeocodeAddress
import com.amap.api.services.geocoder.RegeocodeQuery
import com.amap.api.services.geocoder.RegeocodeResult
import com.amap.api.services.core.LatLonPoint
import com.amap.api.services.core.ServiceSettings

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
        PlaceNameRequest.resolve(
            allowed = { BuildConfig.AMAP_KEY.isNotBlank() && context
                .getSharedPreferences("ui_onboarding", Context.MODE_PRIVATE)
                .getBoolean("amap_sdk_consent", false) },
            lat = lat, lon = lon,
            request = { sourceLat, sourceLon, reply ->
                val appContext = context.applicationContext
                ServiceSettings.updatePrivacyShow(appContext, true, true)
                ServiceSettings.updatePrivacyAgree(appContext, true)
                ServiceSettings.getInstance().setApiKey(BuildConfig.AMAP_KEY)
                val search = GeocodeSearch(appContext)
                search.setOnGeocodeSearchListener(object : GeocodeSearch.OnGeocodeSearchListener {
                    override fun onRegeocodeSearched(result: RegeocodeResult?, code: Int) {
                        reply(if (code == 1000) mostLocal(result?.regeocodeAddress) else null)
                    }
                    override fun onGeocodeSearched(result: GeocodeResult?, code: Int) = Unit
                })
                // SQLite and native location fixes remain WGS84; let the official service handle GPS.
                search.getFromLocationAsyn(RegeocodeQuery(LatLonPoint(sourceLat, sourceLon), 200f, GeocodeSearch.GPS))
            }, reply = onResult)
    }
}
