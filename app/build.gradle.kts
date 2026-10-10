import java.security.KeyStore
import java.security.MessageDigest
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}
// The restricted key is stored only in Git-ignored local.properties. -PwalkpaintAmapKey= wins over
// the file so the guard below can be exercised without touching credentials on disk.
val mapProperties = Properties()
rootProject.file("local.properties").takeIf { it.isFile }?.inputStream()?.use { mapProperties.load(it) }
val cliAmapKey = providers.gradleProperty("walkpaintAmapKey").orNull
val amapKey = (cliAmapKey ?: mapProperties.getProperty("walkpaintAmapKey") ?: "").trim()
val allowMissingAmapKey = providers.gradleProperty("allowMissingAmapKey").orNull == "true"
// The old check was Regex("[A-Za-z0-9]*"), which accepts an empty key: a missing key still built
// successfully and only failed on the phone when the map opened. A release without a key is a
// deliberate choice, so it needs the explicit switch.
if (amapKey.isEmpty()) {
    if (!allowMissingAmapKey) {
        error(
            "[walkpaint-amap-key] 缺少高德 Android Key。\n" +
                "来源是 local.properties 的 walkpaintAmapKey，或 -PwalkpaintAmapKey=<32 位字母数字>。\n" +
                "没有 Key 时地图只能走离线或备用底图。确实要构建无 Key 包，请加 -PallowMissingAmapKey=true。",
        )
    }
} else if (!amapKey.matches(Regex("[A-Za-z0-9]{32}"))) {
    error("[walkpaint-amap-key] 高德 Android Key 格式不对：应为 32 位字母数字，实际 ${amapKey.length} 位。")
}
// Public GitHub App client id only. Never put a client secret or a PC token here.
val updateClientId = (providers.gradleProperty("walkpaintUpdateClientId").orNull ?: "Iv23liQM3N2avCTc0QAd").trim()
require(updateClientId.matches(Regex("[A-Za-z0-9.]*"))) { "Invalid public update client id" }

// ---------------------------------------------------------------------------------------------
// Signing red line. Every installed copy on a friend's phone was signed with the certificate below.
// If that keystore goes missing, or gets replaced by a freshly generated one, nobody can install an
// update over the old app any more; the only path left is uninstall and lose every recorded
// footprint. The old code wrapped the signing config in `if (existing.isFile)`, so a missing file
// made AGP silently generate a new key and the build still went green. It fails loudly instead.
// A genuinely new machine passes -PallowNewDebugKey=true and owns a new signing era.
// ---------------------------------------------------------------------------------------------
val expectedDebugCertSha1 = "FA:E3:DF:35:B3:2F:A7:DF:41:B3:F8:53:79:3E:A6:9D:39:5B:8E:52"
val allowNewDebugKey = providers.gradleProperty("allowNewDebugKey").orNull == "true"
val debugKeystore = file(
    providers.gradleProperty("walkpaintSigningKeystore")
        .getOrElse("${System.getProperty("user.home")}/.android/debug.keystore"),
)

fun debugKeystoreSha1(keystore: File): String {
    val store = KeyStore.getInstance(keystore, "android".toCharArray())
    val certificate = store.getCertificate("androiddebugkey")
        ?: error("[walkpaint-signing] 密钥库 ${keystore.absolutePath} 里找不到 androiddebugkey。")
    return MessageDigest.getInstance("SHA-1")
        .digest(certificate.encoded)
        .joinToString(":") { "%02X".format(it) }
}

if (!allowNewDebugKey) {
    if (!debugKeystore.isFile) {
        error(
            "[walkpaint-signing] 签名密钥不存在：${debugKeystore.absolutePath}\n" +
                "用它签名的包才能覆盖升级已安装的手机。丢了再生成一把新密钥，所有人只能卸载重装并丢掉全部足迹。\n" +
                "先恢复备份（见 docs/SIGNING.md）。确实要在新机器上开新的签名起点，请加 -PallowNewDebugKey=true。",
        )
    }
    val actualSha1 = debugKeystoreSha1(debugKeystore)
    if (!actualSha1.equals(expectedDebugCertSha1, ignoreCase = true)) {
        error(
            "[walkpaint-signing] 签名密钥指纹不符。\n" +
                "期望：$expectedDebugCertSha1\n实际：$actualSha1\n" +
                "换签名会让所有已安装用户必须卸载重装。确认后用 -PallowNewDebugKey=true 显式放行。",
        )
    }
}

android {
    namespace = "cn.walkpaint"
    compileSdk = 36
    defaultConfig {
        applicationId = "cn.walkpaint"
        minSdk = 26
        targetSdk = 36
        // A distinct upgrade from the distributed code 22 preview (v0.20).
        versionCode = 23
        versionName = "0.21"
        testInstrumentationRunner = "cn.walkpaint.RoadAcceptanceInstrumentation"
        // Optional phone-sized APK; the default universal debug APK remains emulator-compatible.
        if (providers.gradleProperty("walkpaintArm64Only").orNull == "true") {
            ndk { abiFilters.add("arm64-v8a") }
        }
        buildConfigField("String", "AMAP_KEY", "\"$amapKey\"")
        buildConfigField("String", "UPDATE_CLIENT_ID", "\"$updateClientId\"")
        manifestPlaceholders["AMAP_API_KEY"] = amapKey
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    // Reuse the existing local debug certificate so v0.2 upgrades v0.1 without uninstalling.
    // The existence and fingerprint of that certificate are checked above, before this block runs.
    signingConfigs {
        getByName("debug") {
            if (debugKeystore.isFile) {
                storeFile = debugKeystore
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    testOptions { unitTests.isReturnDefaultValues = true }
}
dependencies {
    implementation("org.maplibre.gl:android-sdk-opengl:13.5.2")
    // 2026 official AMap SDK; only initialized after separate user consent.
    implementation("com.amap.api:3dmap-location-search:11.3.100_loc11.3.000_sea9.8.1")
    implementation(platform("androidx.compose:compose-bom:2026.03.01"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")
}
