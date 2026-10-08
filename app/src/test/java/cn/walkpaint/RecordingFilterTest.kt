package cn.walkpaint

import org.junit.Assert.*
import org.junit.Test

class RecordingFilterTest {
    private val previous = TrackPoint(31.0, 121.0, 1000, 5f)
    @Test fun filtersHaveDistinctReasonsWithoutCoordinates() {
        assertEquals("STATIONARY_OR_SMALL_MOVE", Geo.rejectionReason(previous, previous.copy(time=5000)))
        assertEquals("NON_INCREASING_TIME", Geo.rejectionReason(previous, previous.copy(time=1000)))
        assertEquals("JUMP_OR_TOO_FAST", Geo.rejectionReason(previous, previous.copy(lat=32.0,time=5000)))
        assertEquals("UNRELIABLE_ACCURACY", Geo.rejectionReason(null,previous.copy(accuracy=Float.NaN)))
        assertEquals("INVALID_COORDINATE", Geo.rejectionReason(null,previous.copy(lat=Double.NaN)))
        assertEquals("UNRELIABLE_ACCURACY", Geo.rejectionReason(null,previous.copy(accuracy=0f)))
        assertNull(Geo.rejectionReason(previous,previous.copy(lat=31.0001,time=5000)))
    }
}
