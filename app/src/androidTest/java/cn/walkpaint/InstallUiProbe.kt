package cn.walkpaint

import android.app.Instrumentation
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/** Test APK only: stage a locally verified candidate, then open the real system installer.
 * No OAuth, no install session/adb install, no database or preferences writes.
 * The host must confirm the visible installer result and installed package hash separately.
 */
internal object InstallUiProbe {
    fun run(i:Instrumentation, sha:String):String {
        check(sha.matches(Regex("[a-f0-9]{64}")))
        val ctx=i.targetContext
        val source=File(ctx.getExternalFilesDir(null),"install-ui-candidate.apk")
        val candidate=File(ctx.cacheDir,"updates/install-ui-candidate.apk")
        check(source.isFile)
        candidate.parentFile!!.mkdirs();source.copyTo(candidate,overwrite=true)
        PrivateUpdatePolicy.verifyHash(candidate,UpdateAsset(1,candidate.length(),sha,"candidate.apk"))
        var nativeBridgeOnly=false
        try { UpdateApkVerifier.verify(ctx,candidate,50,"0.48") }
        catch(e:UpdateFailure) {
            // The guarded Google AVD runs this exact phone APK through ARM translation.
            // Production correctly selects universal for x86_64; do not weaken its ABI policy.
            check(e.userMessage=="安装包不适合这台设备" && android.os.Build.SUPPORTED_ABIS.first()=="x86_64"
                && "arm64-v8a" in android.os.Build.SUPPORTED_ABIS && android.os.Build.PRODUCT=="sdk_gphone64_x86_64")
            nativeBridgeOnly=true
        }
        val activity=i.startActivitySync(Intent(ctx,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val uri=FileProvider.getUriForFile(ctx,"${ctx.packageName}.updates",candidate)
        i.runOnMainSync { activity.startActivity(Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri,"application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
        // Instrumentation.finish kills the caller before InstallStart can stage its URI.
        // Keep the real caller alive until the host operates the system UI. A successful
        // package replacement ends this instrumentation; the host asserts installed hash.
        File(ctx.getExternalFilesDir(null),"install-ui-ready.txt").writeText(sha)
        val deadline=android.os.SystemClock.elapsedRealtime()+180_000
        while(android.os.SystemClock.elapsedRealtime()<deadline) android.os.SystemClock.sleep(250)
        return "INSTALL_UI_STAGE_TIMEOUT: exact SHA/version/original signer verified; nativeBridgeOnly=$nativeBridgeOnly; production FileProvider URI and ACTION_VIEW opened. Installation outcome is not yet asserted."
    }
}
