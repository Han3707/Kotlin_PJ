package com.ssafy.lantern.ui.navigation

import androidx.compose.material.BottomNavigation
import androidx.compose.material.BottomNavigationItem
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call // 또는 History, List 등 적절한 아이콘
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import com.ssafy.lantern.ui.navigation.AppDestinations

// 하단 네비게이션 아이템 정의
sealed class BottomNavItem(
    val route: String,
    val label: String, // 문자열 리소스 대신 직접 사용
    val icon: ImageVector
) {
    object FriendList : BottomNavItem(AppDestinations.FRIENDLIST_ROUTE, "통화목록", Icons.Filled.Call)
    object Home : BottomNavItem(AppDestinations.HOME_ROUTE, "홈", Icons.Filled.Home)
    object MyPage : BottomNavItem(AppDestinations.MYPAGE_ROUTE, "마이페이지", Icons.Filled.Person)
}

@Composable
fun AppBottomNavigationBar(navController: NavController) {
    val items = listOf(
        BottomNavItem.FriendList,
        BottomNavItem.Home,
        BottomNavItem.MyPage
    )

    // 현재 백스택 항목 가져오기
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    // 현재 화면 경로 가져오기
    val currentDestination = navBackStackEntry?.destination

    val bottomBarVisible = currentDestination?.route in items.map { it.route }

    // 특정 화면에서는 BottomNavigation 숨기기 (NavHost에서 관리하므로 불필요할 수 있음)
    // if (!bottomBarVisible) return

    BottomNavigation(
        // TODO: 앱 테마에 맞는 색상 적용
        backgroundColor = Color.Black,
        contentColor = Color(0xFFCCCCCC) // 비선택 아이콘 색상
    ) {
        items.forEach { screen ->
            val isSelected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
            BottomNavigationItem(
                icon = { Icon(screen.icon, contentDescription = screen.label) },
                label = { Text(screen.label) },
                selected = isSelected,
                selectedContentColor = Color(0xFFFFD700), // 선택된 아이템 색상 (테마에 맞게 조정)
                unselectedContentColor = Color(0xFFCCCCCC), // 비선택 아이템 색상
                onClick = {
                    navController.navigate(screen.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
    }
} 