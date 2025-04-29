package com.example.bletest.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * BleService와의 연결을 관리하는 클래스
 */
object BleServiceConnection : ServiceConnection {
    private const val TAG = "BleServiceConnection"
    
    private var serviceBinder: BleService.LocalBinder? = null
    
    // 서비스 연결 상태를 나타내는 Flow
    private val _serviceFlow = MutableStateFlow<BleService?>(null)
    val serviceFlow: StateFlow<BleService?> = _serviceFlow.asStateFlow()
    
    /**
     * 서비스 인스턴스 가져오기
     */
    fun getService(): BleService? {
        return serviceBinder?.getService()
    }
    
    /**
     * 서비스 연결 성공 시 호출
     */
    override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
        Log.d(TAG, "BleService 바인딩 성공")
        
        if (binder is BleService.LocalBinder) {
            serviceBinder = binder
            _serviceFlow.value = binder.getService()
        }
    }
    
    /**
     * 서비스 연결 해제 시 호출
     */
    override fun onServiceDisconnected(name: ComponentName?) {
        Log.d(TAG, "BleService 바인딩 해제")
        serviceBinder = null
        _serviceFlow.value = null
    }
    
    /**
     * 앱에서 서비스 바인딩
     */
    fun bindService(context: Context): Boolean {
        Log.d(TAG, "BleService 바인딩 시도")
        
        val intent = Intent(context, BleService::class.java)
        return try {
            context.bindService(intent, this, Context.BIND_AUTO_CREATE)
            true
        } catch (e: Exception) {
            Log.e(TAG, "서비스 바인딩 실패", e)
            false
        }
    }
    
    /**
     * 앱에서 서비스 바인딩 해제
     */
    fun unbindService(context: Context) {
        Log.d(TAG, "BleService 바인딩 해제 시도")
        
        try {
            context.unbindService(this)
            serviceBinder = null
            _serviceFlow.value = null
        } catch (e: Exception) {
            Log.e(TAG, "서비스 바인딩 해제 실패", e)
        }
    }
} 