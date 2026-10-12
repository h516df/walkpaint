package cn.walkpaint

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class StandardUpdateAssetsTest {
    private fun asset(name:String)=JSONObject().put("id",1).put("name",name)
        .put("size",100).put("state","uploaded").put("digest","sha256:"+"a".repeat(64))
    private fun choose(vararg names:String)=UpdateCatalog.select(listOf(PublishedUpdate(
        CurrentRelease("0.38",40,listOf("修复" to listOf("记录恢复"))),names.map(::asset))),39,listOf("arm64-v8a")) as UpdateCatalogResult.Available
    @Test fun standardArm64ReleaseCanBeInstalled() {
        assertEquals("WalkPaint-v0.38-arm64.apk",choose("WalkPaint-v0.38-arm64.apk").candidate.asset.name)
    }
    @Test fun standardUniversalReleaseWorksOnArm64() {
        assertEquals("WalkPaint-v0.38-universal.apk",choose("WalkPaint-v0.38-universal.apk").candidate.asset.name)
    }
    @Test fun standardPackageIsPreferredOverLegacyAlias() {
        assertEquals("WalkPaint-v0.38-arm64.apk",choose("WalkPaint-v0.38-arm64-debug.apk","WalkPaint-v0.38-arm64.apk").candidate.asset.name)
    }
    @Test fun existingPreviewNamesRemainSupported() {
        assertEquals("WalkPaint-v0.38-arm64-debug.apk",choose("WalkPaint-v0.38-arm64-debug.apk").candidate.asset.name)
    }
}
