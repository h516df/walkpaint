package cn.walkpaint
import java.io.File
import java.io.InputStream

internal object UpdateFileDownload {
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
