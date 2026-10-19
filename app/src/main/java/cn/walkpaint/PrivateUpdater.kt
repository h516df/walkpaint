package cn.walkpaint

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.Future

internal data class PrivateUpdateState(val message:String="点击检查后才联网",val busy:Boolean=false,val authorized:Boolean=false,
    val userCode:String?=null,val progress:Int?=null,val candidate:UpdateCandidate?=null,val ready:File?=null,
    val installVerified:Boolean=false)
internal class PrivateUpdater(context:Context,private var changed:((PrivateUpdateState)->Unit)?=null) {
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
    private val readyCache=ReadyUpdateCache(directory)
    init {
        readyCache.cleanupTransient()
        if(readyCache.exists()) launch("正在核对本机已下载的安装包…",clearCandidate=true) { _,g ->
            try {
                val candidate=readyCache.read(BuildConfig.VERSION_CODE)
                updateRequire(candidate!=null,"本机安装包资料已失效，请重新检查更新")
                val restored=candidate!!
                PrivateUpdatePolicy.verifyHash(readyCache.apk,restored.asset)
                validateApk(readyCache.apk,restored.notes.code,restored.notes.version)
                publish(PrivateUpdateState(message="已恢复校验通过的安装包，可继续安装",candidate=restored,ready=readyCache.apk,
                    authorized=store.load()!=null),g)
            } catch(e:InterruptedException) {throw e}
            catch(_:Exception) {
                readyCache.clear();readyCache.apk.delete()
                publish(PrivateUpdateState(message="本机安装包已失效，请重新检查更新；原版本仍可使用",authorized=store.load()!=null),g)
            }
        }
    }
    fun bind(listener:(PrivateUpdateState)->Unit) {changed=listener;listener(state)}
    fun unbind(listener:(PrivateUpdateState)->Unit) {if(changed===listener) changed=null}
    fun snapshot()=state
    private fun publish(next:PrivateUpdateState,g:Int=generation) { handler.post {
        if(!closed && generation==g) { state=next;changed?.invoke(next) }
    } }
    private fun launch(message:String,clearCandidate:Boolean=false,failureClearsCandidate:Boolean=false,operation:(UpdateTransport,Int)->Unit) {
        // Completion is published on main; a continuation may arrive before Future.isDone
        // flips. The serial executor can safely queue it after the completed operation.
        if(state.busy) return
        val g=++generation;val http=UpdateTransport();transport=http
        state=state.copy(message=message,busy=true,userCode=null,progress=null,ready=null,installVerified=false,
            candidate=if(clearCandidate)null else state.candidate);changed?.invoke(state)
        task=worker.submit {
            try { operation(http,g) }
            catch(_:InterruptedException) { }
            catch(e:UpdateFailure) { publish(state.copy(message=e.userMessage,busy=false,userCode=null,progress=null,ready=null,candidate=if(failureClearsCandidate)null else state.candidate,authorized=store.load()!=null),g) }
            catch(_:Exception) { publish(state.copy(message="暂时无法完成更新，请检查网络后重试",busy=false,userCode=null,progress=null,ready=null,candidate=if(failureClearsCandidate)null else state.candidate,authorized=store.load()!=null),g) }
        }
    }
    fun check() {
        if(BuildConfig.APPLICATION_ID!="cn.walkpaint") {
            publish(state.copy(message="这是独立开发版，请从源码重新构建更新"));return
        }
        launch("正在检查更新…",clearCandidate=true) { http,g ->
            inspect(http,g)
        }
    }
    private fun inspect(http:UpdateTransport,g:Int) {
        val result=try {
            PublicUpdateCatalog.read(BuildConfig.VERSION_CODE,Build.SUPPORTED_ABIS.toList(),http::json) { id ->
                http.asset(id) { http.readBounded(it,65536) }
            }
        } catch(e:Exception) {
            if(e !is UpdateUnavailable && e !is java.io.IOException) throw e
            http.checkCancelled()
            publish(state.copy(message="正在通过官方发布页检查…",busy=true),g)
            PublicReleaseFiles.read(BuildConfig.VERSION_CODE,Build.SUPPORTED_ABIS.toList(),http.latestTag(),http::publicBytes,http::publicSize)
        }
        val legacyAuthorization=store.load()!=null
        when(result) {
            is UpdateCatalogResult.Current -> publish(PrivateUpdateState(message="最新发布 ${result.latest.version}，当前版本无需更新",authorized=legacyAuthorization),g)
            is UpdateCatalogResult.Available -> publish(PrivateUpdateState(message="发现新版本 ${result.candidate.notes.version}",authorized=legacyAuthorization,candidate=result.candidate),g)
        }
    }
    fun download() {
        val candidate=state.candidate ?: return
        if(candidate.asset.id==0L && candidate.asset.publicUrl==null) {notice("请先重新检查更新，再下载安装包");return}
        launch("正在下载新版…") { http,g ->
            directory.mkdirs();UpdateFileDownload.requireSpace(directory.usableSpace,candidate.asset.size)
            val partial=File(directory,"pending.apk");val file=File(directory,"update.apk")
            readyCache.clear();file.delete();partial.delete()
            try {
                UpdateFileDownload.fetch(partial,candidate.asset,{http.checkCancelled()}, { percent ->
                        publish(state.copy(message="正在下载新版…",busy=true,userCode=null,progress=percent),g)
                    }, { offset,read -> http.download(candidate.asset,offset,read) })
                http.checkCancelled()
                validateApk(partial,candidate.notes.code,candidate.notes.version)
                http.checkCancelled();updateRequire(partial.renameTo(file),"无法保存安装包，请重新下载")
                readyCache.save(candidate)
                publish(state.copy(message="安装包已校验，可交给系统安装",busy=false,userCode=null,progress=null,ready=file),g)
            } finally { partial.delete() }
        }
    }
    fun validateApk(file:File,expected:Int,version:String) {
        UpdateApkVerifier.verify(app,file,expected,version)
    }
    fun prepareInstall() {
        val candidate=state.candidate ?: return
        val file=state.ready ?: return
        launch("正在核对安装包…",failureClearsCandidate=true) { http,g ->
            PrivateUpdatePolicy.verifyHash(file,candidate.asset);validateApk(file,candidate.notes.code,candidate.notes.version);http.checkCancelled()
            publish(state.copy(message="请在系统窗口确认安装",busy=false,ready=file,installVerified=true),g)
        }
    }
    fun takeVerifiedInstall():File? {
        if(!state.installVerified) return null
        val file=state.ready
        state=state.copy(installVerified=false);changed?.invoke(state)
        return file
    }
    fun cancel() { generation++;transport?.cancel();task?.cancel(true);task=null
        state=state.copy(message="已取消，仍可使用原版本",installVerified=false,busy=false,userCode=null,progress=null,ready=null);changed?.invoke(state)
    }
    fun logout() { cancel();launch("正在清除旧更新授权…",clearCandidate=true) { _,g ->
        directory.deleteRecursively();store.clear();publish(PrivateUpdateState(message="旧授权已清除，公开更新无需登录，原足迹保留"),g)
    } }
    fun notice(message:String) { state=state.copy(message=message);changed?.invoke(state) }
    fun close() { changed=null;closed=true;generation++;transport?.cancel();task?.cancel(true);worker.shutdownNow();handler.removeCallbacksAndMessages(null) }
}
