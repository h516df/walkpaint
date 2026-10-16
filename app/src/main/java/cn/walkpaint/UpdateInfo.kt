package cn.walkpaint

import org.json.JSONObject

internal data class CurrentRelease(val version:String,val code:Int,val sections:List<Pair<String,List<String>>>) {
    companion object {
        fun decode(text:String):CurrentRelease {
            val j=JSONObject(text)
            val version=j.getString("versionName");val code=j.getInt("versionCode")
            require(version.matches(Regex("[0-9]+\\.[0-9]+")) && code>0)
            val sections=listOf("新增","改进","修复","说明").map { heading ->
                val a=j.getJSONArray(heading);require(a.length() in 1..10)
                heading to (0 until a.length()).map { i -> a.getString(i).also { require(it.length in 1..400) } }
            }
            return CurrentRelease(version,code,sections)
        }
    }
}
internal object UpdateDestination {
    const val URL="https://github.com/h516df/walkpaint/releases"
    const val UNKNOWN="最新版尚未核对"
    const val BROWSER="请在浏览器打开公开发布页，核对版本与更新说明，无需登录"
    const val OFFLINE="暂时无法检查，请连接网络后重试。本版说明仍可离线查看。"
    const val NO_BROWSER="没有可用浏览器，请安装浏览器后再试。本版说明仍可查看。"
    fun notice(networkAvailable:Boolean)=if(networkAvailable) BROWSER else OFFLINE
}
