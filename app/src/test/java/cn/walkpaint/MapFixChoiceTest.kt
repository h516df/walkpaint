package cn.walkpaint
import org.junit.Assert.*
import org.junit.Test
class MapFixChoiceTest {
    @Test fun newNetworkCallbackCannotDisplaceFreshReliableRecordedGps() {
        assertEquals(0,MapFixChoice.choose(listOf(MapFixQuality("gps",20_000,5f),MapFixQuality("network",0,10f))))
    }
    @Test fun expiredOrPoorGpsAllowsFreshFallbackWithoutClaimingRecording() {
        assertEquals(1,MapFixChoice.choose(listOf(MapFixQuality("gps",46_000,5f),MapFixQuality("network",0,10f))))
        assertEquals(1,MapFixChoice.choose(listOf(MapFixQuality("gps",0,80f),MapFixQuality("network",0,10f))))
    }
    @Test fun rejectMissingAccuracyFutureAndStaleFixes() {
        assertNull(MapFixChoice.choose(listOf(MapFixQuality("gps",0,0f),MapFixQuality("gps",-1,5f),MapFixQuality("network",121_000,10f))))
    }
}
