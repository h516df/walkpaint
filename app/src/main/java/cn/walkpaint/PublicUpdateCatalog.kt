package cn.walkpaint

import org.json.JSONObject
import java.security.MessageDigest

/** Read one stable release. No account or private-history traversal is needed. */
internal object PublicUpdateCatalog {
    fun read(installed:Int,abis:List<String>,json:(String)->String,asset:(Long)->ByteArray):UpdateCatalogResult {
        UpdateRepositoryPolicy.validate(JSONObject(json("")))
        val release=JSONObject(json("/releases/latest"))
        updateRequire(!release.optBoolean("draft",true) && !release.optBoolean("prerelease",true),
            "还没有可核对的稳定版本，请在浏览器查看发布页")
        val tag=release.getString("tag_name")
        updateRequire(tag.matches(Regex("v[0-9]+\\.[0-9]+")))
        val items=release.getJSONArray("assets")
        val rows=(0 until items.length()).map { items.getJSONObject(it) }
        val metadata=UpdateCatalog.manifestAsset(tag,rows)
            ?: throw UpdateFailure("新版缺少更新说明，已停止检查")
        val manifest=PrivateUpdatePolicy.asset(metadata)
        updateRequire(manifest.size<=65536)
        val bytes=asset(manifest.id)
        val digest=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)}
        updateRequire(bytes.size.toLong()==manifest.size && digest==manifest.digest,
            "更新说明校验失败，请稍后重试")
        val notes=CurrentRelease.decode(String(bytes,Charsets.UTF_8))
        updateRequire(tag=="v${notes.version}")
        return UpdateCatalog.select(listOf(PublishedUpdate(notes,rows)),installed,abis)
    }
}
