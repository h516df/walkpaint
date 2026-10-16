package cn.walkpaint

import org.json.JSONObject

/** The fixed upstream repository identity is checked independently of catalog data. */
internal object UpdateRepositoryPolicy {
    fun validate(repo:JSONObject) {
        updateRequire(repo.getLong("id")==PrivateUpdatePolicy.REPO_ID &&
            !repo.getBoolean("private") && repo.getString("full_name")==PrivateUpdatePolicy.REPO,
            "更新来源不是预期的公开仓库，已停止检查")
    }
}
