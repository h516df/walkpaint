package cn.walkpaint

/** Converts an SDK display coordinate back using that same SDK's forward conversion. */
internal object CoordinateInverse {
    fun toSource(lat: Double, lon: Double,
                 forward: (Double, Double) -> Pair<Double, Double>): Pair<Double, Double> {
        require(lat.isFinite() && lon.isFinite())
        var sourceLat = lat
        var sourceLon = lon
        repeat(8) {
            val converted = forward(sourceLat, sourceLon)
            val latError = lat - converted.first
            val lonError = lon - converted.second
            check(latError.isFinite() && lonError.isFinite())
            sourceLat += latError
            sourceLon += lonError
            if (kotlin.math.abs(latError) < 0.000001 && kotlin.math.abs(lonError) < 0.000001)
                return sourceLat to sourceLon
        }
        return sourceLat to sourceLon
    }
}
