package cn.walkpaint
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class UpdateInfoTest {
    private val text=File("src/main/assets/release-notes.json").readText()
    @Test fun notesMatchTheActualBuildAndRemainAvailableOffline() {
        val n=CurrentRelease.decode(text)
        assertEquals(BuildConfig.VERSION_NAME,n.version);assertEquals(BuildConfig.VERSION_CODE,n.code)
        assertEquals(listOf("新增","改进","修复","说明"),n.sections.map { it.first })
        val words=n.sections.flatMap { it.second }.joinToString()
        listOf("GPS_PROVIDER","schema","commit","SDK").forEach { assertFalse(words.contains(it)) }
        assertTrue(words.isNotBlank())
    }
    @Test fun patchReleaseMayHaveNoNewFeaturesButCannotBeEntirelyEmpty() {
        val notes=org.json.JSONObject(text).put("新增",org.json.JSONArray()).put("改进",org.json.JSONArray())
        assertTrue(CurrentRelease.decode(notes.toString()).sections.any {it.second.isNotEmpty()})
        for(key in listOf("新增","改进","修复","说明")) notes.put(key,org.json.JSONArray())
        assertThrows(IllegalArgumentException::class.java) {CurrentRelease.decode(notes.toString())}
    }
    @Test fun destinationIsFixedRepositoryWithNoCredentialOrDownloadParameters() {
        val uri=java.net.URI(UpdateDestination.URL)
        assertEquals("https",uri.scheme);assertEquals("github.com",uri.host)
        assertEquals("/h516df/walkpaint/releases",uri.path);assertNull(uri.userInfo);assertNull(uri.query)
    }
    @Test fun offlineAndBrowserFallbackNeverInventALatestVersion() {
        assertTrue(UpdateDestination.notice(false).contains("暂时无法检查"))
        assertTrue(UpdateDestination.notice(true).contains("无需登录"))
        listOf(UpdateDestination.UNKNOWN,UpdateDestination.BROWSER,UpdateDestination.OFFLINE,UpdateDestination.NO_BROWSER).forEach {
            assertFalse(it.contains("已是最新"));assertFalse(it.contains("发现新版本"))
        }
    }
    @Test fun malformedMetadataFailsBeforeClaimingAnInstalledVersion() {
        val malformed=org.json.JSONObject(text).put("versionName","https://evil").toString()
        assertThrows(IllegalArgumentException::class.java) { CurrentRelease.decode(malformed) }
    }
}
