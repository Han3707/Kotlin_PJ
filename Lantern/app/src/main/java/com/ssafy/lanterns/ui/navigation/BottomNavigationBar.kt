package com.ssafy.lanterns.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier

// 하단 네비게이션 아이템 정의
sealed class BottomNavItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    object Call : BottomNavItem(
        AppDestinations.CALL_HISTORY_ROUTE,
        "통화",
        Icons.Filled.Call,
        Icons.Outlined.Call
    )
    
    object Chat : BottomNavItem(
        AppDestinations.HOME_ROUTE, 
        "채팅", 
        Icons.Filled.Chat,
        Icons.Outlined.Chat
    )
    
    object Detect : BottomNavItem(
        AppDestinations.MAIN_SCREEN_ROUTE, 
        "탐지", 
        Icons.Filled.Radar,
        Icons.Outlined.Radar
    )
    
    object Settings : BottomNavItem(
        AppDestinations.MYPAGE_ROUTE, 
        "설정", 
        Icons.Filled.Settings,
        Icons.Outlined.Settings
    )
}

@Composable
fun AppBottomNavigationBar(navController: NavController) {
    val items = listOf(
        BottomNavItem.Call,
        BottomNavItem.Chat,
        BottomNavItem.Detect,
        BottomNavItem.Settings
    )

    // 현재 백스택 항목 가져오기
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    // 현재 화면 경로 가져오기
    val currentDestination = navBackStackEntry?.destination
    
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        tonalElevation = 10.dp,
        windowInsets = WindowInsets.navigationBars, // 시스템 네비게이션 바의 인셋 사용
        modifier = Modifier.padding(top = 4.dp) // 위쪽에 작은 패딩 추가
    ) {
        items.forEach { item ->
            val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true
            
            NavigationBarItem(
                icon = { 
                    Icon(
                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                        contentDescription = item.label
                    )
                },
                label = { 
                    Text(
                        text = item.label,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 12.sp
                    )
                },
                selected = selected,
                onClick = {
                    navController.navigate(item.route) {
                        // 하단 네비게이션의 백스택 동작 설정
                        // 시작 목적지까지 팝업하여 중복 스택 방지
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        // 동일한 항목 재선택 시 새 화면 생성 방지
                        launchSingleTop = true
                        // 상태 저장
                        restoreState = true
                    }
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.secondary,
                    selectedTextColor = MaterialTheme.colorScheme.secondary,
                    indicatorColor = MaterialTheme.colorScheme.surface,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    unselectedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            )
        }
    }
} 