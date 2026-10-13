// The Android Gradle plugin goes on the ROOT classpath (same classloader as
// the Kotlin plugin, which needs its classes), and only when the app module is
// included (Android SDK present): groma-core keeps building without the SDK
// nor the Google repository.
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        if (rootProject.findProject(":app") != null) {
            classpath("com.android.tools.build:gradle:8.7.3")
        }
    }
}

plugins {
    kotlin("jvm") version "2.4.21" apply false
    kotlin("plugin.compose") version "2.4.21" apply false
}
