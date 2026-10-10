package cn.walkpaint

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.security.MessageDigest

internal object UpdateApkVerifier {
    fun verify(app:Context,file:File,expected:Int,version:String) {
        val flags=if(Build.VERSION.SDK_INT>=28) PackageManager.GET_SIGNING_CERTIFICATES else @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES
        val pm=app.packageManager
        val archive=pm.getPackageArchiveInfo(file.absolutePath,flags) ?: throw UpdateFailure("安装包损坏，请重新下载")
        val installed=pm.getPackageInfo(app.packageName,flags)
        updateRequire(archive.versionName==version,"安装包与更新说明不一致")
        fun signers(info:android.content.pm.PackageInfo):Set<String> =
            (if(Build.VERSION.SDK_INT>=28) info.signingInfo?.apkContentsSigners else @Suppress("DEPRECATION") info.signatures)
                ?.map { MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).joinToString(""){b->"%02x".format(b)} }?.toSet().orEmpty()
        fun code(info:android.content.pm.PackageInfo)=if(Build.VERSION.SDK_INT>=28) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
        PrivateUpdatePolicy.verifyArchive(archive.packageName,code(archive),archive.applicationInfo?.minSdkVersion ?: 0,signers(archive),signers(installed),
            expected,code(installed).toInt(),Build.VERSION.SDK_INT,PrivateUpdatePolicy.nativeAbis(file),Build.SUPPORTED_ABIS.toList())
    }
}
