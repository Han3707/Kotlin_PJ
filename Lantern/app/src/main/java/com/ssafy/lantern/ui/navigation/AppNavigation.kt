package com.ssafy.lantern.ui.navigation

import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ssafy.lantern.ui.common.MainScaffold // MainScaffold 임포트
import com.ssafy.lantern.ui.screens.call.FriendListScreen
import com.ssafy.lantern.ui.screens.call.IncomingCallScreen
import com.ssafy.lantern.ui.screens.call.OngoingCallScreen
import com.ssafy.lantern.ui.screens.chat.ChatScreen // ChatScreen 임포트
import com.ssafy.lantern.ui.screens.chat.ChatListScreen // ChatListScreen 임포트
import com.ssafy.lantern.ui.screens.chat.DirectChatScreen
import com.ssafy.lantern.ui.screens.chat.PublicChatScreen
import com.ssafy.lantern.ui.screens.login.LoginScreen
import com.ssafy.lantern.ui.screens.mypage.MyPageScreen
import com.ssafy.lantern.ui.screens.signup.SignupScreen

// 네비게이션 라우트 정의
object AppDestinations {
    const val LOGIN_ROUTE = "login"
    const val SIGNUP_ROUTE = "signup"
    const val CHAT_ROUTE = "chat" // 채팅 상세 화면 라우트
    const val MYPAGE_ROUTE = "mypage"
    const val FRIENDLIST_ROUTE = "friendlist" // 통화 목록 (하단 탭)
    const val INCOMING_CALL_ROUTE = "incomingcall"
    const val ONGOING_CALL_ROUTE = "ongoingcall"
    const val HOME_ROUTE = "home" // 채팅 목록 (하단 탭) - 기존 메인 역할

    // 새로운 채팅 라우트
    const val PUBLIC_CHAT_ROUTE = "public_chat"
    const val DIRECT_CHAT_ROUTE = "direct_chat/{userId}" // 사용자 ID 파라미터 포함
    const val DIRECT_CHAT_ARG_USER_ID = "userId"
}

/**
 * 앱 전체 네비게이션 구조 (Jetpack Navigation Compose 사용)
 */
@Composable
fun AppNavigation() {
    // NavController 생성
    val navController = rememberNavController()

    // NavHost 설정
    NavHost(navController = navController, startDestination = AppDestinations.LOGIN_ROUTE) {
        // 로그인 화면
        composable(AppDestinations.LOGIN_ROUTE) {
            LoginScreen(
                onSignUpClick = { navController.navigate(AppDestinations.SIGNUP_ROUTE) },
                // 아래 클릭 핸들러들은 로그인 성공 시 이동으로 대체될 수 있음
                onMyPageClick = { navController.navigate(AppDestinations.MYPAGE_ROUTE) },
                onFriendListClick = { navController.navigate(AppDestinations.FRIENDLIST_ROUTE) },
                onIncomingCallClick = { navController.navigate(AppDestinations.INCOMING_CALL_ROUTE) }, // 테스트용?
                onHomeClick = { navController.navigate(AppDestinations.HOME_ROUTE) }, // 주석 해제
                onLoginSuccess = {
                    // 로그인 성공 시 HOME_ROUTE (채팅 목록)으로 이동하고 로그인 화면은 백스택에서 제거
                    navController.navigate(AppDestinations.HOME_ROUTE) {
                        popUpTo(AppDestinations.LOGIN_ROUTE) { inclusive = true }
                    }
                }
            )
        }

        // 회원가입 화면
        composable(AppDestinations.SIGNUP_ROUTE) {
            SignupScreen(
                onBackToLoginClick = { navController.popBackStack() }
            )
        }

        // 채팅 상세 화면 (개별 채팅방) - 하단 네비게이션 없음
        composable(AppDestinations.CHAT_ROUTE) {
            // ChatScreen은 MainScaffold 밖에 있어야 하단 네비게이션이 보이지 않음
            ChatScreen(
                // 필요 시 navController, chatId 등 전달
            )
        }

        // --- 하단 네비게이션 바가 있는 화면들 ---

        // 홈(채팅 목록) 화면 (네비게이션 바 포함)
        composable(AppDestinations.HOME_ROUTE) {
            MainScaffold(navController = navController) { paddingValues ->
                ChatListScreen(
                    paddingValues = paddingValues,
                    navController = navController, // NavController 전달
                    onChatClick = { navController.navigate(AppDestinations.CHAT_ROUTE) }
                )
            }
        }

        // 통화 목록 화면 (네비게이션 바 포함)
        composable(AppDestinations.FRIENDLIST_ROUTE) {
            MainScaffold(navController = navController) { paddingValues ->
                FriendListScreen(
                    onBackClick = { navController.popBackStack() }, // 현재 구조상 필요 없을 수 있음
                    onCallItemClick = { navController.navigate(AppDestinations.ONGOING_CALL_ROUTE) }, // 통화 아이템 클릭 시 통화 중 화면으로 이동 (예시)
                    onProfileClick = { navController.navigate(AppDestinations.MYPAGE_ROUTE) }, // 프로필 클릭 시 마이페이지로 이동 (예시)
                    paddingValues = paddingValues // Scaffold 패딩 전달
                )
            }
        }

        // 마이페이지 화면 (네비게이션 바 포함)
        composable(AppDestinations.MYPAGE_ROUTE) {
            MainScaffold(navController = navController) { paddingValues ->
                MyPageScreen(
                    popBackStack = { navController.popBackStack() }, // 뒤로가기
                    onNavigateToLogin = { // 로그아웃 처리
                        navController.navigate(AppDestinations.LOGIN_ROUTE) {
                            popUpTo(navController.graph.id) { // 전체 백스택 클리어
                                inclusive = true
                            }
                            launchSingleTop = true // 로그인 화면 중복 생성 방지
                        }
                    },
                    paddingValues = paddingValues // Scaffold 패딩 전달
                )
            }
        }

        // --- 하단 네비게이션 바가 없는 화면들 (계속) ---

        // 전화 수신 화면
        composable(AppDestinations.INCOMING_CALL_ROUTE) {
             IncomingCallScreen(
                 callerName = "임시 발신자", // 실제 데이터 전달 필요
                 onRejectClick = { navController.popBackStack() }, // 거절 시 이전 화면으로
                 onAcceptClick = { // 수락 시 통화 중 화면으로 이동하고 수신 화면은 제거
                     navController.navigate(AppDestinations.ONGOING_CALL_ROUTE) {
                         popUpTo(AppDestinations.INCOMING_CALL_ROUTE) { inclusive = true }
                     }
                 }
             )
        }

        // 통화 중 화면
        composable(AppDestinations.ONGOING_CALL_ROUTE) {
            OngoingCallScreen(
                 callerName = "임시 발신자", // 실제 데이터 전달 필요
                 onEndCallClick = {
                     // 통화 종료 후 HOME (채팅 목록)으로 이동 (백스택 관리 필요 시 조정)
                     // 예: 통화 시작 전 화면으로 돌아가려면 popBackStack() 사용
                     navController.navigate(AppDestinations.HOME_ROUTE){
                         // 필요에 따라 popUpTo 등을 사용하여 백스택 정리
                         popUpTo(AppDestinations.LOGIN_ROUTE) // 로그인 이후의 모든 화면 제거 (예시)
                     }
                 }
             )
        }

        // 공용 채팅 화면
        composable(AppDestinations.PUBLIC_CHAT_ROUTE) {
            PublicChatScreen(
                // 필요 시 NavController 전달
                 navController = navController // NavController 전달
            )
        }

        // 1:1 채팅 화면 (userId 인자 받음)
        composable(
            route = AppDestinations.DIRECT_CHAT_ROUTE,
            arguments = listOf(navArgument(AppDestinations.DIRECT_CHAT_ARG_USER_ID) { type = NavType.StringType }) // Int 타입이면 NavType.IntType
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getString(AppDestinations.DIRECT_CHAT_ARG_USER_ID)
            if (userId != null) {
                DirectChatScreen(
                     userId = userId,
                     navController = navController // NavController 전달
                )
            } else {
                Text("Error: User ID not found.")
            }
        }

        // 다른 화면들에 대한 composable 추가 가능
    }
}