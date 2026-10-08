plugins { id("com.android.application") }
android {
    namespace = "cn.walkpaint"
    compileSdk = 36
    defaultConfig {
        applicationId = "cn.walkpaint"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    testOptions { unitTests.isReturnDefaultValues = true }
}
dependencies {
    implementation("org.maplibre.gl:android-sdk-opengl:13.5.2")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")
}
