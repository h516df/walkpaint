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
        assertTrue(words.contains("户外"));assertTrue(words.contains("浏览器"))
    }
    @Test fun destinationIsFixedPrivateRepositoryWithNoCredentialOrDownloadParameters() {
        val uri=java.net.URI(UpdateDestination.URL)
        assertEquals("https",uri.scheme);assertEquals("github.com",uri.host)
        assertEquals("/h516df/walkpaint/releases",uri.path);assertNull(uri.userInfo);assertNull(uri.query)
    }
    @Test fun offlineAndBrowserFallbackNeverInventALatestVersion() {
        assertTrue(UpdateDestination.notice(false).contains("暂时无法检查"))
        assertTrue(UpdateDestination.notice(true).contains("登录"))
        listOf(UpdateDestination.UNKNOWN,UpdateDestination.BROWSER,UpdateDestination.OFFLINE,UpdateDestination.NO_BROWSER).forEach {
            assertFalse(it.contains("已是最新"));assertFalse(it.contains("发现新版本"))
        }
    }
    @Test fun malformedMetadataFailsBeforeClaimingAnInstalledVersion() {
        assertThrows(IllegalArgumentException::class.java) { CurrentRelease.decode(text.replace("\"0.13\"","\"https://evil\"")) }
    }
}
