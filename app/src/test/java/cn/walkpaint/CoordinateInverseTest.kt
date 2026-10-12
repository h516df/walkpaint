package cn.walkpaint

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CoordinateInverseTest {
    @Test fun identityOutsideTheSdkConversionArea() {
        val restored = CoordinateInverse.toSource(48.8566, 2.3522) { lat, lon -> lat to lon }
        assertEquals(48.8566, restored.first, 0.000001)
        assertEquals(2.3522, restored.second, 0.000001)
    }

    @Test fun displayViewportReturnsToStoredGpsCoordinates() {
        val restored = CoordinateInverse.toSource(39.901, 116.404) { lat, lon ->
            (lat + 0.001) to (lon + 0.004)
        }
        assertEquals(39.900, restored.first, 0.000001)
        assertEquals(116.400, restored.second, 0.000001)
    }

    @Test fun curvedForwardConversionRoundTripsBothCorners() {
        fun forward(lat: Double, lon: Double) =
            (lat + 0.001 * kotlin.math.sin(lon)) to (lon + 0.004 * kotlin.math.cos(lat))
        for ((lat, lon) in listOf(39.90 to 116.39, 39.91 to 116.40, 31.23 to 121.47)) {
            val display = forward(lat, lon)
            val restored = CoordinateInverse.toSource(display.first, display.second, ::forward)
            assertEquals(lat, restored.first, 0.000001)
            assertEquals(lon, restored.second, 0.000001)
        }
    }

    @Test fun invalidCoordinatesDoNotEnterTheSdk() {
        assertThrows(IllegalArgumentException::class.java) {
            CoordinateInverse.toSource(Double.NaN, 116.4) { lat, lon -> lat to lon }
        }
    }
}
