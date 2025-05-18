// src/main/java/com/ssafy/lantern/MyApp.kt
package com.ssafy.lanterns

import android.app.Application
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.room.Room
import com.ssafy.lanterns.data.database.AppDatabase
import com.ssafy.lanterns.service.WakeWordService
import com.ssafy.lanterns.service.ble.GlobalBleManager
import com.ssafy.lanterns.service.ble.advertiser.GlobalApplication
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MyApp : Application() {
    val db: AppDatabase by lazy {
        Room.databaseBuilder(
            this,
            AppDatabase::class.java,
            "lantern.db"
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    // 앱 생명주기 옵저버 설정
    override fun onCreate() {
        super<Application>.onCreate()

        // 글로벌 앱 객체 설정
        GlobalApplication.setGlobalApplication(this)

        // 앱 생명주기 관찰
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            // 앱이 처음 시작될 때
            override fun onCreate(owner: LifecycleOwner) {
                super.onCreate(owner)
                Log.d(TAG, "앱 생성")
            }
            
            // 앱이 포그라운드로 나올 때
            override fun onStart(owner: LifecycleOwner) {
                super.onStart(owner)
                Log.d(TAG, "앱 시작 (포그라운드)")
            }

            // 앱이 포그라운드에서 활성화될 때
            override fun onResume(owner: LifecycleOwner) {
                super.onResume(owner)
                Log.d(TAG, "앱 포그라운드 활성화")
            }

            // 앱이 백그라운드로 가기 직전
            override fun onPause(owner: LifecycleOwner) {
                super.onPause(owner)
                Log.d(TAG, "앱 일시중지 (백그라운드로 전환 중)")
            }

            // 앱이 백그라운드로 갔을 때
            override fun onStop(owner: LifecycleOwner) {
                super.onStop(owner)
                Log.d(TAG, "앱 정지 (백그라운드)")
                
                // 앱이 백그라운드로 가면 모든 BLE 리소스 일시 중지
                GlobalBleManager.pause()
            }

            // 앱이 종료될 때
            override fun onDestroy(owner: LifecycleOwner) {
                super.onDestroy(owner)
                Log.d(TAG, "앱 종료")
                
                // 앱 종료 시 모든 BLE 리소스 완전 해제
                GlobalBleManager.releaseAllBleResources()
            }
        })
        
        // Wake Word Service 시작 준비 - 필요한 경우 서비스 시작
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startWakeWordService()
        }
    }
    
    // WakeWord 서비스 시작 메서드
    private fun startWakeWordService() {
        try {
            Intent(this, WakeWordService::class.java).also { intent ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent) // API 26 이상에서는 foreground service로 시작
                } else {
                    startService(intent)
                }
            }
            Log.d(TAG, "WakeWord 서비스 시작 요청 완료")
        } catch (e: Exception) {
            Log.e(TAG, "WakeWord 서비스 시작 실패: ${e.message}")
        }
    }
    
    companion object {
        private const val TAG = "LanternsApp"
    }
}
