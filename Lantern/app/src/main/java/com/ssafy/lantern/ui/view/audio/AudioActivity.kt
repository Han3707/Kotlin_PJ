package com.ssafy.lantern.ui.view.audio

import android.bluetooth.le.ScanCallback
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.ui.Modifier
import com.ssafy.lantern.data.source.ble.scanner.ScannerManager
import com.ssafy.lantern.ui.screens.call.OngoingCallScreen
import com.ssafy.lantern.ui.theme.LanternTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AudioActivity : ComponentActivity() {
    
    private var _scanner: ScannerManager? = null
    // lateinit var scanner: ScannerManager - 이것이 문제를 발생시킴

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            // Scanner를 일반 변수로 초기화
            _scanner = ScannerManager(this) { result ->
                // 스캔 결과 처리
                Log.d("AudioActivity", "Scan result found: ${result.device.address}")
            }
        } catch (e: Exception) {
            Log.e("AudioActivity", "Scanner 초기화 실패: ${e.message}")
        }
        
        setContent {
            LanternTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background
                ) {
                    OngoingCallScreen(
                        callerName = "사용자",
                        onEndCallClick = { finish() }
                    )
                }
            }
        }
    }
    
    override fun onDestroy() {
        try {
            // 여기서 null 체크를 하여 안전하게 호출
            _scanner?.stopScanning()
        } catch (e: Exception) {
            Log.e("AudioActivity", "Scanner 정리 실패: ${e.message}")
        }
        _scanner = null
        super.onDestroy()
    }
} 