plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
}

kotlin { jvmToolchain(21) }

dependencies {
    implementation(project(":core:domain"))
    implementation(libs.spring.boot.starter.jdbc)
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.flyway.core)
    implementation(libs.flyway.postgresql)
    implementation(libs.postgresql)
    implementation(libs.nimbus.jose.jwt)
    implementation(libs.bouncycastle.provider)
    implementation(libs.kotlin.stdlib)
    implementation(libs.jackson.module.kotlin)

    testImplementation(project(":tools:seed"))
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotest.assertions)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit)
}

tasks.processResources {
    from(rootProject.file("server/db/migration")) {
        into("db/migration")
    }
    from(rootProject.file("contracts/openapi")) {
        into("openapi")
    }
}

tasks.test {
    useJUnitPlatform()
    systemProperty("ipvgc.migrationsDir", rootProject.file("server/db/migration").absolutePath)
}
