package cn.walkpaint
import java.io.File
import java.io.InputStream

internal object UpdateFileDownload {
    /** Resume only this attempt's verified destination and expected asset; never reuse old cache. */
    fun fetch(partial:File,asset:UpdateAsset,checkCancelled:()->Unit,progress:(Int)->Unit,
              open:(Long,(InputStream)->Unit)->Unit) {
        var succeeded=false
        partial.delete()
        try {
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
        } finally {if(!succeeded)partial.delete()}
    }
    fun requireSpace(available:Long,size:Long) {
        updateRequire(size in 1..PrivateUpdatePolicy.MAX_APK)
        updateRequire(available>size+10L*1024*1024,"存储空间不足，请腾出空间后重试")
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
