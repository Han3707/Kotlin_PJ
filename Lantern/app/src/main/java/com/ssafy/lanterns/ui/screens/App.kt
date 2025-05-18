package com.ssafy.lanterns.ui.screens

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.ssafy.lanterns.ui.navigation.AppNavigation
import com.ssafy.lanterns.ui.screens.ondevice.OnDeviceAIDialog
import com.ssafy.lanterns.ui.screens.main.MainViewModel

/**
 * 앱의 루트 컴포넌트
 */
@Composable
fun App() {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        AppContent()
    }
}

/**
 * 실제 UI와 AI 오버레이를 그리는 영역
 */
@Composable
fun AppContent(
    mainViewModel: MainViewModel = hiltViewModel()
) {
    // "헤이 랜턴" 감지 여부
    val aiActive by mainViewModel.aiActive.collectAsState()
    Log.d("AppContent", "aiActive 상태: $aiActive")

    Box(modifier = Modifier.fillMaxSize()) {
        // 메인 네비게이션
        AppNavigation(modifier = Modifier.fillMaxSize())

        // AI 다이얼로그 오버레이
        if (aiActive) {
            Log.d("AppContent", "OnDeviceAIDialog 표시")
            OnDeviceAIDialog(
                onDismiss = {
                    Log.d("AppContent", "AI 다이얼로그 닫힘 → deactivateAI()")
                    mainViewModel.deactivateAI()
                }
            )
        }
    }
}
