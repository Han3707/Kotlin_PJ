package com.ssafy.lantern.ui.common // 또는 다른 적절한 패키지

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.Scaffold
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import com.ssafy.lantern.ui.navigation.AppBottomNavigationBar

@Composable
fun MainScaffold(
    navController: NavHostController,
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        bottomBar = { AppBottomNavigationBar(navController = navController) }
    ) { innerPadding ->
        content(innerPadding) // Scaffold 내부 패딩을 콘텐츠에 전달
    }
} 