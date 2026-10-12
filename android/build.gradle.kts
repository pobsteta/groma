// The Android Gradle plugin is declared in app/build.gradle.kts, so that the
// pure Kotlin module builds without the Android SDK nor the Google repository.
plugins {
    kotlin("jvm") version "2.0.21" apply false
}
