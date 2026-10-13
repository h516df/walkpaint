package cn.walkpaint

import org.json.JSONObject
import java.net.URI
import java.security.MessageDigest
import java.io.File
import java.util.zip.ZipFile

internal class UpdateFailure(val userMessage:String):Exception()
internal fun updateRequire(value:Boolean,message:String="更新资料不完整，请稍后重试") {
    if(!value) throw UpdateFailure(message)
}
internal data class UpdateAsset(val id:Long,val size:Long,val digest:String,val name:String)
internal data class UpdateCandidate(val notes:CurrentRelease,val asset:UpdateAsset)
internal object PrivateUpdatePolicy {
    const val REPO="h516df/walkpaint"
    const val REPO_ID=1410040906L
    const val API="https://api.github.com/repos/$REPO"
    const val VERIFY="https://github.com/login/device"
    const val MAX_APK=250L*1024*1024
    fun apiUrl(value:String):Boolean = runCatching {
        val u=URI(value)
        u.scheme=="https" && u.host=="api.github.com" && u.port==-1 && u.userInfo==null && u.fragment==null &&
            (u.rawPath=="/repos/$REPO" || u.rawPath.startsWith("/repos/$REPO/")) &&
            !u.rawPath.contains("%") && !u.rawPath.contains("..")
    }.getOrDefault(false)
    fun assetRedirect(value:String):Boolean = runCatching {
        val u=URI(value)
        u.scheme=="https" && u.host in setOf("release-assets.githubusercontent.com","objects.githubusercontent.com","github-releases.githubusercontent.com") &&
            u.port==-1 && u.userInfo==null && u.fragment==null
    }.getOrDefault(false)
    fun asset(json:JSONObject):UpdateAsset {
        val id=json.getLong("id");val size=json.getLong("size")
        val digest=json.getString("digest").removePrefix("sha256:")
        updateRequire(id>0 && size in 1..MAX_APK && digest.matches(Regex("[a-f0-9]{64}")))
        updateRequire(json.getString("state")=="uploaded")
        return UpdateAsset(id,size,digest,json.getString("name"))
    }
    fun apkName(version:String,abis:List<String>):String {
        val variant=if(abis.firstOrNull()=="arm64-v8a") "arm64" else "universal"
        return "WalkPaint-v$version-$variant-debug.apk"
    }
    fun apkNames(version:String,abis:List<String>):List<String> {
        val variants=if(abis.firstOrNull()=="arm64-v8a") listOf("arm64","universal") else listOf("universal")
        return variants.map { "WalkPaint-v$version-$it.apk" } +
            variants.map { "WalkPaint-v$version-$it-debug.apk" }
    }
    fun verifyHash(file:File,asset:UpdateAsset) {
        updateRequire(file.length()==asset.size,"安装包下载不完整，请重新下载")
        val sha=MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input -> val b=ByteArray(65536);while(true) { val n=input.read(b);if(n<0)break;sha.update(b,0,n) } }
        updateRequire(sha.digest().joinToString(""){"%02x".format(it)}==asset.digest,"安装包校验失败，请重新下载")
    }
    fun verifyArchive(packageName:String?,code:Long,minSdk:Int,signers:Set<String>,installedSigners:Set<String>,
                      expected:Int,installed:Int,deviceSdk:Int,apkAbis:Set<String>,deviceAbis:List<String>) {
        updateRequire(packageName=="cn.walkpaint","安装包不属于足迹涂色")
        updateRequire(code==expected.toLong() && code>installed,"不能安装相同或更旧的版本")
        updateRequire(minSdk in 1..deviceSdk,"新版暂不支持这台设备")
        updateRequire(signers.isNotEmpty() && signers==installedSigners,"安装包签名不可信，已停止更新")
        updateRequire(apkAbis.isNotEmpty() && deviceAbis.firstOrNull() in apkAbis,"安装包不适合这台设备")
    }
    fun nativeAbis(file:File):Set<String> = ZipFile(file).use { zip ->
        zip.entries().asSequence().map { it.name }.filter { it.matches(Regex("lib/[^/]+/[^/]+\\.so")) }
            .map { it.split('/')[1] }.toSet()
    }
    fun oauthError(error:String):String=when(error) {
        "access_denied" -> "已取消授权，仍可使用原版本"
        "expired_token","token_expired","bad_verification_code","incorrect_device_code" -> "授权码已过期，请重新授权"
        "device_flow_disabled","incorrect_client_credentials" -> "更新授权通道尚未开通，可先使用发布页"
        else -> "授权未完成，请重新授权"
    }
}
