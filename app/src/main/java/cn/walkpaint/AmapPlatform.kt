package cn.walkpaint

/**
 * AMap 10+ ships native libraries for ARM only, so the engine is available exactly when the
 * platform is able to *execute* ARM libraries — which is not the same question as "is the primary
 * ABI ARM".
 *
 * `Build.SUPPORTED_ABIS.first()` is `x86_64` on the Android emulator, yet Google's API 30+ x86_64
 * system images declare `ro.product.cpu.abilist=x86_64,arm64-v8a`, set `ro.enable.native.bridge.exec=1`
 * and ship `libndk_translation.so`: an app's arm64-v8a native libraries are installed and run
 * through NDK translation. That is the same AMap build the phone runs, so asking only about the
 * *first* ABI downgraded the project's own emulator to the MapLibre fallback and hid the AMap path
 * from every local check — which is precisely how "the tests and the real phone differ" became
 * permanent.
 *
 * Deciding on the whole list matches the rule the installer itself uses to choose which ABI folder
 * to extract: if `arm64-v8a` is in the list, an arm64 APK is installed and its ARM libraries are
 * what the process loads. A real x86_64 phone with no native bridge lists only `x86_64,x86` and
 * keeps the fallback. (Written without a path containing a slash-star, because Kotlin nests block
 * comments and one in a KDoc swallows the rest of the file.)
 *
 * Note that this only decides *availability*. Which engine actually runs is still gated on the AMap
 * key and on the person's own one-time consent, and the field export's `引擎判定=` line reports all
 * three facts separately so an unavailable engine can never be reported as a working one.
 */
object AmapPlatform {
    private val ARM_ABIS = setOf("arm64-v8a", "armeabi-v7a")

    fun supported(abis: List<String>): Boolean = abis.any { it in ARM_ABIS }
}
