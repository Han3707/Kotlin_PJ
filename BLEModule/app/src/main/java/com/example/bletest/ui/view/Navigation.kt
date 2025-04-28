package com.example.bletest.ui.view

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.bletest.ui.view.chat.ChatScreen
import com.example.bletest.ui.view.main.MainScreen

/**
 * 앱 내 네비게이션 경로
 */
object NavRoute {
    const val MAIN = "main"
    const val CHAT = "chat"
}

/**
 * 앱 네비게이션
 */
@Composable
fun BleMeshNavigation() {
    val navController = rememberNavController()
    
    NavHost(
        navController = navController,
        startDestination = NavRoute.MAIN
    ) {
        // 메인 화면
        composable(NavRoute.MAIN) {
            MainScreen(
                onNavigateToChat = {
                    navController.navigate(NavRoute.CHAT)
                }
            )
        }
        
        // 채팅 화면
        composable(NavRoute.CHAT) {
            ChatScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
} 