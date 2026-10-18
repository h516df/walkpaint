package cn.walkpaint

import java.net.URI
import java.security.MessageDigest

/** Official release-file fallback when anonymous API quotas or its network route fail. */
internal object PublicReleaseFiles {
    const val ROOT="https://github.com/h516df/walkpaint/releases"
    const val LATEST="$ROOT/latest"
    private val tagPattern=Regex("v[0-9]+\\.[0-9]+")
    fun tagFromRedirect(location:String):String {
        val tag=location.removePrefix("$ROOT/tag/")
        updateRequire(location=="$ROOT/tag/$tag" && tagPattern.matches(tag),"公开版本地址不可信，已停止更新")
        return tag
    }
    fun validAssetUrl(value:String):Boolean = runCatching {
        val u=URI(value)
        u.scheme=="https" && u.host=="github.com" && u.port==-1 && u.userInfo==null && u.query==null && u.fragment==null &&
            Regex("/h516df/walkpaint/releases/download/v[0-9]+\\.[0-9]+/WalkPaint-v[0-9]+\\.[0-9]+-[A-Za-z0-9.-]+").matches(u.rawPath)
    }.getOrDefault(false)
    fun checksums(text:String):Map<String,String> {
        val result=linkedMapOf<String,String>()
        text.lineSequence().filter { it.isNotBlank() }.forEach { line ->
            val match=Regex("([a-fA-F0-9]{64})  (WalkPaint-[A-Za-z0-9.-]+)").matchEntire(line.trim())
            updateRequire(match!=null,"更新校验清单格式异常")
            val name=match!!.groupValues[2]
            updateRequire(result.put(name,match.groupValues[1].lowercase())==null,"更新校验清单有重复文件")
        }
        return result
    }
    fun read(installed:Int,abis:List<String>,tag:String,bytes:(String)->ByteArray,size:(String)->Long):UpdateCatalogResult {
        updateRequire(tagPattern.matches(tag))
        val prefix="WalkPaint-$tag"
        val base="$ROOT/download/$tag"
        val sums=checksums(String(bytes("$base/$prefix-SHA256.txt"),Charsets.UTF_8))
        val name="$prefix-release-notes.json"
        val manifest=bytes("$base/$name")
        val hash=MessageDigest.getInstance("SHA-256").digest(manifest).joinToString(""){"%02x".format(it)}
        updateRequire(sums[name]==hash,"更新说明校验失败，请稍后重试")
        val notes=CurrentRelease.decode(String(manifest,Charsets.UTF_8))
        updateRequire(tag=="v${notes.version}","版本与更新说明不一致")
        if(notes.code<=installed) return UpdateCatalogResult.Current(notes)
        val apk=PrivateUpdatePolicy.apkNames(notes.version,abis).firstOrNull { it in sums }
            ?: throw UpdateFailure("新版暂未提供适合这台设备的安装包")
        val url="$base/$apk"
        val length=size(url)
        updateRequire(length in 1..PrivateUpdatePolicy.MAX_APK)
        return UpdateCatalogResult.Available(UpdateCandidate(notes,UpdateAsset(0,length,sums.getValue(apk),apk,url)))
    }
}
