plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
}

kotlin { jvmToolchain(21) }

application {
    mainClass.set("cu.ipvgc.seed.SeedMainKt")
}

dependencies {
    implementation(libs.kotlin.stdlib)
    implementation(libs.postgresql)
    implementation(libs.flyway.core)
    implementation(libs.flyway.postgresql)
    implementation(libs.jackson.databind)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.kotlin.reflect)
    implementation(libs.slf4j.simple)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotest.assertions)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test { useJUnitPlatform() }
