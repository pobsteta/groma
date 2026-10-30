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

// WMM2025 coefficients: single source in the Python package, copied as a
// resource (/groma/WMM2025.COF) rather than duplicated by hand.
val copierWmm by tasks.registering(Copy::class) {
    from(rootProject.projectDir.parentFile.resolve("core/src/groma/donnees/WMM2025.COF"))
    into(layout.buildDirectory.dir("generated/wmm/groma"))
}
sourceSets.main {
    resources.srcDir(copierWmm.map { layout.buildDirectory.dir("generated/wmm").get() })
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:6.1.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    // SQLite JDBC driver: runs the GeoPackage write protocol against the
    // example GeoPackage built by GDAL, as the phone does with android.database.sqlite.
    testImplementation("org.xerial:sqlite-jdbc:3.53.4.0")
    // Mission 012: terrain-carte (JitPack) only to check it matches Groma's own map code before mission 013 swaps it in.
    testImplementation("com.github.pobsteta.terrain:terrain-carte:v0.7.1")
}

tasks.test {
    useJUnitPlatform()
    systemProperty("groma.racine", rootProject.projectDir.parentFile.absolutePath)
}
