package cn.walkpaint
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.InputStream
import java.io.IOException
import java.security.MessageDigest

class UpdateFileDownloadTest {
    private val bytes=ByteArray(200000){(it%127).toByte()}
    private val asset=UpdateAsset(1,bytes.size.toLong(),MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)},"fixture.apk")
    private fun file()=File.createTempFile("download", ".apk")
    @Test fun interruptedTransferResumesAtActualPersistedByteCount() {
        val f=file();val offsets=mutableListOf<Long>()
        try {
            UpdateFileDownload.fetch(f,asset,{},{}) {offset,read->
                offsets+=offset
                if(offset==0L)read(bytes.copyOf(80000).inputStream())
                else read(bytes.copyOfRange(offset.toInt(),bytes.size).inputStream())
            }
            assertEquals(listOf(0L,80000L),offsets);assertArrayEquals(bytes,f.readBytes())
        } finally {f.delete()}
    }
    @Test fun resumedCorruptionFailsWithoutRetryAndCleansFile() {
        val f=file();var attempts=0
        assertThrows(UpdateFailure::class.java) {
            UpdateFileDownload.fetch(f,asset,{},{}) {_,read->attempts++;read(bytes.copyOf().also{it[0]=99}.inputStream())}
        }
        assertEquals(1,attempts);assertFalse(f.exists())
    }
    @Test fun reconnectAttemptsAreBoundedAndCancellationNeverRetries() {
        val f=file();var attempts=0
        assertThrows(IOException::class.java) {UpdateFileDownload.fetch(f,asset,{},{}) {_,_->attempts++;throw IOException("offline")}}
        assertEquals(5,attempts);assertFalse(f.exists())
        attempts=0
        assertThrows(InterruptedException::class.java) {UpdateFileDownload.fetch(f,asset,{throw InterruptedException()},{}) {_,_->attempts++}}
        assertEquals(0,attempts);assertFalse(f.exists())
    }
    @Test fun successfulDownloadUsesActualBytesAndFinishesAt100Percent() {
        val f=file();val progress=mutableListOf<Int>()
        try {
            UpdateFileDownload.copy(bytes.inputStream(),f,asset,{},{progress+=it})
            assertArrayEquals(bytes,f.readBytes());assertEquals(100,progress.last())
            assertTrue(progress.zipWithNext().all { it.first<it.second })
        } finally { f.delete() }
    }
    @Test fun truncatedDownloadCleansPartialFile() {
        val f=file()
        assertThrows(UpdateFailure::class.java) { UpdateFileDownload.copy(bytes.copyOf(80000).inputStream(),f,asset,{},{}) }
        assertFalse(f.exists())
    }
    @Test fun wrongHashCleansPartialFileEvenWhenSizeIsCorrect() {
        val f=file();val corrupt=bytes.copyOf().also { it[0]=99 }
        assertThrows(UpdateFailure::class.java) { UpdateFileDownload.copy(corrupt.inputStream(),f,asset,{},{}) }
        assertFalse(f.exists())
    }
    @Test fun oversizedStreamStopsAndCleansOutput() {
        val f=file()
        assertThrows(UpdateFailure::class.java) { UpdateFileDownload.copy((bytes+bytes).inputStream(),f,asset,{},{}) }
        assertFalse(f.exists())
    }
    @Test fun cancellationAfterFirstChunkCannotLeaveAnInstallablePartial() {
        val f=file();var calls=0
        assertThrows(InterruptedException::class.java) {
            UpdateFileDownload.copy(bytes.inputStream(),f,asset,{if(++calls>1)throw InterruptedException()},{})
        }
        assertFalse(f.exists())
    }
    @Test fun networkFailureCleansOutput() {
        val f=file();val broken=object:InputStream(){override fun read():Int=throw IOException("fixture disconnect")}
        assertThrows(IOException::class.java) { UpdateFileDownload.copy(broken,f,asset,{},{}) }
        assertFalse(f.exists())
    }
    @Test fun noSpaceStopsBeforeAnyDownload() {
        assertThrows(UpdateFailure::class.java) { UpdateFileDownload.requireSpace(0,asset.size) }
        assertThrows(UpdateFailure::class.java) { UpdateFileDownload.requireSpace(Long.MAX_VALUE,0) }
        UpdateFileDownload.requireSpace(asset.size+11*1024*1024,asset.size)
    }
}
