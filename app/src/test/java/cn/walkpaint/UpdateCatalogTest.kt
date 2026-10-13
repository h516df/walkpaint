package cn.walkpaint
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class UpdateCatalogTest {
    @Test fun existingVersionPrefixedManifestIsRecognizedWithoutChangingPublishedAssets() {
        val existing=JSONObject().put("name","WalkPaint-v0.13-release-notes.json")
        assertSame(existing,UpdateCatalog.manifestAsset("v0.13",listOf(existing)))
        val plain=JSONObject().put("name","release-notes.json")
        assertSame(plain,UpdateCatalog.manifestAsset("v0.14",listOf(plain)))
    }
    @Test fun anotherVersionsNotesAndArbitraryJsonAreNotAccepted() {
        assertNull(UpdateCatalog.manifestAsset("v0.14",listOf(JSONObject().put("name","WalkPaint-v0.13-release-notes.json"),
            JSONObject().put("name","arbitrary.json"))))
    }
    @Test fun duplicateManifestsFailInsteadOfPickingOneAtRandom() {
        assertThrows(UpdateFailure::class.java) { UpdateCatalog.manifestAsset("v0.14",listOf(JSONObject().put("name","release-notes.json"),
            JSONObject().put("name","WalkPaint-v0.14-release-notes.json"))) }
    }
    private fun notes(version:String,code:Int)=CurrentRelease(version,code,listOf("新增" to listOf("中文说明")))
    private fun apk(version:String,abi:String="universal")=JSONObject().put("id",1).put("name","WalkPaint-v$version-$abi-debug.apk")
        .put("size",100).put("state","uploaded").put("digest","sha256:"+"a".repeat(64))
    @Test fun oldPhoneOnlyReleaseDoesNotBlockANewerInstalledEmulatorPreview() {
        val result=UpdateCatalog.select(listOf(PublishedUpdate(notes("0.13",14),listOf(apk("0.13","arm64")))),15,listOf("x86_64"))
        assertTrue(result is UpdateCatalogResult.Current)
        assertEquals("0.13",(result as UpdateCatalogResult.Current).latest.version)
    }
    @Test fun aNewerUnsupportedReleaseIsNeverReportedAsLatestInstalled() {
        assertThrows(UpdateFailure::class.java) {
            UpdateCatalog.select(listOf(PublishedUpdate(notes("0.15",16),listOf(apk("0.15","arm64")))),15,listOf("x86_64"))
        }
    }
    @Test fun latestManifestIsSelectedBeforeCheckingPlatformNotOrderOfApiRows() {
        val rows=listOf(PublishedUpdate(notes("0.16",17),listOf(apk("0.16"))),PublishedUpdate(notes("0.15",16),listOf(apk("0.15","arm64"))))
        val result=UpdateCatalog.select(rows,15,listOf("x86_64")) as UpdateCatalogResult.Available
        assertEquals(17,result.candidate.notes.code)
    }
    @Test fun emptyCatalogDoesNotInventNoUpdate() {
        assertThrows(UpdateFailure::class.java) { UpdateCatalog.select(emptyList(),15,listOf("arm64-v8a")) }
    }
    @Test fun conflictingVersionCodesCannotChooseAnArbitraryApk() {
        assertThrows(UpdateFailure::class.java) { UpdateCatalog.select(listOf(PublishedUpdate(notes("0.14",15),emptyList()),
            PublishedUpdate(notes("0.15",15),emptyList())),14,listOf("arm64-v8a")) }
    }
}
