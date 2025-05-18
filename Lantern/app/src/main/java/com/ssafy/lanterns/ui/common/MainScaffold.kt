package com.ssafy.lanterns.ui.common

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.ssafy.lanterns.ui.navigation.AppBottomNavigationBar
import com.ssafy.lanterns.ui.theme.NavyTop

/**
 * 앱의 주요 화면에 공통으로 사용되는 스캐폴드 컴포넌트
 * 하단 네비게이션 바를 포함하고 자식 콘텐츠에 적절한 패딩 값을 전달합니다.
 *
 * @param navController 네비게이션 컨트롤러
 * @param content 스캐폴드 내부에 표시될 콘텐츠
 */
@Composable
fun MainScaffold(
    navController: NavHostController,
    content: @Composable (PaddingValues) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Scaffold(
            bottomBar = { 
                AppBottomNavigationBar(navController = navController) 
            },
            containerColor = MaterialTheme.colorScheme.background // 배경 색상을 테마에 맞게 설정
        ) { innerPadding ->
            // 패딩을 줄여서 콘텐츠에 전달
            val adjustedPadding = PaddingValues(
                top = innerPadding.calculateTopPadding(),
                bottom = innerPadding.calculateBottomPadding() * 0.7f, // 하단 패딩 절반으로 줄임
                start = innerPadding.calculateStartPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
                end = innerPadding.calculateEndPadding(androidx.compose.ui.unit.LayoutDirection.Ltr)
            )
            
            content(adjustedPadding)
        }
    }
} 