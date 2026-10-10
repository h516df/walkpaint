package cn.walkpaint

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.io.InputStream
import java.io.ByteArrayOutputStream

/** Fixed destinations; redirects never inherit credentials. No response/error body is logged. */
internal class UpdateTransport {
    @Volatile private var connection:HttpURLConnection?=null
    @Volatile private var cancelled=false
    fun cancel() { cancelled=true;connection?.disconnect() }
    fun checkCancelled() { if(cancelled || Thread.currentThread().isInterrupted) throw InterruptedException() }
    private fun connect(url:String,token:String?,binary:Boolean=false):HttpURLConnection {
        checkCancelled()
        val c=URI(url).toURL().openConnection() as HttpURLConnection
        connection=c;c.instanceFollowRedirects=false;c.connectTimeout=15000;c.readTimeout=20000
        c.setRequestProperty("User-Agent","WalkPaint-updater")
        c.setRequestProperty("Accept",if(binary) "application/octet-stream" else "application/vnd.github+json")
        if(token!=null) { updateRequire(PrivateUpdatePolicy.apiUrl(url));c.setRequestProperty("Authorization","Bearer $token") }
        c.setRequestProperty("X-GitHub-Api-Version","2026-03-10")
        return c
    }
    fun oauth(path:String,values:Map<String,String>):JSONObject {
        updateRequire(path in setOf("device/code","oauth/access_token"))
        val c=connect("https://github.com/login/$path",null)
        try {
            c.requestMethod="POST";c.doOutput=true;c.setRequestProperty("Accept","application/json")
            c.setRequestProperty("Content-Type","application/x-www-form-urlencoded")
            val body=values.entries.joinToString("&") { (k,v)->"${URLEncoder.encode(k,"UTF-8")}=${URLEncoder.encode(v,"UTF-8")}" }
            c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            status(c.responseCode);return JSONObject(c.inputStream.use { String(readBounded(it,65536),Charsets.UTF_8) })
        } finally { c.disconnect();connection=null }
    }
    fun json(path:String,token:String):String {
        val url=PrivateUpdatePolicy.API+path;updateRequire(PrivateUpdatePolicy.apiUrl(url))
        val c=connect(url,token)
        try { status(c.responseCode);return c.inputStream.use { String(readBounded(it,2*1024*1024),Charsets.UTF_8) } }
        finally { c.disconnect();connection=null }
    }
    fun <T> asset(id:Long,token:String,read:(InputStream)->T):T {
        updateRequire(id>0)
        var url="${PrivateUpdatePolicy.API}/releases/assets/$id"
        var authorization:String?=token
        repeat(4) {
            val c=connect(url,authorization,true)
            try {
                val code=c.responseCode
                if(code in setOf(301,302,303,307,308)) {
                    val next=c.getHeaderField("Location").orEmpty()
                    updateRequire(PrivateUpdatePolicy.assetRedirect(next),"更新下载地址不可信，已停止下载")
                    url=next;authorization=null
                } else { status(code);return c.inputStream.use(read) }
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
        401,404 -> throw UpdateFailure("授权已失效或没有更新权限，请重新授权")
        403,429 -> throw UpdateFailure("暂时无法检查，请稍后重试；若权限被撤回，请重新授权")
        else -> throw UpdateFailure("更新服务暂不可用，请稍后重试")
    } }
}
