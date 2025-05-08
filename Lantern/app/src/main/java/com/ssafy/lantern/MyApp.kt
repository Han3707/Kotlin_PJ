// src/main/java/com/ssafy/lantern/MyApp.kt
package com.ssafy.lantern

import android.app.Application
import android.util.Log
import androidx.room.Room
import com.ssafy.lantern.data.database.AppDatabase
import dagger.hilt.android.HiltAndroidApp

/**
 * 애플리케이션 클래스
 */
@HiltAndroidApp
class MyApp : Application() {
    private val TAG = "MyApp"

    // Room 데이터베이스 인스턴스 초기화
    val database: AppDatabase by lazy {
        Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "lantern.db"
        ).build()
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "애플리케이션 초기화")
    }
}
