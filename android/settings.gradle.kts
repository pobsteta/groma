pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io") { content { includeGroup("com.github.pobsteta.terrain") } }
    }
}

rootProject.name = "groma"

// Pure Kotlin logic shared with the server rules (JVM, testable without Android SDK).
include(":groma-core")

// The Android application needs the Android SDK (ANDROID_HOME or local.properties).
if (System.getenv("ANDROID_HOME") != null || file("local.properties").exists()) {
    include(":app")
}
