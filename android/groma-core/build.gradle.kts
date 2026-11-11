plugins {
    kotlin("jvm")
    `java-library`
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

// WMM2025 coefficients, species list and triggers: single source in the Python package, copied as
// resources (/groma/WMM2025.COF, essences_bdforet_v2.json, declencheurs.sql) rather than duplicated.
val copierWmm by tasks.registering(Copy::class) {
    val donnees = rootProject.projectDir.parentFile.resolve("core/src/groma/donnees")
    from(donnees.resolve("WMM2025.COF"), donnees.resolve("essences_bdforet_v2.json"), donnees.resolve("declencheurs.sql"))
    into(layout.buildDirectory.dir("generated/wmm/groma"))
}
sourceSets.main {
    resources.srcDir(copierWmm.map { layout.buildDirectory.dir("generated/wmm").get() })
}

dependencies {
    // Shared map code (Emprise, ZoneTuiles, Geoplateforme layers; JitPack). `api`: Emprise is part of DonneesCarte.
    api("com.github.pobsteta.terrain:terrain-carte:v0.7.1")
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:6.1.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.12.0")
    // SQLite JDBC driver: runs the GeoPackage write protocol against the
    // example GeoPackage built by GDAL, as the phone does with android.database.sqlite.
    testImplementation("org.xerial:sqlite-jdbc:3.54.1.0")
}

tasks.test {
    useJUnitPlatform()
    systemProperty("groma.racine", rootProject.projectDir.parentFile.absolutePath)
}
