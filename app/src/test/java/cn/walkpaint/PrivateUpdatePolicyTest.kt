package cn.walkpaint

import org.junit.Test
import org.junit.Assert.*
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipOutputStream
import java.util.zip.ZipEntry

class PrivateUpdatePolicyTest {
    private val cert=setOf("original-certificate")
    private fun archive(pkg:String?="cn.walkpaint",code:Long=16,minSdk:Int=26,signers:Set<String> =cert,
        expected:Int=16,installed:Int=15,abis:Set<String> =setOf("arm64-v8a"),device:List<String> =listOf("arm64-v8a")) =
        PrivateUpdatePolicy.verifyArchive(pkg,code,minSdk,signers,cert,expected,installed,36,abis,device)
    @Test fun originalPublisherHigherVersionAndMatchingPhoneAreAccepted() { archive() }
    @Test fun untrustedCertificateIsRejected() { assertThrows(UpdateFailure::class.java) { archive(signers=setOf("attacker")) } }
    @Test fun unsignedArchiveIsRejected() { assertThrows(UpdateFailure::class.java) { archive(signers=emptySet()) } }
    @Test fun addingASecondSignerCannotBypassTrust() { assertThrows(UpdateFailure::class.java) { archive(signers=cert+"attacker") } }
    @Test fun differentApplicationIsRejected() { assertThrows(UpdateFailure::class.java) { archive(pkg="other.application") } }
    @Test fun sameVersionDoesNotInstall() { assertThrows(UpdateFailure::class.java) { archive(code=15,expected=15) } }
    @Test fun downgradeDoesNotInstall() { assertThrows(UpdateFailure::class.java) { archive(code=14,expected=14) } }
    @Test fun manifestAndApkMustAgree() { assertThrows(UpdateFailure::class.java) { archive(code=17) } }
    @Test fun incompatibleAndroidIsRejected() { assertThrows(UpdateFailure::class.java) { archive(minSdk=37) } }
    @Test fun missingAndroidMetadataIsRejected() { assertThrows(UpdateFailure::class.java) { archive(minSdk=0) } }
    @Test fun phoneApkCannotPassAsAnEmulatorBuild() {
        assertThrows(UpdateFailure::class.java) { archive(device=listOf("x86_64","arm64-v8a")) }
    }
    @Test fun missingNativeCodeCannotPassAbiGuard() { assertThrows(UpdateFailure::class.java) { archive(abis=emptySet()) } }
    @Test fun apkVariantIsSelectedByPrimaryAbi() {
        assertEquals("WalkPaint-v0.14-arm64-debug.apk",PrivateUpdatePolicy.apkName("0.14",listOf("arm64-v8a")))
        assertEquals("WalkPaint-v0.14-universal-debug.apk",PrivateUpdatePolicy.apkName("0.14",listOf("x86_64","arm64-v8a")))
    }
    @Test fun onlyOurRepositoryApiCanReceiveAuthorization() {
        assertTrue(PrivateUpdatePolicy.apiUrl(PrivateUpdatePolicy.API+"/releases?per_page=100"))
        listOf("http://api.github.com/repos/h516df/walkpaint/releases","https://api.github.com/repos/other/walkpaint",
            "https://api.github.com.evil/repos/h516df/walkpaint","https://u@api.github.com/repos/h516df/walkpaint",
            "https://api.github.com:444/repos/h516df/walkpaint","https://api.github.com/repos/h516df/walkpaint/../other",
            "https://api.github.com/repos/h516df/walkpaint/%2e%2e/other","https://api.github.com/repos/h516df/walkpaint-extra",
            "https://api.github.com/repos/h516df/walkpaint#secret").forEach { assertFalse(it,PrivateUpdatePolicy.apiUrl(it)) }
    }
    @Test fun redirectMustStayOnGitHubAssetHosts() {
        assertTrue(PrivateUpdatePolicy.assetRedirect("https://release-assets.githubusercontent.com/asset?signature=fixture"))
        listOf("https://evil.example/apk","http://release-assets.githubusercontent.com/apk",
            "https://release-assets.githubusercontent.com.evil/apk","https://u@objects.githubusercontent.com/apk",
            "https://github.com/login","https://api.github.com/repos/other/asset").forEach { assertFalse(PrivateUpdatePolicy.assetRedirect(it)) }
    }
    private fun asset(size:Long=100,digest:String="sha256:"+"a".repeat(64),id:Long=1,state:String="uploaded")=
        JSONObject().put("id",id).put("size",size).put("digest",digest).put("state",state).put("name","test.apk")
    @Test fun invalidDigestSizeOrAssetIdentifierIsRejected() {
        listOf(asset(size=0),asset(size=PrivateUpdatePolicy.MAX_APK+1),asset(digest=""),asset(digest="sha256:"+"A".repeat(64)),
            asset(id=0),asset(state="new")).forEach { assertThrows(UpdateFailure::class.java) { PrivateUpdatePolicy.asset(it) } }
    }
    @Test fun exactDownloadedBytesAreAcceptedButSameSizeCorruptionAndTruncationFail() {
        val bytes="trusted binary fixture".toByteArray();val f=File.createTempFile("update-policy", ".apk")
        val a=UpdateAsset(1,bytes.size.toLong(),MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)},"test.apk")
        try {
            f.writeBytes(bytes);PrivateUpdatePolicy.verifyHash(f,a)
            f.writeBytes(bytes.copyOf(bytes.size-1));assertThrows(UpdateFailure::class.java) { PrivateUpdatePolicy.verifyHash(f,a) }
            f.writeBytes(bytes.map { (it+1).toByte() }.toByteArray());assertThrows(UpdateFailure::class.java) { PrivateUpdatePolicy.verifyHash(f,a) }
        } finally { f.delete() }
    }
    @Test fun nativeAbiComesFromLibrariesNotMisleadingFilename() {
        val f=File.createTempFile("update-policy", ".apk")
        try {
            ZipOutputStream(f.outputStream()).use { out ->
                listOf("lib/x86_64/map.so","assets/arm64-v8a/map.so","lib/arm64-v8a/not-a-library.txt").forEach { out.putNextEntry(ZipEntry(it));out.write(1);out.closeEntry() }
            }
            assertEquals(setOf("x86_64"),PrivateUpdatePolicy.nativeAbis(f))
        } finally { f.delete() }
    }
    @Test fun cancelledTransportFailsBeforeOpeningAnyNetworkConnection() {
        val http=UpdateTransport();http.cancel()
        assertThrows(InterruptedException::class.java) { http.json("/releases","fixture-not-a-token") }
    }
    @Test fun pendingAuthNeverPretendsToBeLatest() {
        listOf("access_denied","expired_token","device_flow_disabled","incorrect_client_credentials","unknown").forEach {
            val message=PrivateUpdatePolicy.oauthError(it);assertFalse(message.contains("已是最新"));assertFalse(message.contains(it))
        }
    }
    private fun token()=JSONObject().put("access_token","ghu_"+"a".repeat(40)).put("refresh_token","ghr_"+"b".repeat(40))
        .put("expires_in",28800).put("refresh_token_expires_in",15897600).put("scope","").put("token_type","bearer")
    @Test fun deviceGrantExpiresAndDoesNotAccidentallyPrintCredentials() {
        val grant=UpdateAuthorization.response(token(),1000)
        assertEquals(28801000,grant.expires);assertEquals(15897601000,grant.refreshExpires)
        assertFalse(grant.toString().contains("ghu_"));assertFalse(grant.toString().contains("ghr_"))
    }
    @Test fun classicBroadOrNonExpiringCredentialsAreNotAccepted() {
        listOf(token().put("access_token","ghp_"+"a".repeat(40)),token().put("scope","repo"),token().put("expires_in",0),
            token().put("refresh_token_expires_in",30000000),token().put("token_type","other")).forEach {
            assertThrows(UpdateFailure::class.java) { UpdateAuthorization.response(it,0) }
        }
    }
}
