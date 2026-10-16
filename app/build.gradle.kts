import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}
// The restricted key is stored only in Git-ignored local.properties.
val mapProperties = Properties()
rootProject.file("local.properties").takeIf { it.isFile }?.inputStream()?.use { mapProperties.load(it) }
val amapKey = (mapProperties.getProperty("walkpaintAmapKey")
    ?: providers.gradleProperty("walkpaintAmapKey").orNull).orEmpty().trim()
require(amapKey.matches(Regex("[A-Za-z0-9]*"))) { "Invalid AMap Android Key" }
val arm64Only = providers.gradleProperty("walkpaintArm64Only").orNull == "true"
// A clean public checkout can build an isolated debug app without the official private key.
val developerBuild = providers.gradleProperty("walkpaintDeveloperBuild").orNull == "true"
// Public GitHub App client id only. Never put a client secret or a PC token here.
val updateClientId = (providers.gradleProperty("walkpaintUpdateClientId").orNull ?: "Iv23liQM3N2avCTc0QAd").trim()
require(updateClientId.matches(Regex("[A-Za-z0-9.]*"))) { "Invalid public update client id" }

android {
    namespace = "cn.walkpaint"
    compileSdk = 36
    defaultConfig {
        applicationId = "cn.walkpaint"
        minSdk = 26
        targetSdk = 36
        versionCode = 63
        versionName = "0.55"
        testInstrumentationRunner = "cn.walkpaint.RoadAcceptanceInstrumentation"
        // Optional phone-sized APK; the default universal debug APK remains emulator-compatible.
        if (arm64Only) {
            ndk { abiFilters.add("arm64-v8a") }
        }
        buildConfigField("boolean", "ARM64_ONLY", arm64Only.toString())
        buildConfigField("boolean", "DEVELOPER_BUILD", developerBuild.toString())
        buildConfigField("String", "AMAP_KEY", "\"$amapKey\"")
        buildConfigField("String", "UPDATE_CLIENT_ID", "\"$updateClientId\"")
        manifestPlaceholders["AMAP_API_KEY"] = amapKey
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    // Reuse the existing local debug certificate so v0.2 upgrades v0.1 without uninstalling.
    signingConfigs {
        getByName("debug") {
            if (!developerBuild) {
                val existing = rootProject.file("${System.getProperty("user.home")}/.android/debug.keystore")
                require(existing.isFile) { "Original WalkPaint signing certificate is missing; refusing an incompatible update" }
                storeFile = existing
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
                // OEM installers may inspect the JAR certificate before platform verification.
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }
    buildTypes {
        getByName("debug") {
            if (developerBuild) applicationIdSuffix = ".dev"
        }
        getByName("release") {
            // Preserve the identity of every distributed version, without a debuggable release.
            signingConfig = if (developerBuild) null else signingConfigs.getByName("debug")
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








