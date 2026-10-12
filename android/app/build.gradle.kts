plugins {
    id("com.android.application")
    kotlin("android")
}

// Single project version, bumped by release-please. versionCode is derived
// from it (MAJOR*10000 + MINOR*100 + PATCH) so every release is monotonic.
val versionGroma = "0.1.0" // x-release-please-version
val codeVersionGroma = versionGroma.split(".").map(String::toInt).let { (ma, mi, pa) -> ma * 10000 + mi * 100 + pa }

// Release signing from the environment (CI secrets); without it the release
// build stays unsigned and the CI ships the debug APK instead.
val keystoreGroma: String? = System.getenv("GROMA_KEYSTORE")

android {
    namespace = "groma.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "fr.nemeton.groma"
        minSdk = 26
        targetSdk = 35
        versionCode = codeVersionGroma
        versionName = versionGroma
    }

    signingConfigs {
        if (keystoreGroma != null) {
            create("release") {
                storeFile = file(keystoreGroma)
                storePassword = System.getenv("GROMA_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("GROMA_KEY_ALIAS")
                keyPassword = System.getenv("GROMA_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            if (keystoreGroma != null) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { viewBinding = true }
}

dependencies {
    implementation(project(":groma-core"))
    implementation("com.google.ar:core:1.46.0")                 // Apache-2.0 (client SDK)
    implementation("org.opencv:opencv:4.10.0")                  // Apache-2.0, ArUco + blur
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.work:work-runtime-ktx:2.10.0")      // queued upload, Wi-Fi by default
    implementation("androidx.documentfile:documentfile:1.0.1")
}
