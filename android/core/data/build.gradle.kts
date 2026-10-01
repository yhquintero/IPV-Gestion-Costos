plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "cu.ipvgc.android.core.data"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }
    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
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
    implementation(project(":core:network"))
    implementation(project(":core:security"))
    implementation(libs.core.domain)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.sqlcipher)
    implementation(libs.work.runtime)
    implementation(libs.hilt.work)
    ksp(libs.hilt.androidx.compiler)
    implementation(libs.coroutines.android)
    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.room.testing)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.room.testing)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
}
