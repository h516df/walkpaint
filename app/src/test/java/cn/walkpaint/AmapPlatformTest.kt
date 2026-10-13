package cn.walkpaint

import org.junit.Assert.*
import org.junit.Test

class AmapPlatformTest {
    @Test fun armOnlyArtifactCanUseTheEmulatorNativeBridge() {
        assertTrue(AmapPlatform.supported(listOf("x86_64", "arm64-v8a"), arm64Only = true))
        assertFalse(AmapPlatform.supported(listOf("x86_64", "x86"), arm64Only = true))
        assertFalse(AmapPlatform.supported(listOf("armeabi-v7a"), arm64Only = true))
    }
    @Test fun armDevicesUseTheOfficialNativeMap() {
        assertTrue(AmapPlatform.supported(listOf("arm64-v8a", "armeabi-v7a")))
        assertTrue(AmapPlatform.supported(listOf("armeabi-v7a")))
    }
    @Test fun x86CannotLoadTheCurrentArmOnlySdkEvenWithATranslatedSecondaryAbi() {
        assertFalse(AmapPlatform.supported(listOf("x86_64", "x86")))
        assertFalse(AmapPlatform.supported(listOf("x86_64", "arm64-v8a")))
        assertFalse(AmapPlatform.supported(emptyList()))
    }
}
