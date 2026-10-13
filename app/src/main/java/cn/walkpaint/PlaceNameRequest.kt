package cn.walkpaint

/** The network boundary: check live consent before starting and again before accepting a reply. */
internal object PlaceNameRequest {
    fun resolve(allowed: () -> Boolean, lat: Double, lon: Double,
                request: (Double, Double, (String?) -> Unit) -> Unit, reply: (String?) -> Unit) {
        if (!allowed() || !lat.isFinite() || !lon.isFinite() || lat !in -90.0..90.0 || lon !in -180.0..180.0) {
            reply(null)
            return
        }
        try {
            request(lat, lon) { name -> reply(if (allowed()) name else null) }
        } catch (_: Exception) {
            reply(null)
        }
    }
}
