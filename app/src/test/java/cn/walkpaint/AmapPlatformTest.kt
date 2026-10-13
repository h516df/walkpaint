package cn.walkpaint

import org.junit.Assert.*
import org.junit.Test

/**
 * Which devices can run the official AMap engine.
 *
 * The middle case is the one this file used to get wrong, and the wrongness is worth keeping in the
 * record: it asserted that `x86_64,arm64-v8a` could not run AMap, on the reasoning that the primary
 * ABI decides. That is not the rule the installer uses, and it is not what the device does — on
 * Google's API 30+ x86_64 images that list comes with `ro.enable.native.bridge.exec=1` and
 * `libndk_translation.so`, the arm64-only build installs, `primaryCpuAbi` becomes `arm64-v8a`, and
 * the loader maps the real `libAMapSDK_MAP_*.so` through the bridge. The old assertion is exactly why
 * the AMap path looked unverifiable from this machine for as long as it did.
 *
 * Measured on the dedicated AVD (emulator-5556, WalkPaint_Google_API36) in
 * verification/amap-translation-3: primaryCpuAbi=arm64-v8a and
 * `nativeloader: Load …/lib/arm64-v8a/libAMapSDK_MAP_v11_3_100.so`.
 */
class AmapPlatformTest {
    @Test fun armDevicesUseTheOfficialNativeMap() {
        assertTrue(AmapPlatform.supported(listOf("arm64-v8a", "armeabi-v7a")))
        assertTrue(AmapPlatform.supported(listOf("armeabi-v7a")))
    }

    @Test fun anX86DeviceThatCanExecuteArmLibrariesRunsTheRealEngine() {
        // The emulator, and any x86_64 device with the ARM native bridge: the installer picks
        // arm64-v8a from the whole list, so the process really does load the ARM libraries.
        assertTrue(AmapPlatform.supported(listOf("x86_64", "arm64-v8a")))
        assertTrue(AmapPlatform.supported(listOf("x86_64", "x86", "arm64-v8a", "armeabi-v7a")))
    }

    @Test fun anX86DeviceWithoutTheBridgeKeepsTheFallback() {
        // A real x86_64 phone with no ARM bridge advertises x86 only, and the AMap libraries are
        // simply not installed there; availability must stay false.
        assertFalse(AmapPlatform.supported(listOf("x86_64", "x86")))
        assertFalse(AmapPlatform.supported(listOf("x86")))
        assertFalse(AmapPlatform.supported(emptyList()))
    }

    @Test fun availabilityAloneNeverMeansTheEngineIsRunning() {
        // supported() answers "can this device load it". Whether AMap is actually used is a separate
        // question gated on the key and the person's consent, and the export reports all of it.
        assertTrue(AmapPlatform.supported(listOf("arm64-v8a")))
    }
}
