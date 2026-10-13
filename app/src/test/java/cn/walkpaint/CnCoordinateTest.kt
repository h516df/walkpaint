package cn.walkpaint

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The WGS84 / GCJ-02 boundary.
 *
 * Every local fixture this project had was in Paris, where this transform is the identity — which
 * is precisely why "the veil frame is in the wrong datum" survived a passing acceptance run. The
 * cases below pin the two halves that matter: the shift is real in China, and it is undone exactly
 * once when the engine reports a view back.
 */
class CnCoordinateTest {

    private fun metres(lat1: Double, lon1: Double, lat2: Double, lon2: Double) =
        Geo.meters(TrackPoint(lat1, lon1, 0L, 0f), TrackPoint(lat2, lon2, 0L, 0f))

    @Test fun theParisFixtureIsUntouchedWhichIsWhyItHidThis() {
        val out = CnCoordinate.wgsToGcj(48.8566, 2.3522)
        assertEquals(48.8566, out[0], 0.0)
        assertEquals(2.3522, out[1], 0.0)
        assertTrue(CnCoordinate.outOfChina(48.8566, 2.3522))
        val back = CnCoordinate.gcjToWgs(48.8566, 2.3522)
        assertEquals(48.8566, back[0], 0.0)
        assertEquals(2.3522, back[1], 0.0)
    }

    @Test fun insideChinaTheShiftIsHundredsOfMetresNotZero() {
        // Anonymous public landmark in Shanghai. The published offset there is a few hundred metres
        // in each direction; a missing conversion is exactly this error, on the map.
        val lat = 31.2304
        val lon = 121.4737
        assertFalse(CnCoordinate.outOfChina(lat, lon))
        val shifted = CnCoordinate.wgsToGcj(lat, lon)
        val distance = metres(lat, lon, shifted[0], shifted[1])
        assertTrue("expected a real datum shift, got $distance m", distance in 200.0..900.0)
    }

    @Test fun theInverseUndoesTheForwardTransformToWithinACentimetre() {
        val points = listOf(31.2304 to 121.4737, 39.9042 to 116.4074, 23.1291 to 113.2644,
            30.6586 to 104.0648, 45.8038 to 126.5349)
        for ((lat, lon) in points) {
            val shifted = CnCoordinate.wgsToGcj(lat, lon)
            val back = CnCoordinate.gcjToWgs(shifted[0], shifted[1])
            val error = metres(lat, lon, back[0], back[1])
            assertTrue("round trip moved %.4f m in %s".format(error, points), error < 0.01)
        }
    }

    @Test fun everyChineseCityTestedActuallyShifts() {
        for ((lat, lon) in listOf(39.9042 to 116.4074, 23.1291 to 113.2644, 30.6586 to 104.0648)) {
            val shifted = CnCoordinate.wgsToGcj(lat, lon)
            assertTrue("%.4f,%.4f did not move".format(lat, lon),
                metres(lat, lon, shifted[0], shifted[1]) > 100.0)
        }
    }

    @Test fun theShiftHelperAgreesWithTheTransform() {
        val lat = 31.2304
        val lon = 121.4737
        val shifted = CnCoordinate.wgsToGcj(lat, lon)
        assertEquals(metres(lat, lon, shifted[0], shifted[1]), CnCoordinate.shiftMetres(lat, lon), 0.001)
    }

    @Test fun aPointWellOutsideTheBoundaryIsLeftAlone() {
        // Just outside the covered area, so the frame and the track cannot disagree near the edge.
        for ((lat, lon) in listOf(0.5 to 100.0, 56.5 to 100.0, 30.0 to 71.0, 30.0 to 138.5)) {
            val out = CnCoordinate.wgsToGcj(lat, lon)
            assertEquals(lat, out[0], 0.0)
            assertEquals(lon, out[1], 0.0)
        }
    }
}
