package cn.walkpaint

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class RoadDownloadTest {
    private class Reply(val status: Int = 200, val body: ByteArray = "{\"elements\":[]}".toByteArray(), val failRead: Boolean = false) : HttpURLConnection(URL(NearbyRoads.ENDPOINT)) {
        var closed = false
        val request = ByteArrayOutputStream()
        override fun connect() {}
        override fun disconnect() { closed = true }
        override fun usingProxy() = false
        override fun getOutputStream() = request
        override fun getResponseCode() = status
        override fun getInputStream(): java.io.InputStream {
            if(failRead) throw IOException("test offline")
            return ByteArrayInputStream(body)
        }
    }
    private val area=NearbyRoads.area(51.754,-1.255)
    @Test fun busyServerFailsWithoutAutomaticRetry() {
        val reply=Reply(status=429)
        var calls=0
        assertThrows(IllegalStateException::class.java) { RoadDownload { calls++; reply }.fetch(area) }
        assertEquals(1,calls)
        assertTrue(reply.closed)
        assertFalse(reply.instanceFollowRedirects)
    }
    @Test fun unexpectedHttpResponseFailsClosed() {
        val reply=Reply(status=302)
        assertThrows(IllegalStateException::class.java) { RoadDownload { reply }.fetch(area) }
        assertTrue(reply.closed)
    }
    @Test fun offlineReadClosesConnectionAndReturnsNoPack() {
        val reply=Reply(failRead=true)
        assertThrows(IOException::class.java) { RoadDownload { reply }.fetch(area) }
        assertTrue(reply.closed)
    }
    @Test fun oversizedResponseClosesConnectionAndReturnsNoPack() {
        val reply=Reply(body=ByteArray(RoadPackCodec.MAX_BYTES+1))
        assertThrows(IllegalArgumentException::class.java) { RoadDownload { reply }.fetch(area) }
        assertTrue(reply.closed)
    }
    @Test fun cancellationBeforeFetchSendsNothing() {
        var calls=0
        val job=RoadDownload { calls++; Reply() }
        job.cancel()
        assertThrows(IllegalStateException::class.java) { job.fetch(area) }
        assertEquals(0,calls)
    }
    @Test fun invalidDataCannotBecomeARoadPack() {
        val reply=Reply()
        assertThrows(IllegalArgumentException::class.java) { RoadDownload { reply }.fetch(area) }
        assertTrue(reply.closed)
        val request=java.net.URLDecoder.decode(reply.request.toString("UTF-8"),"UTF-8")
        assertTrue(request.startsWith("data=[out:json]"))
        assertFalse(request.contains("points"))
    }
}
