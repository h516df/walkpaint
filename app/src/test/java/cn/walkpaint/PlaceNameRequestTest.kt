package cn.walkpaint

import org.junit.Assert.*
import org.junit.Test

class PlaceNameRequestTest {
    @Test fun decliningConsentNeverInitializesOrCallsTheService() {
        var calls = 0
        var replies = 0
        PlaceNameRequest.resolve({ false }, 39.9075, 116.3913,
            { _, _, _ -> calls++ }, { assertNull(it); replies++ })
        assertEquals(0, calls)
        assertEquals(1, replies)
    }

    @Test fun invalidOrOutOfRangeCoordinatesNeverEnterTheService() {
        for ((lat, lon) in listOf(Double.NaN to 116.39, 39.90 to Double.POSITIVE_INFINITY,
                                 91.0 to 116.39, 39.90 to -181.0)) {
            PlaceNameRequest.resolve({ true }, lat, lon,
                { _, _, _ -> fail("Invalid GPS sent to service") }, { assertNull(it) })
        }
    }

    @Test fun aPublicGpsPointIsPassedUnmodifiedAndReplyIsAccepted() {
        var answer: String? = null
        PlaceNameRequest.resolve({ true }, 31.23, 121.47, { lat, lon, reply ->
            assertEquals(31.23, lat, 0.0); assertEquals(121.47, lon, 0.0)
            reply("人民广场")
        }, { answer = it })
        assertEquals("人民广场", answer)
    }

    @Test fun withdrawingConsentDiscardsAnAlreadyPendingReply() {
        var consent = true
        var pending: ((String?) -> Unit)? = null
        var answer: String? = "old"
        PlaceNameRequest.resolve({ consent }, 39.9075, 116.3913,
            { _, _, reply -> pending = reply }, { answer = it })
        consent = false
        pending!!("东华门街道")
        assertNull(answer)
        PlaceNameRequest.resolve({ consent }, 39.9075, 116.3913,
            { _, _, _ -> fail("Service requested after withdrawal") }, { assertNull(it) })
    }

    @Test fun networkFailureDegradesAndDoesNotPreventTheNextQuery() {
        var replies = 0
        PlaceNameRequest.resolve({ true }, 39.9075, 116.3913,
            { _, _, _ -> throw IllegalStateException("offline") }, { assertNull(it); replies++ })
        PlaceNameRequest.resolve({ true }, 39.9075, 116.3913,
            { _, _, reply -> reply("东华门街道") }, { assertNotNull(it); replies++ })
        assertEquals(2, replies)
    }
}
