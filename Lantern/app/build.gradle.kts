plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    id("com.google.dagger.hilt.android")
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}

android {
    namespace = "com.ssafy.lanterns"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ssafy.lanterns"
        minSdk = 31
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

    signingConfigs {
        getByName("debug") {
            // app/keystores/shared-debug.jks
            storeFile = file("keystores/shared-debug.jks")
            storePassword = "team204"
            keyAlias = "e204debugkey"
            keyPassword = "team204"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("debug")
            isDebuggable = true
        }
        getByName("release") {
            // 릴리스 빌드 기본 설정만 유지
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            isDebuggable = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-Xjvm-default=all",
            "-opt-in=androidx.media3.common.util.UnstableApi",
            "-Xskip-metadata-version-check"
        )
        apiVersion = "1.9"
        languageVersion = "1.9"
    }

    buildFeatures {
        viewBinding = true
        compose = true
        buildConfig = true
        dataBinding = true
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
    
    // DataBinding과 KSP 문제 해결을 위한 설정 추가
    androidComponents {
        onVariants(selector().all()) { variant ->
            afterEvaluate {
                try {
                    val variantName = variant.name.capitalize()
                    val kspTask = tasks.getByName("ksp${variantName}Kotlin")
                    val dataBindingTask = tasks.getByName("dataBindingGenBaseClasses${variantName}")
                    
                    kspTask.dependsOn(dataBindingTask)
                    
                    if (kspTask is org.jetbrains.kotlin.gradle.tasks.AbstractKotlinCompileTool<*>) {
                        kspTask.source(dataBindingTask.outputs.files)
                    }
                } catch (e: Exception) {
                    println("Warning: Could not configure DataBinding-KSP integration: ${e.message}")
                }
            }
        }
    }
}

kotlin {
    jvmToolchain(17)
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

    // Kotlin 표준 라이브러리 버전 강제 지정
    implementation(platform("org.jetbrains.kotlin:kotlin-bom:1.9.22"))

    // Room
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.room.ktx)
    testImplementation(libs.androidx.room.testing)
    implementation(libs.androidx.room.paging)

    // Retrofit & OkHttp for network calls
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    
    // DataStore for token storage
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // Jetpack Compose UI Components
    implementation(libs.androidx.ui.v180)
    implementation(libs.androidx.ui.tooling.preview.v180)
    implementation(libs.androidx.activity.compose.v170)
    debugImplementation(libs.androidx.compose.ui.tooling)
    
    // Compose Material 관련 의존성
    implementation(libs.androidx.compose.material.icons)
    implementation("androidx.compose.material:material:1.6.5")
    implementation("androidx.compose.material3:material3:1.2.1")
    implementation("androidx.compose.material3:material3-window-size-class:1.2.1")
    
    // Compose Animation
    implementation("androidx.compose.animation:animation:1.6.5")
    implementation("androidx.compose.animation:animation-core:1.6.5")
    implementation("androidx.compose.material:material-icons-extended:1.6.5")
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-ui:1.3.1")
    implementation("androidx.media3:media3-common:1.3.1")

    // ViewModel
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    
    // Lifecycle Runtime Compose - LocalLifecycleOwner 지원
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    
    // 앱 생명주기 감지를 위한 Process Lifecycle Owner
    implementation("androidx.lifecycle:lifecycle-process:2.7.0")

    // Navigation Compose
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Google Sign-In
    implementation("com.google.android.gms:play-services-auth:20.7.0")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.51.1")
    ksp("com.google.dagger:hilt-compiler:2.51.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // Accompanist Permissions
    implementation("com.google.accompanist:accompanist-permissions:0.34.0")

    // GPS
    implementation("com.google.android.gms:play-services-location:21.0.1")
    
    // Porcupine
    implementation("ai.picovoice:porcupine-android:3.0.1")
}