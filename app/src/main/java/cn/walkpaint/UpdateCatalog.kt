package cn.walkpaint
import org.json.JSONObject

internal data class PublishedUpdate(val notes:CurrentRelease,val assets:List<JSONObject>)
internal sealed class UpdateCatalogResult {
    class Current(val latest:CurrentRelease):UpdateCatalogResult()
    class Available(val candidate:UpdateCandidate):UpdateCatalogResult()
}
internal object UpdateCatalog {
    fun manifestAsset(tag:String,assets:List<JSONObject>):JSONObject? {
        updateRequire(tag.matches(Regex("v[0-9]+\\.[0-9]+")))
        val matches=assets.filter { it.optString("name") in setOf("release-notes.json","WalkPaint-$tag-release-notes.json") }
        updateRequire(matches.size<=1,"更新说明重复，请打开发布页核对版本")
        return matches.singleOrNull()
    }
    fun select(releases:List<PublishedUpdate>,installed:Int,abis:List<String>):UpdateCatalogResult {
        updateRequire(releases.isNotEmpty(),"发布资料尚未就绪，请打开发布页核对版本")
        updateRequire(releases.map { it.notes.code }.distinct().size==releases.size,"版本资料有冲突，请打开发布页核对版本")
        val newest=releases.maxBy { it.notes.code }
        // An installed newer preview needs no older APK for this CPU. Compare the manifest first.
        if(newest.notes.code<=installed) return UpdateCatalogResult.Current(newest.notes)
        val apk=PrivateUpdatePolicy.apkNames(newest.notes.version,abis).firstNotNullOfOrNull { name ->
            val matches=newest.assets.filter { it.optString("name")==name }
            updateRequire(matches.size<=1,"安装包资料重复，请打开发布页核对版本")
            matches.singleOrNull()
        }
            ?: throw UpdateFailure("新版暂未提供适合这台设备的安装包，请打开发布页")
        return UpdateCatalogResult.Available(UpdateCandidate(newest.notes,PrivateUpdatePolicy.asset(apk)))
    }
}
