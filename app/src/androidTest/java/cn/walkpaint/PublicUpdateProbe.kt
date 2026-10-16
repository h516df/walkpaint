package cn.walkpaint

import android.app.Instrumentation
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Dedicated AVD only. Never exports credentials, coordinates, or personal rows. */
internal object PublicUpdateProbe {
    fun run(i:Instrumentation,mode:String):String {
        check(Build.PRODUCT=="sdk_gphone64_x86_64" && "arm64-v8a" in Build.SUPPORTED_ABIS)
        val ctx=i.targetContext
        if(mode=="state") {
            val ui=ctx.getSharedPreferences("ui_appearance",Context.MODE_PRIVATE)
            return "PUBLIC_UPDATE_STATE_PASS: "+JSONObject()
                .put("automatic",AmbientTracking.enabled(ctx)).put("mode",ui.getInt("mode",0))
                .put("skin",ui.getString("skin",null)).put("color",RoadAppearance(ctx).color)
                .put("legacy_authorization_present",UpdateTokenStore(ctx).load()!=null)
        }
        if(mode=="legacy-stage") {
            val store=UpdateTokenStore(ctx)
            check(store.load()==null) {"Refuse to replace an existing authorization"}
            val now=System.currentTimeMillis()
            store.save(UpdateAuthorization("ghu_"+"a".repeat(40),"ghr_"+"b".repeat(40),now+60000,now+120000))
            return "PUBLIC_UPDATE_LEGACY_STAGE_PASS: synthetic local-only authorization; no account or network access"
        }
        check(mode in setOf("catalog","download"))
        val http=UpdateTransport()
        try {
            // The AVD runs the phone APK with ARM translation. Explicit fixture ABI selection
            // does not alter Build.SUPPORTED_ABIS or production selection/installation policy.
            val result=PublicUpdateCatalog.read(if(mode=="download") BuildConfig.VERSION_CODE-1 else BuildConfig.VERSION_CODE,
                listOf("arm64-v8a"),http::json) { id -> http.asset(id) { http.readBounded(it,65536) } }
            if(mode=="catalog") {
                val version=when(result) {
                    is UpdateCatalogResult.Current->result.latest.version
                    is UpdateCatalogResult.Available->result.candidate.notes.version
                }
                return "PUBLIC_UPDATE_ANONYMOUS_CATALOG_PASS: latest=$version; account_authorization_sent=false; fixture_abi=arm64-v8a"
            }
            val candidate=(result as UpdateCatalogResult.Available).candidate
            val directory=File(ctx.cacheDir,"public-update-probe-"+UUID.randomUUID()).apply {mkdirs()}
            try {
                val file=File(directory,"download.apk")
                http.asset(candidate.asset.id) { input ->
                    UpdateFileDownload.copy(input,file,candidate.asset,{http.checkCancelled()}) { }
                }
                PrivateUpdatePolicy.verifyHash(file,candidate.asset)
                val pm=ctx.packageManager
                val archive=pm.getPackageArchiveInfo(file.path,PackageManager.GET_SIGNING_CERTIFICATES)!!
                val current=pm.getPackageInfo(ctx.packageName,PackageManager.GET_SIGNING_CERTIFICATES)
                check(archive.packageName==ctx.packageName && archive.versionName==candidate.notes.version)
                check(archive.longVersionCode==candidate.notes.code.toLong())
                check(archive.signingInfo!!.apkContentsSigners.toList()==current.signingInfo!!.apkContentsSigners.toList())
                return "PUBLIC_UPDATE_ANONYMOUS_DOWNLOAD_PASS: bytes=${file.length()}; sha256=${candidate.asset.digest}; original_signer=true; fixture_installed_code=${BuildConfig.VERSION_CODE-1}; real_install_claimed=false"
            } finally {check(directory.deleteRecursively())}
        } finally {http.cancel()}
    }
}
