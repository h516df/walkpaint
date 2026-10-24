package cn.walkpaint
import java.io.File
import java.io.InputStream

internal object UpdateFileDownload {
    /** Durable reuse is opt-in and must be bound to trusted asset metadata by the caller. */
    fun fetch(partial:File,asset:UpdateAsset,checkCancelled:()->Unit,progress:(Int)->Unit,
              retainPartial:Boolean=false,
              open:(Long,(InputStream)->Unit)->Unit) {
        var succeeded=false
        var recoverable=false
        if(!retainPartial) partial.delete()
        try {
            updateRequire(!partial.exists() || (partial.isFile && partial.length() in 0..asset.size),"下载缓存无效，请重新下载")
            if(partial.exists()) progress((partial.length()*100/asset.size).toInt())
            repeat(5) { attempt ->
                checkCancelled()
                try {
                    val offset=if(partial.exists())partial.length() else 0L
                    if(offset<asset.size) open(offset) { input ->
                        java.io.FileOutputStream(partial,offset>0).use { out ->
                            val buffer=ByteArray(65536);var total=offset;var last=-1
                            while(true) {
                                checkCancelled();val count=input.read(buffer);if(count<0)break
                                total+=count;updateRequire(total<=asset.size,"下载内容异常，已停止更新")
                                out.write(buffer,0,count)
                                val percent=(total*100/asset.size).toInt()
                                if(percent!=last){last=percent;progress(percent)}
                            }
                        }
                    }
                    if(partial.length()<asset.size)throw java.io.EOFException("Incomplete download")
                    checkCancelled();PrivateUpdatePolicy.verifyHash(partial,asset);succeeded=true;return
                } catch(e:java.io.IOException) {
                    checkCancelled();if(attempt==4)throw e
                }
            }
        } catch(e:java.io.IOException) {recoverable=true;throw e}
        catch(e:InterruptedException) {recoverable=true;throw e}
        catch(e:UpdateUnavailable) {recoverable=true;throw e}
        finally {if(!succeeded && !(retainPartial && recoverable))partial.delete()}
    }
    fun requireSpace(available:Long,size:Long,existing:Long=0) {
        updateRequire(size in 1..PrivateUpdatePolicy.MAX_APK)
        updateRequire(existing in 0..size)
        updateRequire(available>size-existing+10L*1024*1024,"存储空间不足，请腾出空间后重试")
    }
    /** Partial output is never retained after a failed, interrupted or corrupt transfer. */
    fun copy(input:InputStream,partial:File,asset:UpdateAsset,checkCancelled:()->Unit,progress:(Int)->Unit) {
        var succeeded=false
        try {
            partial.outputStream().use { out ->
                val b=ByteArray(65536);var total=0L;var last=-1
                while(true) {
                    checkCancelled();val n=input.read(b);if(n<0)break;total+=n
                    updateRequire(total<=asset.size,"下载内容异常，已停止更新");out.write(b,0,n)
                    val percent=(total*100/asset.size).toInt()
                    if(percent!=last) { last=percent;progress(percent) }
                }
            }
            checkCancelled();PrivateUpdatePolicy.verifyHash(partial,asset);succeeded=true
        } finally { if(!succeeded) partial.delete() }
    }
}
