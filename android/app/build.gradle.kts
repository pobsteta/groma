plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    namespace = "groma.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "fr.nemeton.groma"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
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
