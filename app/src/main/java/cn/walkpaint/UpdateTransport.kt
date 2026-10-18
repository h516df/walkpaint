package cn.walkpaint

import java.net.HttpURLConnection
import java.net.URI
import java.io.InputStream
import java.io.ByteArrayOutputStream

/** Public fixed destinations. No credentials are read or sent, including on redirects. */
internal class UpdateTransport {
    @Volatile private var connection:HttpURLConnection?=null
    @Volatile private var cancelled=false
    fun cancel() { cancelled=true;connection?.disconnect() }
    fun checkCancelled() { if(cancelled || Thread.currentThread().isInterrupted) throw InterruptedException() }
    private fun connect(url:String,binary:Boolean=false):HttpURLConnection {
        checkCancelled()
        val c=URI(url).toURL().openConnection() as HttpURLConnection
        connection=c;c.instanceFollowRedirects=false;c.connectTimeout=15000;c.readTimeout=45000
        c.setRequestProperty("User-Agent","WalkPaint-updater")
        c.setRequestProperty("Accept",if(binary) "application/octet-stream" else "application/vnd.github+json")
        c.setRequestProperty("X-GitHub-Api-Version","2026-03-10")
        return c
    }
    fun json(path:String):String {
        val url=PrivateUpdatePolicy.API+path;updateRequire(PrivateUpdatePolicy.apiUrl(url))
        val c=connect(url)
        try { status(c.responseCode);return c.inputStream.use { String(readBounded(it,2*1024*1024),Charsets.UTF_8) } }
        finally { c.disconnect();connection=null }
    }
    fun <T> asset(id:Long,read:(InputStream)->T):T {
        updateRequire(id>0)
        return stream("${PrivateUpdatePolicy.API}/releases/assets/$id",read)
    }
    fun <T> download(asset:UpdateAsset,offset:Long=0,read:(InputStream)->T):T {
        updateRequire(offset in 0 until asset.size)
        val url=asset.publicUrl?.also {updateRequire(PublicReleaseFiles.validAssetUrl(it))}
            ?: "${PrivateUpdatePolicy.API}/releases/assets/${asset.id.also {updateRequire(it>0)}}"
        return stream(url,read,offset,asset.size)
    }
    fun latestTag():String {
        val c=connect(PublicReleaseFiles.LATEST)
        try {
            val code=c.responseCode
            updateRequire(code in setOf(301,302,303,307,308),"公开发布页暂不可用，请稍后重试")
            return PublicReleaseFiles.tagFromRedirect(c.getHeaderField("Location").orEmpty())
        } finally { c.disconnect();connection=null }
    }
    fun publicBytes(url:String):ByteArray = publicStream(url) { readBounded(it,65536) }
    fun publicSize(url:String):Long = publicStream(url) {
        val size=connection?.contentLengthLong ?: -1
        updateRequire(size in 1..PrivateUpdatePolicy.MAX_APK,"未能核对安装包大小，请稍后重试")
        size
    }
    private fun <T> publicStream(url:String,read:(InputStream)->T):T {
        updateRequire(PublicReleaseFiles.validAssetUrl(url),"更新下载地址不可信，已停止下载")
        return stream(url,read)
    }
    private fun <T> stream(initial:String,read:(InputStream)->T,offset:Long=0,total:Long?=null):T {
        var url=initial
        repeat(4) {
            val c=connect(url,true)
            if(offset>0)c.setRequestProperty("Range","bytes=$offset-")
            try {
                val code=c.responseCode
                if(code in setOf(301,302,303,307,308)) {
                    val next=c.getHeaderField("Location").orEmpty()
                    updateRequire(PrivateUpdatePolicy.assetRedirect(next),"更新下载地址不可信，已停止下载")
                    url=next
                } else {
                    if(offset>0) updateRequire(code==206 && c.getHeaderField("Content-Range")=="bytes $offset-${total!!-1}/$total","服务器未能继续下载，请重新检查更新")
                    else status(code)
                    return c.inputStream.use(read)
                }
            } finally { c.disconnect();connection=null }
        }
        throw UpdateFailure("更新下载地址异常，请稍后重试")
    }
    fun readBounded(input:InputStream,max:Int):ByteArray {
        val out=ByteArrayOutputStream();val b=ByteArray(16384)
        while(true) { checkCancelled();val n=input.read(b);if(n<0)break;updateRequire(out.size()+n<=max);out.write(b,0,n) }
        return out.toByteArray()
    }
    private fun status(code:Int) { when(code) {
        200 -> return
        401,404 -> throw UpdateFailure("公开更新源暂不可用，请在浏览器核对发布页")
        403,429 -> throw UpdateUnavailable("更新请求暂时受限，请稍后重试或在浏览器查看")
        in 500..599 -> throw UpdateUnavailable("更新服务暂不可用，请稍后重试")
        else -> throw UpdateFailure("更新服务暂不可用，请稍后重试")
    } }
}
