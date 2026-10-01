plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "cu.ipvgc.android.core.security"
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
    implementation(libs.core.domain)
    implementation(libs.biometric)
    implementation(libs.tink)
    implementation(libs.datastore)
    implementation(libs.coroutines.android)
    testImplementation(libs.junit)
    testImplementation(libs.truth)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
}
