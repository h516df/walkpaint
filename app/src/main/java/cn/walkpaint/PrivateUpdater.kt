package cn.walkpaint

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.Future

internal data class PrivateUpdateState(val message:String="点击检查后才联网",val busy:Boolean=false,val authorized:Boolean=false,
    val userCode:String?=null,val progress:Int?=null,val candidate:UpdateCandidate?=null,val ready:File?=null)
internal class PrivateUpdater(context:Context,private val changed:(PrivateUpdateState)->Unit) {
    private val app=context.applicationContext
    private val store=UpdateTokenStore(app)
    private val handler=Handler(Looper.getMainLooper())
    private val worker=Executors.newSingleThreadExecutor()
    private var task:Future<*>?=null
    private var transport:UpdateTransport?=null
    @Volatile private var generation=0
    @Volatile private var closed=false
    @Volatile private var state=PrivateUpdateState(authorized=store.load()!=null)
    private val directory=File(app.cacheDir,"updates")
    init { directory.deleteRecursively() }
    fun snapshot()=state
    private fun publish(next:PrivateUpdateState,g:Int=generation) { handler.post {
        if(!closed && generation==g) { state=next;changed(next) }
    } }
    private fun launch(message:String,clearCandidate:Boolean=false,operation:(UpdateTransport,Int)->Unit) {
        if(task?.isDone==false) return
        val g=++generation;val http=UpdateTransport();transport=http
        state=state.copy(message=message,busy=true,userCode=null,progress=null,ready=null,
            candidate=if(clearCandidate)null else state.candidate);changed(state)
        task=worker.submit {
            try { operation(http,g) }
            catch(_:InterruptedException) { }
            catch(e:UpdateFailure) { publish(state.copy(message=e.userMessage,busy=false,userCode=null,progress=null,ready=null,authorized=store.load()!=null),g) }
            catch(_:Exception) { publish(state.copy(message="暂时无法完成更新，请检查网络后重试",busy=false,userCode=null,progress=null,ready=null,authorized=store.load()!=null),g) }
        }
    }
    fun check() {
        if(BuildConfig.UPDATE_CLIENT_ID.isEmpty()) { publish(state.copy(message="私有更新授权尚未开通，可先打开发布页核对版本"));return }
        launch("正在检查更新…",clearCandidate=true) { http,g ->
            var token=store.load()
            val now=System.currentTimeMillis()
            if(token!=null && token.expires<=now+60000) {
                if(token.refreshExpires<=now) { store.clear();token=null }
                else {
                    val response=http.oauth("oauth/access_token",mapOf("client_id" to BuildConfig.UPDATE_CLIENT_ID,
                        "grant_type" to "refresh_token","refresh_token" to token.refresh))
                    if(response.has("error")) { store.clear();token=null }
                    else { token=UpdateAuthorization.response(response,now);store.save(token) }
                }
            }
            if(token==null) { publish(state.copy(message="先授权更新访问，再检查私有安装包",busy=false,authorized=false),g);return@launch }
            inspect(http,token.access,g)
        }
    }
    fun authorize() {
        if(BuildConfig.UPDATE_CLIENT_ID.isEmpty()) { check();return }
        launch("正在取得授权码…",clearCandidate=true) { http,g ->
            val j=http.oauth("device/code",mapOf("client_id" to BuildConfig.UPDATE_CLIENT_ID,"scope" to "offline_access"))
            val grant=UpdateDeviceGrant.decode(j,android.os.SystemClock.elapsedRealtime());var interval=grant.interval
            publish(state.copy(message="在 GitHub 输入下方授权码，确认后返回；只读取本项目的更新",busy=true,userCode=grant.userCode),g)
            while(android.os.SystemClock.elapsedRealtime()<grant.expiresAt) {
                val remaining=grant.expiresAt-android.os.SystemClock.elapsedRealtime()
                Thread.sleep(minOf(interval*1000L,remaining.coerceAtLeast(1)));http.checkCancelled()
                if(android.os.SystemClock.elapsedRealtime()>=grant.expiresAt) break
                val response=http.oauth("oauth/access_token",mapOf("client_id" to BuildConfig.UPDATE_CLIENT_ID,"device_code" to grant.deviceCode,
                    "grant_type" to "urn:ietf:params:oauth:grant-type:device_code","repository_id" to PrivateUpdatePolicy.REPO_ID.toString()))
                when(val poll=UpdatePoll.decode(response,interval,System.currentTimeMillis())) {
                    is UpdatePoll.Pending -> interval=poll.nextInterval
                    is UpdatePoll.Approved -> { store.save(poll.authorization);inspect(http,poll.authorization.access,g);return@launch }
                }
            }
            throw UpdateFailure("授权码已过期，请重新授权")
        }
    }
    private fun inspect(http:UpdateTransport,access:String,g:Int) {
        val repo=JSONObject(http.json("",access))
        updateRequire(repo.getLong("id")==PrivateUpdatePolicy.REPO_ID && repo.getBoolean("private") && repo.getString("full_name")==PrivateUpdatePolicy.REPO)
        val releases=JSONArray(http.json("/releases?per_page=100",access))
        updateRequire(releases.length()<100,"发布资料太多，暂时无法完整核对，请打开发布页")
        val published=mutableListOf<PublishedUpdate>()
        for(i in 0 until releases.length()) {
            http.checkCancelled();val r=releases.getJSONObject(i)
            if(r.optBoolean("draft",true)) continue
            val tag=r.optString("tag_name");if(!tag.matches(Regex("v[0-9]+\\.[0-9]+")))continue
            // Old releases predate the manifest; absence is allowed, corruption is not hidden.
            val assets=r.getJSONArray("assets")
            val rows=(0 until assets.length()).map { assets.getJSONObject(it) }
            val metadata=UpdateCatalog.manifestAsset(tag,rows) ?: continue
            val a=PrivateUpdatePolicy.asset(metadata);updateRequire(a.size<=65536)
            val bytes=http.asset(a.id,access) { http.readBounded(it,65536) }
            updateRequire(bytes.size.toLong()==a.size && digest(bytes)==a.digest)
            val notes=CurrentRelease.decode(String(bytes,Charsets.UTF_8));updateRequire(tag=="v${notes.version}")
            published+=PublishedUpdate(notes,rows)
        }
        when(val result=UpdateCatalog.select(published,BuildConfig.VERSION_CODE,Build.SUPPORTED_ABIS.toList())) {
            is UpdateCatalogResult.Current -> publish(PrivateUpdateState(message="最新发布 ${result.latest.version}，当前版本无需更新",authorized=true),g)
            is UpdateCatalogResult.Available -> publish(PrivateUpdateState(message="发现新版本 ${result.candidate.notes.version}",authorized=true,candidate=result.candidate),g)
        }
    }
    fun download() {
        val candidate=state.candidate ?: return
        launch("正在下载新版…") { http,g ->
            val token=store.load() ?: throw UpdateFailure("请先重新授权")
            updateRequire(token.expires>System.currentTimeMillis(),"授权已过期，请先重新检查更新")
            directory.mkdirs();UpdateFileDownload.requireSpace(directory.usableSpace,candidate.asset.size)
            val partial=File(directory,"pending.apk");val file=File(directory,"update.apk")
            file.delete();partial.delete()
            try {
                http.asset(candidate.asset.id,token.access) { input ->
                    UpdateFileDownload.copy(input,partial,candidate.asset,{http.checkCancelled()}) { percent ->
                        publish(state.copy(message="正在下载新版…",busy=true,userCode=null,progress=percent),g)
                    }
                }
                http.checkCancelled()
                validateApk(partial,candidate.notes.code,candidate.notes.version)
                http.checkCancelled();updateRequire(partial.renameTo(file),"无法保存安装包，请重新下载")
                publish(state.copy(message="安装包已校验，可交给系统安装",busy=false,userCode=null,progress=null,ready=file),g)
            } finally { partial.delete() }
        }
    }
    fun validateApk(file:File,expected:Int,version:String) {
        UpdateApkVerifier.verify(app,file,expected,version)
    }
    fun prepareInstall(onReady:(File)->Unit) {
        val candidate=state.candidate ?: return
        val file=state.ready ?: return
        launch("正在核对安装包…") { http,g ->
            PrivateUpdatePolicy.verifyHash(file,candidate.asset);validateApk(file,candidate.notes.code,candidate.notes.version);http.checkCancelled()
            publish(state.copy(message="请在系统窗口确认安装",busy=false,ready=file),g)
            handler.post { if(!closed && generation==g) onReady(file) }
        }
    }
    fun cancel() { generation++;transport?.cancel();task?.cancel(true);task=null
        state=state.copy(message="已取消，仍可使用原版本",busy=false,userCode=null,progress=null,ready=null);changed(state)
    }
    fun logout() { cancel();launch("正在退出更新授权…",clearCandidate=true) { _,g ->
        directory.deleteRecursively();store.clear();publish(PrivateUpdateState(message="已退出更新授权，原足迹保留"),g)
    } }
    fun notice(message:String) { state=state.copy(message=message);changed(state) }
    fun close() { closed=true;generation++;transport?.cancel();task?.cancel(true);worker.shutdownNow();handler.removeCallbacksAndMessages(null) }
    private fun digest(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)}
}
