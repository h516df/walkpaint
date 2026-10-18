package cn.walkpaint

import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest

class PublicReleaseFilesTest {
    private val notes="""{"versionName":"0.56","versionCode":64,"新增":["新增"],"改进":["改进"],"修复":["修复"],"说明":["说明"]}""".toByteArray()
    private val digest=MessageDigest.getInstance("SHA-256").digest(notes).joinToString(""){"%02x".format(it)}
    private fun bytes(url:String)=if(url.endsWith("SHA256.txt"))
        "$digest  WalkPaint-v0.56-release-notes.json\n${"a".repeat(64)}  WalkPaint-v0.56-arm64.apk\n".toByteArray() else notes
    @Test fun officialFilesCanReplaceRateLimitedApiWithoutLosingHashOrSize() {
        val result=PublicReleaseFiles.read(63,listOf("arm64-v8a"),"v0.56",::bytes){1234} as UpdateCatalogResult.Available
        assertEquals(1234L,result.candidate.asset.size)
        assertEquals("a".repeat(64),result.candidate.asset.digest)
        assertTrue(PublicReleaseFiles.validAssetUrl(result.candidate.asset.publicUrl!!))
    }
    @Test fun currentVersionDoesNotDownloadAnApk() {
        assertTrue(PublicReleaseFiles.read(64,listOf("arm64-v8a"),"v0.56",::bytes){error("no APK request")} is UpdateCatalogResult.Current)
    }
    @Test fun hostileLocationsAndDuplicateChecksumsAreRejected() {
        for(url in listOf("http://github.com/h516df/walkpaint/releases/tag/v0.56","https://github.com/other/app/releases/tag/v0.56", "${PublicReleaseFiles.ROOT}/tag/v0.56?redirect=evil"))
            assertThrows(UpdateFailure::class.java){PublicReleaseFiles.tagFromRedirect(url)}
        assertFalse(PublicReleaseFiles.validAssetUrl("${PublicReleaseFiles.ROOT}/download/v0.56/../../evil.apk"))
        assertFalse(PublicReleaseFiles.validAssetUrl("${PublicReleaseFiles.ROOT}/download/v0.56/WalkPaint-v0.56-arm64.apk?x=1"))
        val row="${"a".repeat(64)}  WalkPaint-v0.56-arm64.apk\n"
        assertThrows(UpdateFailure::class.java){PublicReleaseFiles.checksums(row+row)}
    }
    @Test fun corruptedNotesCannotOfferAnUpdate() {
        assertThrows(UpdateFailure::class.java){
            PublicReleaseFiles.read(63,listOf("arm64-v8a"),"v0.56",{url->if(url.endsWith("SHA256.txt")) bytes(url) else notes+32.toByte()}){1234}
        }
    }
}
