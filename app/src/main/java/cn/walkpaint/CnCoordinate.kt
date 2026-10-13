package cn.walkpaint

/**
 * The difference between the coordinate system the receiver produces and the one Chinese map SDKs
 * draw in.
 *
 * Everything this app stores and computes in is WGS84: the GNSS fix, the SQLite rows, the veil
 * frame, the distance measurements. AMap draws in GCJ-02, a deliberately offset datum, so a point
 * handed to it must be shifted — and, just as importantly, anything it *reports back* must be
 * shifted the other way before the rest of the app may use it.
 *
 * That second half was missing. `viewport()` returned AMap's visible-region corners as if they were
 * WGS84, so the veil frame was built a few hundred metres from the ground it was meant to cover,
 * and the frame's corners were then shifted *again* by the same `toAmap()` used to place the ground
 * overlay. The project's anonymous fixture is in Paris, where the shift does not apply at all, so
 * every local check passed while the phone was wrong. A fixture in China is what makes this visible.
 *
 * The forward transform is the published algorithm that the AMap SDK's own converter implements.
 * Its inverse has no closed form, so it is refined numerically here: fifteen rounds of "move by the
 * residual" converge to well under a centimetre, which is far below the accuracy of the fix being
 * drawn. Reference: https://developer.amap.com/api/android-sdk/guide/computing-equipment/coordinate-transformation
 */
internal object CnCoordinate {
    private const val A = 6378245.0
    private const val EE = 0.00669342162296594323
    private const val PI = Math.PI

    /** Roughly the area the offset applies to. Outside it both datum are the same thing. */
    fun outOfChina(lat: Double, lon: Double): Boolean =
        lon < 72.004 || lon > 137.8347 || lat < 0.8293 || lat > 55.8271

    private fun transformLat(x: Double, y: Double): Double {
        var ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * Math.sqrt(Math.abs(x))
        ret += (20.0 * Math.sin(6.0 * x * PI) + 20.0 * Math.sin(2.0 * x * PI)) * 2.0 / 3.0
        ret += (20.0 * Math.sin(y * PI) + 40.0 * Math.sin(y / 3.0 * PI)) * 2.0 / 3.0
        ret += (160.0 * Math.sin(y / 12.0 * PI) + 320.0 * Math.sin(y * PI / 30.0)) * 2.0 / 3.0
        return ret
    }

    private fun transformLon(x: Double, y: Double): Double {
        var ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * Math.sqrt(Math.abs(x))
        ret += (20.0 * Math.sin(6.0 * x * PI) + 20.0 * Math.sin(2.0 * x * PI)) * 2.0 / 3.0
        ret += (20.0 * Math.sin(x * PI) + 40.0 * Math.sin(x / 3.0 * PI)) * 2.0 / 3.0
        ret += (150.0 * Math.sin(x / 12.0 * PI) + 300.0 * Math.sin(x / 30.0 * PI)) * 2.0 / 3.0
        return ret
    }

    /** WGS84 to the datum AMap draws in. Identity outside China. */
    fun wgsToGcj(lat: Double, lon: Double): DoubleArray {
        if (outOfChina(lat, lon)) return doubleArrayOf(lat, lon)
        val dLat = transformLat(lon - 105.0, lat - 35.0)
        val dLon = transformLon(lon - 105.0, lat - 35.0)
        val radLat = lat / 180.0 * PI
        var magic = Math.sin(radLat)
        magic = 1 - EE * magic * magic
        val sqrtMagic = Math.sqrt(magic)
        val outLat = lat + (dLat * 180.0) / ((A * (1 - EE)) / (magic * sqrtMagic) * PI)
        val outLon = lon + (dLon * 180.0) / (A / sqrtMagic * Math.cos(radLat) * PI)
        return doubleArrayOf(outLat, outLon)
    }

    /**
     * The datum AMap reports back, to WGS84. Identity outside China.
     *
     * There is no closed form for this, so the forward transform is inverted by walking back the
     * residual: each round moves the estimate by the distance it is still off by. It converges
     * geometrically because the offset field is smooth over this area.
     */
    fun gcjToWgs(lat: Double, lon: Double): DoubleArray {
        if (outOfChina(lat, lon)) return doubleArrayOf(lat, lon)
        var wLat = lat
        var wLon = lon
        repeat(ROUNDS) {
            val forward = wgsToGcj(wLat, wLon)
            wLat += lat - forward[0]
            wLon += lon - forward[1]
        }
        return doubleArrayOf(wLat, wLon)
    }

    /** How far the datum shift moves a point, in metres. Used by the acceptance check. */
    fun shiftMetres(lat: Double, lon: Double): Double {
        val shifted = wgsToGcj(lat, lon)
        return Geo.meters(TrackPoint(lat, lon, 0L, 0f),
            TrackPoint(shifted[0], shifted[1], 0L, 0f))
    }

    /** Fifteen rounds put the residual under a centimetre; more would be work with nothing to show. */
    private const val ROUNDS = 15
}
