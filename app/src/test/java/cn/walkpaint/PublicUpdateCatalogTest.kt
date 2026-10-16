package cn.walkpaint

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest

class PublicUpdateCatalogTest {
    private val bytes=JSONObject().put("versionName","0.54").put("versionCode",60).apply {
        listOf("新增","改进","修复","说明").forEach { put(it,JSONArray().put("公开说明")) }
    }.toString().toByteArray()
    private fun digest(b:ByteArray)=MessageDigest.getInstance("SHA-256").digest(b).joinToString(""){"%02x".format(it)}
    private fun asset(name:String,id:Int,size:Long,hash:String)=JSONObject().put("name",name).put("id",id)
        .put("size",size).put("digest","sha256:$hash").put("state","uploaded")
    private fun repo()=JSONObject().put("id",PrivateUpdatePolicy.REPO_ID).put("full_name",PrivateUpdatePolicy.REPO).put("private",false)
    private fun latest()=JSONObject().put("tag_name","v0.54").put("draft",false).put("prerelease",false).put("assets",JSONArray()
        .put(asset("WalkPaint-v0.54-release-notes.json",1,bytes.size.toLong(),digest(bytes)))
        .put(asset("WalkPaint-v0.54-arm64.apk",2,100,"a".repeat(64))))
    private fun read(release:JSONObject=latest(),repository:JSONObject=repo(),body:ByteArray=bytes,installed:Int=59,
                     requests:MutableList<String> =mutableListOf())=PublicUpdateCatalog.read(installed,listOf("arm64-v8a"),{ path ->
        requests+=path
        when(path) { ""->repository.toString();"/releases/latest"->release.toString();else->error("unexpected request") }
    },{ id -> assertEquals(1L,id);requests+="manifest";body })

    @Test fun anonymousCheckReadsOnlyTheStableReleaseAndItsManifest() {
        val requests=mutableListOf<String>()
        val result=read(requests=requests) as UpdateCatalogResult.Available
        assertEquals(60,result.candidate.notes.code)
        assertEquals(listOf("","/releases/latest","manifest"),requests)
    }
    @Test fun installedVersionDoesNotOfferADowngrade() { assertTrue(read(installed=61) is UpdateCatalogResult.Current) }
    @Test fun privateOrDifferentSourcesFailBeforeReadingAssets() {
        val requests=mutableListOf<String>()
        assertThrows(UpdateFailure::class.java) { read(repository=repo().put("private",true),requests=requests) }
        assertEquals(listOf(""),requests)
    }
    @Test fun draftAndPreviewCannotBecomeStableUpdates() {
        for(field in listOf("draft","prerelease")) assertThrows(UpdateFailure::class.java) { read(latest().put(field,true)) }
    }
    @Test fun corruptManifestAndMismatchedTagAreRejected() {
        assertThrows(UpdateFailure::class.java) { read(body=bytes+byteArrayOf(1)) }
        assertThrows(UpdateFailure::class.java) { read(latest().put("tag_name","v0.55")) }
    }
    @Test fun missingManifestDoesNotInventAnUpToDateResult() {
        assertThrows(UpdateFailure::class.java) { read(latest().put("assets",JSONArray())) }
    }
}
