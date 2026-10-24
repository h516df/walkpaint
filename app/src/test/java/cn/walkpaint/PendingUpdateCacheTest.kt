package cn.walkpaint

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class PendingUpdateCacheTest {
    private val bytes=ByteArray(200000){(it%127).toByte()}
    private val candidate=UpdateCandidate(CurrentRelease("0.71",79,emptyList()),UpdateAsset(123,bytes.size.toLong(),
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)},
        "WalkPaint-v0.71-arm64.apk","https://example.invalid/never-persist"))
    private fun directory()=Files.createTempDirectory("pending-update-test").toFile()
    @Test fun failedDownloadAndNewSessionResumeOnlyAfterMatchingFreshCandidate() {
        val dir=directory()
        try {
            val cache=PendingUpdateCache(dir,78);cache.prepare(candidate)
            var attempts=0
            assertThrows(IOException::class.java) {
                UpdateFileDownload.fetch(cache.partial,candidate.asset,{},{},retainPartial=true) {offset,read->
                    if(attempts++==0) read(bytes.copyOf(80000).inputStream()) else throw IOException("synthetic offline")
                }
            }
            assertEquals(5,attempts);assertEquals(80000L,cache.bytes(candidate))
            val restored=PendingUpdateCache(dir,78);restored.cleanup()
            assertTrue(restored.hasPartial());assertEquals(80000L,restored.prepare(candidate))
            var resumedAt=-1L
            UpdateFileDownload.fetch(restored.partial,candidate.asset,{},{},retainPartial=true) {offset,read->
                resumedAt=offset;read(bytes.copyOfRange(offset.toInt(),bytes.size).inputStream())
            }
            assertEquals(80000L,resumedAt);assertArrayEquals(bytes,restored.partial.readBytes())
            assertFalse(File(dir,"pending.json").readText().contains("https"))
            assertFalse(org.json.JSONObject(File(dir,"pending.json").readText()).has("id"))
            assertFalse(File(dir,"update.apk").exists());assertFalse(File(dir,"ready.json").exists())
        } finally {dir.deleteRecursively()}
    }
    @Test fun differentHashVersionOrSizeCannotAppendToOldBytes() {
        val dir=directory()
        try {
            val variants=listOf(candidate.copy(asset=candidate.asset.copy(digest="a".repeat(64))),
                candidate.copy(notes=CurrentRelease("0.72",80,emptyList()),asset=candidate.asset.copy(name="WalkPaint-v0.72-arm64.apk")),
                candidate.copy(asset=candidate.asset.copy(size=candidate.asset.size+1)))
            for(next in variants) {
                val cache=PendingUpdateCache(dir,78);cache.prepare(candidate);cache.partial.writeBytes(bytes.copyOf(80000))
                assertEquals(0L,cache.bytes(next));assertEquals(0L,cache.prepare(next));assertFalse(cache.partial.exists())
            }
        } finally {dir.deleteRecursively()}
    }
    @Test fun sameSizeButCorruptedPartialCannotPassFinalHashOrBecomeReady() {
        val dir=directory()
        try {
            val cache=PendingUpdateCache(dir,78);cache.prepare(candidate)
            cache.partial.writeBytes(bytes.copyOf(80000).also{it[0]=99})
            assertThrows(UpdateFailure::class.java) {
                UpdateFileDownload.fetch(cache.partial,candidate.asset,{},{},retainPartial=true) {offset,read->
                    read(bytes.copyOfRange(offset.toInt(),bytes.size).inputStream())
                }
            }
            assertFalse(cache.partial.exists());assertEquals(0L,cache.bytes(candidate))
            assertFalse(File(dir,"update.apk").exists())
        } finally {dir.deleteRecursively()}
    }
    @Test fun missingCorruptOversizedAndAlreadyInstalledRecordsAreRemoved() {
        val dir=directory()
        try {
            val cache=PendingUpdateCache(dir,78)
            File(dir,"pending.apk").writeText("orphan");cache.cleanup();assertFalse(cache.partial.exists())
            cache.prepare(candidate);cache.partial.writeBytes(bytes.copyOf(20));File(dir,"pending.json").writeText("broken")
            cache.cleanup();assertFalse(cache.hasPartial());assertFalse(cache.partial.exists())
            cache.prepare(candidate);cache.partial.writeBytes(bytes+byteArrayOf(1));cache.cleanup();assertFalse(cache.hasPartial())
            cache.prepare(candidate);cache.partial.writeBytes(bytes.copyOf(20))
            PendingUpdateCache(dir,79).cleanup();assertFalse(cache.partial.exists())
        } finally {dir.deleteRecursively()}
    }
    @Test fun cancellationCleanupAndSymlinkNeverTouchUnrelatedFiles() {
        val dir=directory();val outside=File.createTempFile("synthetic-outside", ".bin").apply{writeText("keep")}
        try {
            val cache=PendingUpdateCache(dir,78);cache.prepare(candidate)
            Files.createSymbolicLink(cache.partial.toPath(),outside.toPath())
            cache.cleanup();assertFalse(cache.hasPartial());assertEquals("keep",outside.readText())
            cache.prepare(candidate);cache.partial.writeBytes(bytes.copyOf(20));cache.clear()
            assertFalse(cache.hasPartial());assertFalse(File(dir,"pending.json").exists())
            assertEquals("keep",outside.readText())
        } finally {dir.deleteRecursively();outside.delete()}
    }
    @Test fun insufficientSpaceAccountsForBytesAlreadyKept() {
        UpdateFileDownload.requireSpace(11*1024*1024,20*1024*1024,20*1024*1024)
        assertThrows(UpdateFailure::class.java) {UpdateFileDownload.requireSpace(11*1024*1024,20*1024*1024)}
    }
}
