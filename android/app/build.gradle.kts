plugins {
    id("com.android.application") // AGP 9: Kotlin built in, no kotlin("android")
    kotlin("plugin.compose")
}

// Single project version (calendar YYYY.M.N), bumped by release-please.
// versionCode is derived from it (YYYY*10000 + M*100 + N, N < 100) so every
// release is monotonic, including after the former 0.x versions.
val versionGroma = "2026.11.8" // x-release-please-version
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
        // Smoke test (mission 026, modelled on Surculus ADR 0011): a copy of the release, installed BESIDE the
        // real app. The suffix is ESSENTIAL: without it the test APK would carry the id of the installed app
        // (fr.nemeton.groma) and the test runner's cleanup could uninstall it with the field data. With it, the
        // app is fr.nemeton.groma.fumee and the test APK fr.nemeton.groma.fumee.test: neither can replace nor
        // uninstall the real one. Same signature as the release when the keystore is given, else the debug key.
        create("fumee") {
            initWith(getByName("release"))
            matchingFallbacks += "release"
            applicationIdSuffix = ".fumee"
            versionNameSuffix = "-fumee"
            signingConfig = signingConfigs.getByName(if (keystoreGroma != null) "release" else "debug")
            // Optional, local only: -PabiFumee=x86_64 keeps one ABI so the 190 MB APK fits a small emulator disk.
            // Not set in CI nor for a phone: the default is every ABI, like the release.
            (findProperty("abiFumee") as String?)?.let { abis -> ndk { abiFilters += abis.split(",") } }
        }
    }
    testBuildType = "fumee"
    // The example project (single source, not copied): inventaire_video.gpkg and rasters/ become assets of the test APK.
    sourceSets.getByName("androidTest").assets.directories.add(rootProject.file("../exemples/inventaire_video").path)

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { viewBinding = true; compose = true }
}

dependencies {
    implementation(project(":groma-core"))
    implementation("com.google.ar:core:1.57.0")                 // Apache-2.0 (client SDK)
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
    implementation("com.github.pobsteta.terrain:terrain-carte:v0.7.1") // shared map code: tile zone, Géoplateforme WMTS layers (JitPack)
    testImplementation(kotlin("test-junit"))
    // Smoke test on a device (mission 026): Apache-2.0, test APK only.
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

// The smoke test fails when no test ran (Gradle reports success when the instrumentation crashes before the first
// test). The report is deleted first, so that an older green report cannot hide a crash.
val rapportsFumee = layout.buildDirectory.dir("outputs/androidTest-results/connected/fumee").get().asFile
val verifierScriptFumee = rootProject.file("../scripts/verifier-fumee.sh")
val verifierFumee = tasks.register<Exec>("verifierFumee") {
    description = "Checks that the smoke test ran at least one test, no failure (scripts/verifier-fumee.sh)."
    commandLine("bash", verifierScriptFumee.path, rapportsFumee.path)
}
val effacerRapportFumee = tasks.register<Delete>("effacerRapportFumee") { delete(rapportsFumee) }
tasks.matching { it.name == "connectedFumeeAndroidTest" }.configureEach {
    dependsOn(effacerRapportFumee)
    finalizedBy(verifierFumee)
}
