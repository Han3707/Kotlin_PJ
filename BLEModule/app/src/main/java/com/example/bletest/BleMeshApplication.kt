package com.example.bletest

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * BLE 메시 앱의 Application 클래스
 * Hilt 의존성 주입을 위한 진입점
 */
@HiltAndroidApp
class BleMeshApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // 애플리케이션 시작 시 초기화 코드
    }
} 