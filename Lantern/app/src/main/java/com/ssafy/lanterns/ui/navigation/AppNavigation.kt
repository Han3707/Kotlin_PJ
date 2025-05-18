package com.ssafy.lanterns.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ssafy.lanterns.ui.common.MainScaffold
import com.ssafy.lanterns.ui.screens.call.IncomingCallScreen
import com.ssafy.lanterns.ui.screens.call.OngoingCallScreen
import com.ssafy.lanterns.ui.screens.call.OutgoingCallScreen
import com.ssafy.lanterns.ui.screens.chat.ChatListScreen
import com.ssafy.lanterns.ui.screens.chat.DirectChatScreen
import com.ssafy.lanterns.ui.screens.chat.PublicChatScreen
import com.ssafy.lanterns.ui.screens.common.ProfileScreen
import com.ssafy.lanterns.ui.screens.common.UserProfileData
import com.ssafy.lanterns.ui.screens.login.LoginScreen
import com.ssafy.lanterns.ui.screens.main.MainScreen
import com.ssafy.lanterns.ui.screens.mypage.MyPageScreen
import com.ssafy.lanterns.ui.screens.ondevice.OnDeviceAIScreen
import com.ssafy.lanterns.ui.screens.splash.SplashScreen
import androidx.compose.ui.Modifier
import com.ssafy.lanterns.ui.screens.call.CallHistoryScreen

// 네비게이션 라우트 정의
object AppDestinations {
    const val SPLASH_ROUTE = "splash"
    const val LOGIN_ROUTE = "login"
    const val MYPAGE_ROUTE = "mypage"
    const val FRIENDLIST_ROUTE = "friendlist"
    const val INCOMING_CALL_ROUTE = "incomingcall"
    const val ONGOING_CALL_ROUTE = "ongoingcall"
    const val OUTGOING_CALL_ROUTE = "outgoingcall/{receiverId}"
    const val OUTGOING_CALL_ARG_RECEIVER_ID = "receiverId"
    const val HOME_ROUTE = "home"
    const val MAIN_SCREEN_ROUTE = "main_screen"
    const val ONDEVICE_AI_ROUTE = "ondevice_ai"

    const val PUBLIC_CHAT_ROUTE = "public_chat"
    const val DIRECT_CHAT_ROUTE = "direct_chat/{userId}"
    const val DIRECT_CHAT_ARG_USER_ID = "userId"

    const val PROFILE_ROUTE = "profile/{userId}/{name}/{distance}"
    const val PROFILE_ARG_USER_ID = "userId"
    const val PROFILE_ARG_NAME = "name"
    const val PROFILE_ARG_DISTANCE = "distance"

    const val CALL_HISTORY_ROUTE = "call_history"   
}

/**
 * 앱 전체 네비게이션 구조
 */
@Composable
fun AppNavigation(modifier: Modifier = Modifier) {
    // NavController 생성
    val navController = rememberNavController()

    // NavHost 설정 - 시작 화면을 스플래시 화면으로 변경
    NavHost(
        navController = navController,
        startDestination = AppDestinations.SPLASH_ROUTE,
        modifier = modifier.fillMaxSize()
    ) {
        // 스플래시 스크린 라우트
        composable(route = AppDestinations.SPLASH_ROUTE) {
            SplashScreen { isLoggedIn ->
                // 로그인 상태에 따라 다음 화면으로 이동
                if (isLoggedIn) {
                    navController.navigate(AppDestinations.MAIN_SCREEN_ROUTE) {
                        popUpTo(AppDestinations.SPLASH_ROUTE) { inclusive = true }
                    }
                } else {
                    navController.navigate(AppDestinations.LOGIN_ROUTE) {
                        popUpTo(AppDestinations.SPLASH_ROUTE) { inclusive = true }
                    }
                }
            }
        }
        
        // 로그인 스크린 라우트
        composable(route = AppDestinations.LOGIN_ROUTE) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(AppDestinations.MAIN_SCREEN_ROUTE) {
                        popUpTo(AppDestinations.LOGIN_ROUTE) { inclusive = true }
                    }
                }
            )
        }

        // 메인 화면
        composable(AppDestinations.MAIN_SCREEN_ROUTE) {
            MainScaffold(navController = navController) { paddingValues ->
                MainScreen(paddingValues = paddingValues, navController = navController)
            }
        }

        // 통화 기록 화면
        composable(AppDestinations.CALL_HISTORY_ROUTE) {
            MainScaffold(navController = navController) { paddingValues ->
                CallHistoryScreen(
                    navController = navController,
                    paddingValues = paddingValues,
                    onCallItemClick = { callerId ->
                        navController.navigate(AppDestinations.OUTGOING_CALL_ROUTE.replace("{receiverId}", callerId.toString()))
                    }
                )
            }
        }


        // 마이페이지 화면
        composable(AppDestinations.MYPAGE_ROUTE) {
            MainScaffold(navController = navController) { paddingValues ->
                MyPageScreen(
                    onNavigateToLogin = {
                        navController.navigate(AppDestinations.LOGIN_ROUTE) {
                            popUpTo(navController.graph.id) {
                                inclusive = true
                            }
                            launchSingleTop = true
                        }
                    },
                    paddingValues = paddingValues
                )
            }
        }

        // 전화 수신 화면
        composable(AppDestinations.INCOMING_CALL_ROUTE) {
             IncomingCallScreen(
                 callerName = "임시 발신자",
                 onRejectClick = { navController.popBackStack() },
                 onAcceptClick = {
                     navController.navigate(AppDestinations.ONGOING_CALL_ROUTE) {
                         popUpTo(AppDestinations.INCOMING_CALL_ROUTE) { inclusive = true }
                     }
                 }
             )
        }

        // 통화 거는 중 화면
        composable(
            route = AppDestinations.OUTGOING_CALL_ROUTE,
            arguments = listOf(navArgument(AppDestinations.OUTGOING_CALL_ARG_RECEIVER_ID) { type = NavType.StringType })
        ) { backStackEntry ->
            val receiverId = backStackEntry.arguments?.getString(AppDestinations.OUTGOING_CALL_ARG_RECEIVER_ID)
            if (receiverId != null) {
                OutgoingCallScreen(
                    receiverName = "수신자",
                    receiverId = receiverId.toIntOrNull() ?: 1,
                    onCancelClick = {
                        navController.popBackStack()
                    }
                )
            } else {
                Text("Error: Receiver ID not found.")
            }
        }

        // 통화 중 화면
        composable(AppDestinations.ONGOING_CALL_ROUTE) {
            OngoingCallScreen(
                 callerName = "임시 발신자",
                 onEndCallClick = {
                     navController.navigate(AppDestinations.MAIN_SCREEN_ROUTE){
                         popUpTo(AppDestinations.LOGIN_ROUTE)
                     }
                 }
             )
        }

        // 채팅 화면
        composable(AppDestinations.HOME_ROUTE) {
            MainScaffold(navController = navController) { paddingValues ->
                ChatListScreen(
                    paddingValues = paddingValues,
                    navController = navController
                )
            }
        }

        // 공용 채팅 화면
        composable(AppDestinations.PUBLIC_CHAT_ROUTE) {
            PublicChatScreen(
                navController = navController
            )
        }

        // 1:1 채팅 화면
        composable(
            route = AppDestinations.DIRECT_CHAT_ROUTE,
            arguments = listOf(navArgument(AppDestinations.DIRECT_CHAT_ARG_USER_ID) { type = NavType.StringType })
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getString(AppDestinations.DIRECT_CHAT_ARG_USER_ID)
            if (userId != null) {
                DirectChatScreen(
                     userId = userId,
                     navController = navController
                )
            } else {
                Text("Error: User ID not found.")
            }
        }

        // 프로필 화면
        composable(
            route = AppDestinations.PROFILE_ROUTE,
            arguments = listOf(
                navArgument(AppDestinations.PROFILE_ARG_USER_ID) { type = NavType.StringType },
                navArgument(AppDestinations.PROFILE_ARG_NAME) { type = NavType.StringType },
                navArgument(AppDestinations.PROFILE_ARG_DISTANCE) { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getString(AppDestinations.PROFILE_ARG_USER_ID) ?: ""
            val name = backStackEntry.arguments?.getString(AppDestinations.PROFILE_ARG_NAME) ?: ""
            val distance = backStackEntry.arguments?.getString(AppDestinations.PROFILE_ARG_DISTANCE) ?: ""
            
            ProfileScreen(
                navController = navController,
                userData = UserProfileData(
                    userId = userId,
                    name = name,
                    distance = distance
                )
            )
        }

        // 온디바이스 AI 화면
        composable(AppDestinations.ONDEVICE_AI_ROUTE) {
            OnDeviceAIScreen(
                onDismiss = { navController.popBackStack() }
            )
        }
    }
}