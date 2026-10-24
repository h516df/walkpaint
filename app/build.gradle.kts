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
        versionCode = 77
        versionName = "0.69"
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
    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".dev"
            buildConfigField("boolean", "DEVELOPER_BUILD", "true")
        }
        getByName("release") {
            // Preserve the identity of every distributed version, without a debuggable release.
            signingConfig = null
            buildConfigField("boolean", "DEVELOPER_BUILD", "false")
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    testOptions { unitTests.isReturnDefaultValues = true }
}
dependencies {
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









// An official package must contain the restricted application key; users never configure it.
gradle.taskGraph.whenReady {
    if(!developerBuild && allTasks.any { it.name in listOf("assembleDebug","packageDebug","bundleDebug","assembleRelease","packageRelease","bundleRelease") }) {
        require(amapKey.isNotBlank()) { "Official packages require the configured AMap application key; use walkpaintDeveloperBuild=true for an isolated developer build" }
    }
    if(allTasks.any { it.name in listOf("assembleRelease","bundleRelease") }) {
        require(amapKey.isNotBlank()) { "Official release requires the configured AMap application key" }
        require(!developerBuild && arm64Only) { "Official release requires the ARM64 AMap build" }
    }
}

// Host-native UI regression checks use only synthetic fixtures, never the production map key.
android.testOptions.unitTests.apply {
    isIncludeAndroidResources = true
    all {
        it.maxHeapSize = "1g"
        it.systemProperty("user.home", rootProject.file(".gradle/test-home").also { it.mkdirs() }.absolutePath)
        it.systemProperty("java.io.tmpdir", rootProject.file(".gradle/test-tmp").also { it.mkdirs() }.absolutePath)
        it.systemProperty("roborazzi.test.record", "true")
        it.systemProperty("robolectric.pixelCopyRenderMode", "hardware")
        it.systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2")
        listOf("http.proxyHost", "http.proxyPort", "https.proxyHost", "https.proxyPort", "javax.net.ssl.trustStore").forEach { key ->
            System.getProperty(key)?.let { value -> it.systemProperty(key, value) }
        }
    }
}
dependencies {
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("org.robolectric:robolectric:4.17")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.76.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.76.0")
}

// Fail closed: unsigned candidates require an explicit opt-in; final signing is external.
gradle.taskGraph.whenReady {
    val releaseRequested = allTasks.any { it.project == project && it.name in setOf("assembleRelease", "packageRelease", "bundleRelease") }
    if (releaseRequested && providers.gradleProperty("unsignedRelease").orNull != "true") {
        throw GradleException("Official signing requires original injected credentials via cloud/sign_apk.py. Use -PunsignedRelease=true only for an unsigned build candidate.")
    }
}
