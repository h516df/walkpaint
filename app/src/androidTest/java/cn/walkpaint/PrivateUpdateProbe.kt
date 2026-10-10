package cn.walkpaint

import android.app.Instrumentation
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.util.UUID

/** Real Android boundaries, isolated test cache/preferences only. No live authorization or network. */
internal object PrivateUpdateProbe {
    fun run(i:Instrumentation):String {
        val ctx=i.targetContext
        val namespace="update-probe-"+UUID.randomUUID().toString()
        val directory=File(ctx.cacheDir,namespace).apply { mkdirs() }
        val store=UpdateTokenStore(ctx,namespace)
        val prefs=ctx.getSharedPreferences(namespace,Context.MODE_PRIVATE)
        try {
            val fixture=UpdateAuthorization("ghu_"+"x".repeat(40),"ghr_"+"y".repeat(40),1000,2000)
            store.save(fixture)
            val restored=store.load() ?: error("isolated grant did not decrypt")
            check(restored.access==fixture.access && restored.refresh==fixture.refresh && restored.expires==1000L)
            check(prefs.all.values.none { it.toString().contains("ghu_") || it.toString().contains("ghr_") })
            val before=prefs.getString("cipher",null)
            store.save(fixture);check(before!=prefs.getString("cipher",null)) { "encryption IV was reused" }
            prefs.edit().putString("cipher","tampered").commit()
            check(store.load()==null && prefs.all.isEmpty()) { "corrupt authorization did not fail closed" }
            store.save(fixture);store.clear();check(store.load()==null)
            val apk=File(directory,"pending.apk")
            File(ctx.applicationInfo.sourceDir).inputStream().use { input -> apk.outputStream().use { input.copyTo(it) } }
            val parsed=ctx.packageManager.getPackageArchiveInfo(apk.absolutePath,PackageManager.GET_SIGNING_CERTIFICATES)
                ?: error("actual archive did not parse")
            check(parsed.packageName=="cn.walkpaint" && parsed.longVersionCode==BuildConfig.VERSION_CODE.toLong())
            val installed=ctx.packageManager.getPackageInfo(ctx.packageName,PackageManager.GET_SIGNING_CERTIFICATES)
            check(parsed.signingInfo!!.apkContentsSigners.toList()==installed.signingInfo!!.apkContentsSigners.toList())
            check(Build.SUPPORTED_ABIS.first() in PrivateUpdatePolicy.nativeAbis(apk))
            try { UpdateApkVerifier.verify(ctx,apk,BuildConfig.VERSION_CODE,BuildConfig.VERSION_NAME);error("same-version update passed") }
            catch(e:UpdateFailure) { check(e.userMessage.contains("更旧")) }
            val bad=File(directory,"broken.apk").apply { writeText("broken fixture") }
            try { UpdateApkVerifier.verify(ctx,bad,BuildConfig.VERSION_CODE+1,"0.15");error("broken archive passed") }
            catch(e:UpdateFailure) { check(e.userMessage.contains("损坏")) }
            val uri=androidx.core.content.FileProvider.getUriForFile(ctx,"${ctx.packageName}.updates",File(ctx.cacheDir,"updates/update.apk"))
            check(uri.scheme=="content" && uri.authority=="cn.walkpaint.updates")
            try { androidx.core.content.FileProvider.getUriForFile(ctx,"${ctx.packageName}.updates",File(ctx.cacheDir,"footprints.db"));error("provider exposed unrelated cache") }
            catch(_:IllegalArgumentException) { }
            return "PRIVATE_UPDATE_ANDROID_BOUNDARIES_PASS: isolated Keystore roundtrip/tamper/logout; actual APK signatures/ABI; same-version/broken package rejection; narrow FileProvider. No authenticated download or installation claimed."
        } finally {
            store.clear();ctx.deleteSharedPreferences(namespace)
            check(directory.deleteRecursively()) { "isolated updater cache cleanup failed" }
        }
    }
}
