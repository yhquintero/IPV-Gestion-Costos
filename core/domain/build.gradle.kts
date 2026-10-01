plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
}

group = "cu.ipvgc"
version = "0.5.0"

java {
    toolchain {
        // Bytecode 17: el servidor corre en JDK 21 y Android (desugar) lo consume.
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlin.reflect)
    implementation(libs.jackson.databind)
    implementation(libs.jackson.module.kotlin)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.junit.jupiter.params)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.kotest.assertions)
    testImplementation(libs.kotest.property)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}
