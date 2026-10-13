package cn.walkpaint

import android.app.Instrumentation
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import com.amap.api.services.geocoder.*
import com.amap.api.services.core.LatLonPoint
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Public landmarks only. Consent fixtures have their own preference file, never the App's. */
internal object PlaceNameProbe {
    fun run(i: Instrumentation): String {
        val base = i.targetContext
        val name = "place-probe-${UUID.randomUUID()}"
        val preferences = base.getSharedPreferences(name, Context.MODE_PRIVATE)
        var appContextReads = 0
        val isolated = object : ContextWrapper(base) {
            override fun getSharedPreferences(key: String, mode: Int): SharedPreferences =
                if (key == "ui_onboarding") preferences else super.getSharedPreferences(key, mode)
            override fun getApplicationContext(): Context {
                appContextReads++
                return base.applicationContext
            }
        }
        val results = JSONArray()
        try {
            var declinedReplies = 0
            PlaceName.resolve(isolated, 39.9075, 116.3913) { check(it == null); declinedReplies++ }
            check(declinedReplies == 1 && appContextReads == 0) { "Declined path initialized SDK" }
            check(preferences.edit().putBoolean("amap_sdk_consent", true).commit())
            for ((lat, lon, prefix) in listOf(Triple(39.9075, 116.3913, "110"), Triple(31.23, 121.47, "310"))) {
                var resolved: String? = null
                val resolvedLatch = CountDownLatch(1)
                PlaceName.resolve(isolated, lat, lon) { resolved = it; resolvedLatch.countDown() }
                check(resolvedLatch.await(45, TimeUnit.SECONDS) && !resolved.isNullOrBlank()) {
                    "Public place query did not return a name"
                }
                val query = RegeocodeQuery(LatLonPoint(lat, lon), 200f, GeocodeSearch.GPS)
                check(query.latLonType == GeocodeSearch.GPS)
                val latch = CountDownLatch(1)
                var code = 0
                var adCode: String? = null
                val search = GeocodeSearch(base.applicationContext)
                search.setOnGeocodeSearchListener(object : GeocodeSearch.OnGeocodeSearchListener {
                    override fun onRegeocodeSearched(result: RegeocodeResult?, value: Int) {
                        code = value; adCode = result?.regeocodeAddress?.adCode; latch.countDown()
                    }
                    override fun onGeocodeSearched(result: GeocodeResult?, value: Int) = Unit
                })
                search.getFromLocationAsyn(query)
                check(latch.await(45, TimeUnit.SECONDS) && code == 1000 && adCode?.startsWith(prefix) == true) {
                    "Public GPS point did not resolve to the expected city (code=$code)"
                }
                results.put(JSONObject().put("public_point", if(prefix == "110") "Beijing" else "Shanghai")
                    .put("name_present", true).put("expected_city", true).put("coordinate_type", "gps"))
            }
            val beforeWithdrawal = appContextReads
            check(preferences.edit().putBoolean("amap_sdk_consent", false).commit())
            PlaceName.resolve(isolated, 39.9075, 116.3913) { check(it == null) }
            check(appContextReads == beforeWithdrawal) { "SDK initialized after withdrawal" }
            return "PLACE_NAME_PASS: " + JSONObject().put("declined_sdk_initializations", 0)
                .put("withdrawn_sdk_initializations", 0).put("public_queries", results)
                .put("real_app_preferences_changed", false)
        } finally {
            base.deleteSharedPreferences(name)
        }
    }
}
