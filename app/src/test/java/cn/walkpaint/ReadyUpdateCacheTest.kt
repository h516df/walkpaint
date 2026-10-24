package cn.walkpaint

import java.io.File
import java.nio.file.Files
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ReadyUpdateCacheTest {
    private val candidate=UpdateCandidate(CurrentRelease("0.65",73,emptyList()),
        UpdateAsset(123,3,"a".repeat(64),"WalkPaint-v0.65-arm64.apk","https://example.invalid/not-retained"))
    @Test fun restoredRecordIsInstallOnlyWithoutNetworkDestination() {
        val text=ReadyUpdateRecord.encode(candidate)
        assertFalse(text.contains("http"));assertFalse(text.contains("123"))
        val restored=ReadyUpdateRecord.decode(text,72)
        assertEquals(73,restored.notes.code);assertEquals(candidate.asset.digest,restored.asset.digest)
        assertEquals(0L,restored.asset.id);assertNull(restored.asset.publicUrl)
    }
    @Test fun staleOrSameVersionIsRejected() {
        for(installed in listOf(73,74)) assertThrows(IllegalArgumentException::class.java){ReadyUpdateRecord.decode(ReadyUpdateRecord.encode(candidate),installed)}
    }
    @Test fun malformedMetadataNeverBecomesAnInstallCandidate() {
        val bad=listOf("version" to 2,"versionName" to "../0.65","versionCode" to 0,"size" to 0,
            "size" to PrivateUpdatePolicy.MAX_APK+1,"sha256" to "bad","name" to "../update.apk","name" to "Other-v0.65.apk")
        bad.forEach {(key,value)->val json=JSONObject(ReadyUpdateRecord.encode(candidate)).put(key,value)
            assertThrows(IllegalArgumentException::class.java){ReadyUpdateRecord.decode(json.toString(),72)}}
    }
    @Test fun oversizedRecordIsRejected() {
        assertThrows(IllegalArgumentException::class.java){ReadyUpdateRecord.decode(" ".repeat(8193),72)}
    }
    @Test fun cacheUsesOnlyFixedFileNamesAndRequiresApk() {
        val dir=Files.createTempDirectory("update-cache-test").toFile()
        try {
            val cache=ReadyUpdateCache(dir);assertNull(cache.read(72));cache.save(candidate)
            assertThrows(IllegalArgumentException::class.java){cache.read(72)}
            File(dir,"update.apk").writeBytes(byteArrayOf(1,2,3))
            assertEquals(73,cache.read(72)!!.notes.code)
            assertEquals(setOf("update.apk","ready.json"),dir.list()!!.toSet())
        } finally {dir.deleteRecursively()}
    }
    @Test fun freshSessionRemovesOnlyFixedOrphans() {
        val dir=Files.createTempDirectory("update-cache-test").toFile()
        try {
            val cache=ReadyUpdateCache(dir)
            listOf("pending.apk","ready.json.tmp","update.apk","unrelated.txt").forEach {File(dir,it).writeText("fixture")}
            cache.cleanupTransient();PendingUpdateCache(dir,72).cleanup();assertEquals(setOf("unrelated.txt"),dir.list()!!.toSet())
        } finally {dir.deleteRecursively()}
    }
    @Test fun freshSessionRetainsReadyPairForFullRevalidation() {
        val dir=Files.createTempDirectory("update-cache-test").toFile()
        try {
            val cache=ReadyUpdateCache(dir);cache.save(candidate);cache.apk.writeText("fixture")
            File(dir,"pending.apk").writeText("interrupted")
            cache.cleanupTransient();PendingUpdateCache(dir,72).cleanup();assertTrue(cache.apk.exists());assertTrue(cache.exists())
            assertFalse(File(dir,"pending.apk").exists())
        } finally {dir.deleteRecursively()}
    }
    @Test fun cacheRejectsApkSymlinkInsteadOfFollowingIt() {
        val dir=Files.createTempDirectory("update-cache-test").toFile()
        try {val cache=ReadyUpdateCache(dir);cache.save(candidate)
            val outside=File(dir,"other.apk");outside.writeText("not an APK")
            Files.createSymbolicLink(cache.apk.toPath(),outside.toPath())
            assertThrows(IllegalArgumentException::class.java){cache.read(72)}
        } finally {dir.deleteRecursively()}
    }
    @Test fun metadataDoesNotReplaceHashVerification() {
        val dir=Files.createTempDirectory("update-cache-test").toFile()
        try {val cache=ReadyUpdateCache(dir);cache.save(candidate);cache.apk.writeBytes(byteArrayOf(1,2,3))
            val restored=cache.read(72)!!
            assertThrows(UpdateFailure::class.java){PrivateUpdatePolicy.verifyHash(cache.apk,restored.asset)}
        } finally {dir.deleteRecursively()}
    }
    @Test fun continuationRequiresRecentExplicitClickAndMonotonicTime() {
        assertFalse(UpdateInstallContinuation.allowed(0,1000))
        assertFalse(UpdateInstallContinuation.allowed(1000,999))
        assertTrue(UpdateInstallContinuation.allowed(1000,601000))
        assertFalse(UpdateInstallContinuation.allowed(1000,601001))
    }
    @Test fun permissionResultWhileBusyThenPausedRestoreWaitsForResume() {
        assertFalse(UpdateInstallContinuation.ready(1000,2000,true,true,true,false))
        assertFalse(UpdateInstallContinuation.ready(1000,3000,false,true,false,true))
        assertTrue(UpdateInstallContinuation.ready(1000,4000,true,true,false,true))
        assertFalse(UpdateInstallContinuation.ready(0,4000,true,true,false,true))
        assertFalse(UpdateInstallContinuation.ready(1000,4000,true,false,false,true))
    }
    @Test fun downloadNoticeGivesSizeMeteredWarningAndExplicitAction() {
        val text=UpdateDownloadNotice.text(12*1024*1024)
        assertTrue(text.contains("12.0 MB"));assertTrue(text.contains("当前网络流量"));assertTrue(text.contains("点击"))
    }
}
