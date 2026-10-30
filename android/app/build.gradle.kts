plugins {
    id("com.android.application") // AGP 9: Kotlin built in, no kotlin("android")
    kotlin("plugin.compose")
}

// Single project version, bumped by release-please. versionCode is derived
// from it (MAJOR*10000 + MINOR*100 + PATCH) so every release is monotonic.
val versionGroma = "0.14.0" // x-release-please-version
val codeVersionGroma = versionGroma.split(".").map(String::toInt).let { (ma, mi, pa) -> ma * 10000 + mi * 100 + pa }

// Release signing from the environment (CI secrets); without it the release
// build stays unsigned and the CI ships the debug APK instead.
val keystoreGroma: String? = System.getenv("GROMA_KEYSTORE")

android {
    namespace = "groma.app"
    compileSdk = 37

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
    buildFeatures { viewBinding = true; compose = true }
}

dependencies {
    implementation(project(":groma-core"))
    implementation("com.google.ar:core:1.56.0")                 // Apache-2.0 (client SDK)
    implementation("org.opencv:opencv:5.0.0.1")                  // Apache-2.0, ArUco + blur
    implementation("androidx.appcompat:appcompat:1.8.0")
    implementation("androidx.activity:activity-ktx:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.work:work-runtime-ktx:2.12.0")      // queued upload, Wi-Fi by default
    implementation("androidx.documentfile:documentfile:1.1.0")
    implementation("org.osmdroid:osmdroid-android:6.1.20")       // Apache-2.0, map screen (IGN / OSM tiles)
    // Main screen in Compose + Material 3, same look as Marculus (Apache-2.0).
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("com.github.pobsteta.terrain:terrain-style:v0.7.1") // shared Marculus style: colours, shapes, typography (JitPack)
    testImplementation(kotlin("test-junit"))
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}
