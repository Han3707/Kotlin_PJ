plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    // Changed from kapt to ksp
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.hilt.android)
    id("kotlin-parcelize")
}

android {
    namespace = "com.example.bletest"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.bletest"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        viewBinding = true
        compose = true // Compose 활성화
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8" // Kotlin 1.9.22와 호환되는 컴파일러 버전
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    // kapt { ... } block removed as we are using KSP
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material) // Uses corrected alias from TOML
    implementation(libs.androidx.activity.ktx) // Changed from activity to activity.ktx for consistency, ensure TOML defines androidx-activity-ktx
    implementation(libs.androidx.constraintlayout)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Lifecycle 컴포넌트
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx) // Uses uncommented alias from TOML

    // Hilt
    implementation(libs.hilt.android)
    // Changed from kapt to ksp
    ksp(libs.hilt.compiler)
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")

    // Nordic BLE 라이브러리
    implementation(libs.nordic.ble)
    implementation(libs.nordic.ble.ktx)
    // implementation(libs.nordic.ble.common) // Removed this line

    // 테스트 (Corrected aliases to match TOML keys)
    testImplementation(libs.junit4) // Changed from libs.junit
    androidTestImplementation(libs.androidx.test.junit) // Changed from libs.androidx.junit
    androidTestImplementation(libs.androidx.espresso.core)

    // Compose 의존성 추가
    val composeVersion = "1.5.8" // Kotlin 1.9.22와 호환되는 Compose 버전
    implementation("androidx.compose.ui:ui:$composeVersion")
    implementation("androidx.compose.ui:ui-tooling-preview:$composeVersion")
    implementation("androidx.compose.material3:material3:1.2.1")
    implementation("androidx.compose.material:material-icons-extended:$composeVersion")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.compose.runtime:runtime-livedata:$composeVersion")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    
    debugImplementation("androidx.compose.ui:ui-tooling:$composeVersion")
    debugImplementation("androidx.compose.ui:ui-test-manifest:$composeVersion")
}