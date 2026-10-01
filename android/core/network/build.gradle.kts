plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "cu.ipvgc.android.core.network"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {

    implementation(project(":core:common"))
    implementation(libs.okhttp)
    implementation(libs.retrofit)
    implementation(libs.retrofit.jackson)
    implementation(libs.jackson.kotlin)
    implementation(libs.core.domain)
    testImplementation(libs.junit)
    testImplementation(libs.truth)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
}
