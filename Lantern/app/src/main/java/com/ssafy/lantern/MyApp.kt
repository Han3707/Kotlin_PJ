// src/main/java/com/ssafy/lantern/MyApp.kt
package com.ssafy.lantern

import android.app.Application
import android.util.Log
import androidx.room.Room
import com.ssafy.lantern.data.db.ChatDatabase
import dagger.hilt.android.HiltAndroidApp
import no.nordicsemi.android.mesh.MeshManagerApi
import no.nordicsemi.android.mesh.transport.MeshMessage
import java.io.File

@HiltAndroidApp
class MyApp : Application() {
    private val TAG = "MyApp"

    // Room 데이터베이스 인스턴스 초기화
    val database: ChatDatabase by lazy {
        Room.databaseBuilder(
            applicationContext,
            ChatDatabase::class.java,
            "chat_database"
        ).build()
    }

    // Nordic Mesh API 인스턴스 초기화
    val meshManagerApi: MeshManagerApi by lazy {
        MeshManagerApi.getInstance(this)
    }

    override fun onCreate() {
        super.onCreate()
        
        // 메시 네트워크 로드
        try {
            // 메시 네트워크 파일이 있는지 확인하고, 없으면 생성
            val meshNetworkFile = File(filesDir, "mesh-network.json")
            if (!meshNetworkFile.exists()) {
                Log.d(TAG, "메시 네트워크 파일이 존재하지 않습니다. 새 네트워크를 생성합니다.")
                meshManagerApi.createMeshNetwork()
            } else {
                Log.d(TAG, "기존 메시 네트워크 파일을 로드합니다.")
                // 버전 3.3.4에서는 네트워크 로드 방식 간소화
                meshManagerApi.loadMeshNetwork()
            }
        } catch (e: Exception) {
            Log.e(TAG, "메시 네트워크 로드 실패: ${e.message}")
            // 실패 시 새 네트워크 생성
            meshManagerApi.createMeshNetwork()
        }
    }
}
