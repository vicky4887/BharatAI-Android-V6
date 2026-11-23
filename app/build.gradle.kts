plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
val bharataiNative = providers.gradleProperty("bharataiNative").orNull == "true"
android {
    namespace = "com.bharatai.education"
    compileSdk = 35
    buildToolsVersion = "35.0.1"
    defaultConfig {
        applicationId = "com.bharatai.education"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.3.0-local-gguf"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    if (bharataiNative) {
        ndkVersion = "27.2.12479018"
        defaultConfig {
            ndk { abiFilters += listOf("arm64-v8a") }
            externalNativeBuild {
                cmake { arguments += listOf("-DANDROID_STL=c++_shared", "-DGGML_NATIVE=OFF", "-DGGML_OPENMP=OFF") }
            }
        }
        externalNativeBuild {
            cmake {
                path = file("src/main/cpp/CMakeLists.txt")
                version = "3.22.1"
            }
        }
    }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}
dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    // BUNDLED OCR model, usable in airplane mode after installing the APK.
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
