plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "cu.ipvgc.android.feature.auth"
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
    buildFeatures {
        compose = true
    }
}

dependencies {

    implementation(project(":core:common"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:data"))
    implementation(project(":core:security"))
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.core.domain)
    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.mockk)
    implementation(project(":core:network"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
}
