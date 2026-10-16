package cn.walkpaint

import android.app.Instrumentation
import android.content.Intent
import java.io.File
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject
import com.amap.api.maps.CoordinateConverter
import com.amap.api.maps.model.LatLng

/** Same-signature instrumentation can verify JNI on the non-debuggable release as well. */
internal object AmapRuntimeProbe {
    fun run(instrumentation: Instrumentation): String {
        val context = instrumentation.targetContext
        UiEntry.main(instrumentation)
        val report = File(context.getExternalFilesDir(null), "field-report.txt")
        var text = ""
        for (attempt in 0 until 8) {
            val requestedAt = System.currentTimeMillis()
            instrumentation.runOnMainSync {
                context.startActivity(Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra("cn.walkpaint.extra.EXPORT_FIELD_REPORT", true))
            }
            Thread.sleep(1500)
            if (report.isFile && report.lastModified() >= requestedAt - 100) {
                text = report.readText()
                if (text.contains("引擎判定=正在用高德") && text.contains("地图加载完成=true")) break
            }
        }
        check(text.contains("引擎判定=正在用高德")) { "Runtime is not AMap" }
        check(text.contains("地图加载完成=true")) { "AMap has not completed loading" }
        var roundTripMetres = 0.0
        instrumentation.runOnMainSync {
            for ((lat, lon) in listOf(39.9075 to 116.3913, 31.23 to 121.47, 48.8566 to 2.3522)) {
                fun forward(sourceLat: Double, sourceLon: Double): Pair<Double, Double> {
                    val converted = CoordinateConverter(context).from(CoordinateConverter.CoordType.GPS)
                        .coord(LatLng(sourceLat, sourceLon)).convert()
                    return converted.latitude to converted.longitude
                }
                val display = forward(lat, lon)
                val source = CoordinateInverse.toSource(display.first, display.second, ::forward)
                val metres = kotlin.math.hypot((lat - source.first) * 111194.9,
                    (lon - source.second) * 111194.9 * kotlin.math.cos(Math.toRadians(lat)))
                roundTripMetres = maxOf(roundTripMetres, metres)
            }
        }
        check(roundTripMetres < 1.0) { "SDK coordinate round trip exceeds one metre" }
        val apk = File(context.applicationInfo.sourceDir)
        val digest = MessageDigest.getInstance("SHA-256")
        apk.inputStream().use { source ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val read = source.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        val mappings = File("/proc/self/maps").readLines().mapNotNull { line ->
            val parts = line.trim().split(Regex("\\s+"), limit = 6)
            if (parts.size == 6 && parts[5] == apk.absolutePath) parts[2] else null
        }
        val direct = File("/proc/self/maps").readLines().filter { "libAMapSDK_MAP" in it }
        return "AMAP_RUNTIME_PASS: " + JSONObject()
            .put("apk_sha256", digest.digest().joinToString("") { "%02x".format(it) })
            .put("map_loaded", true)
            .put("coordinate_roundtrip_max_metres", roundTripMetres)
            .put("apk_mapping_offsets", JSONArray(mappings))
            .put("direct_amap_mappings", direct.size)
            .put("process", android.os.Process.myPid())
            .toString()
    }
}
