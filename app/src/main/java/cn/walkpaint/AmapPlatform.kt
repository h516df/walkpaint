package cn.walkpaint

/** AMap 10+ ships native libraries only for ARM. Match the actual process ABI. */
object AmapPlatform {
    fun supported(abis: List<String>): Boolean =
        abis.firstOrNull() in setOf("arm64-v8a", "armeabi-v7a")
}
