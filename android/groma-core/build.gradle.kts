plugins {
    kotlin("jvm")
}

// Bytecode 17 (Android compatible), compiled with whichever JDK >= 17 runs Gradle.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    // SQLite JDBC driver: runs the GeoPackage write protocol against the
    // example GeoPackage built by GDAL, as the phone does with android.database.sqlite.
    testImplementation("org.xerial:sqlite-jdbc:3.45.3.0")
}

tasks.test {
    useJUnitPlatform()
    systemProperty("groma.racine", rootProject.projectDir.parentFile.absolutePath)
}
