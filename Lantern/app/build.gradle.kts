plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    id("com.google.dagger.hilt.android")
    id("com.google.gms.google-services")
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}

android {
    namespace = "com.ssafy.lantern"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.ssafy.lantern"
        minSdk = 33
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        // Room 스키마 위치 설정
        javaCompileOptions {
            annotationProcessorOptions {
                arguments += mapOf(
                    "room.schemaLocation" to "$projectDir/schemas",
                    "room.incremental" to "true"
                )
            }
        }
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
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf("-Xjvm-default=all")
    }

    buildFeatures {
        viewBinding = true
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }

    // KSP-generated sources
    applicationVariants.all {
        val variantName = name
        kotlin.sourceSets.getByName(variantName) {
            kotlin.srcDir("build/generated/ksp/$variantName/kotlin")
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // Room
    val roomVersion = "2.6.0"
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.room.ktx)
    testImplementation(libs.androidx.room.testing)
    implementation(libs.androidx.room.paging)

    // 자바 8 기능을 위한 desugaring 라이브러리
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")

    // Nordic BLE Mesh 라이브러리 추가 (버전 업데이트)
    implementation("no.nordicsemi.android:mesh:3.3.4") 
    implementation("no.nordicsemi.android.support.v18:scanner:1.6.0")
    implementation("no.nordicsemi.android:ble:2.6.1")
    // 블루투스 리소스 추가
    implementation("androidx.core:core-ktx:1.12.0")

    // Gson for JSON serialization/deserialization
    implementation("com.google.code.gson:gson:2.10.1")

    // Retrofit & OkHttp for network calls
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    
    // DataStore for token storage
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // Jetpack Compose
    implementation(libs.androidx.ui.v180)
    implementation(libs.androidx.material)
    implementation(libs.androidx.ui.tooling.preview.v180)
    implementation(libs.androidx.activity.compose.v170)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Google Sign-In - 최신 버전으로 통일하고 중복 제거
    implementation("com.google.android.gms:play-services-auth:20.7.0") // 최신 안정 버전으로 변경
    implementation("com.google.firebase:firebase-auth:22.3.0") // 호환되는 Firebase 버전

    // Hilt (버전은 프로젝트 상황에 맞게 조정)
    implementation("com.google.dagger:hilt-android:2.51.1")
    ksp("com.google.dagger:hilt-compiler:2.51.1")
    // ViewModel 주입 (@HiltViewModel)
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // Accompanist Permissions (권한 요청 UI)
    implementation("com.google.accompanist:accompanist-permissions:0.34.0") // 최신 버전 확인
}
