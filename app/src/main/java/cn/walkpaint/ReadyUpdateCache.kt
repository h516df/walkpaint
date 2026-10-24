package cn.walkpaint

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import org.json.JSONObject

/** Only a previously verified, fixed-path APK can be restored; metadata alone never trusts it. */
internal class ReadyUpdateCache(private val directory:File) {
    val apk:File get()=File(directory,"update.apk")
    private val metadata get()=File(directory,"ready.json")
    fun save(candidate:UpdateCandidate) {
        val text=ReadyUpdateRecord.encode(candidate)
        require(directory.isDirectory || directory.mkdirs())
        val temporary=File(directory,"ready.json.tmp")
        try {
            FileOutputStream(temporary).use {it.write(text.toByteArray(Charsets.UTF_8));it.fd.sync()}
            Files.move(temporary.toPath(),metadata.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)
        } finally {temporary.delete()}
    }
    fun cleanupTransient() {
        File(directory,"ready.json.tmp").delete()
        if(!metadata.exists()) apk.delete()
    }
    fun exists()=metadata.exists()
    fun read(installed:Int):UpdateCandidate? {
        if(!metadata.exists()) return null
        require(metadata.isFile && metadata.length() in 1..8192)
        // Reject symlink destinations rather than following metadata or APK paths elsewhere.
        require(metadata.canonicalFile==File(directory.canonicalFile,"ready.json"))
        require(apk.isFile && apk.canonicalFile==File(directory.canonicalFile,"update.apk"))
        return ReadyUpdateRecord.decode(metadata.readText(),installed)
    }
    fun clear() {metadata.delete();File(directory,"ready.json.tmp").delete()}
}

internal object ReadyUpdateRecord {
    fun encode(candidate:UpdateCandidate):String = JSONObject()
        .put("version",1).put("versionName",candidate.notes.version).put("versionCode",candidate.notes.code)
        .put("size",candidate.asset.size).put("sha256",candidate.asset.digest).put("name",candidate.asset.name).toString()
    fun decode(text:String,installed:Int):UpdateCandidate {
        require(text.length<=8192)
        val json=JSONObject(text);require(json.getInt("version")==1)
        val version=json.getString("versionName");val code=json.getInt("versionCode")
        val size=json.getLong("size");val digest=json.getString("sha256");val name=json.getString("name")
        require(version.matches(Regex("[0-9]+\\.[0-9]+")) && code>installed)
        require(size in 1..PrivateUpdatePolicy.MAX_APK && digest.matches(Regex("[a-f0-9]{64}")))
        require(name in PrivateUpdatePolicy.apkNames(version,listOf("arm64-v8a")))
        // Restored candidates are install-only. Never persist a URL, token or asset ID for a future download.
        return UpdateCandidate(CurrentRelease(version,code,listOf("说明" to listOf("已恢复本机下载的安装包；安装前会再次核对完整性、版本和签名。"))),
            UpdateAsset(0,size,digest,name))
    }
}

internal object UpdateDownloadNotice {
    fun size(bytes:Long):String=String.format(java.util.Locale.ROOT,"%.1f MB",bytes/(1024.0*1024.0))
    fun text(bytes:Long)="安装包约 ${size(bytes)}，下载会使用当前网络流量，建议连接 Wi-Fi。只在你点击后下载。"
}

/** A Settings return is a continuation of a recent explicit Install click, never a startup action. */
internal object UpdateInstallContinuation {
    fun allowed(requestedAt:Long,now:Long)=requestedAt>0 && now>=requestedAt && now-requestedAt<=10*60*1000L
    fun ready(requestedAt:Long,now:Long,resumed:Boolean,permission:Boolean,busy:Boolean,hasFile:Boolean)=
        allowed(requestedAt,now) && resumed && permission && !busy && hasFile
}
