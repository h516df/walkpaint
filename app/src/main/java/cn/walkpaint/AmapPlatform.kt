package cn.walkpaint

/** ARM-only artifacts can use an advertised ARM64 native bridge. Universal artifacts still
 * choose the primary ABI and must not enable AMap in an x86 process without its ARM library.
 * Runtime consent, key, map-loaded state and actual JNI mapping are verified separately.
 */
object AmapPlatform {
    private val ARM_ABIS = setOf("arm64-v8a", "armeabi-v7a")

    fun supported(abis: List<String>, arm64Only: Boolean = false): Boolean =
        if (arm64Only) "arm64-v8a" in abis else abis.firstOrNull() in ARM_ABIS
}
