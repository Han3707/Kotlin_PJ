package com.ssafy.lanterns.ui.screens.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.ssafy.lanterns.R
import com.ssafy.lanterns.ui.theme.*
import com.ssafy.lanterns.utils.getConnectionColorByDistance

data class UserProfileData(
    val userId: String,
    val name: String,
    val distance: String,
    val profileImageResId: Int = R.drawable.default_profile // 기본 프로필 이미지 리소스 ID (resources 폴더에 추가 필요)
)

/**
 * 거리 문자열에서 숫자 부분만 추출하여 Float로 변환합니다.
 * 예: "123m" -> 123.0f
 */
private fun extractDistanceValue(distanceString: String): Float {
    return distanceString.replace(Regex("[^0-9]"), "").toFloatOrNull() ?: 0f
}

/**
 * 거리에 따른 색상을 반환합니다.
 */
private fun getDistanceColor(distanceString: String): Color {
    val distance = extractDistanceValue(distanceString)
    return when {
        distance < 100f -> ConnectionNear
        distance < 300f -> ConnectionMedium
        else -> ConnectionFar
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    navController: NavController,
    userData: UserProfileData
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("프로필", color = MaterialTheme.colorScheme.onBackground) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "뒤로가기",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // 프로필 내용
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(40.dp))
                
                // 프로필 이미지
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFFD700)) // 원형 배경색
                ) {
                    Image(
                        painter = painterResource(id = userData.profileImageResId),
                        contentDescription = "프로필 이미지",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 사용자 이름
                Text(
                    text = userData.name,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 거리 정보
                Box(
                    modifier = Modifier
                        .background(
                            color = getDistanceColor(userData.distance).copy(alpha = 0.2f),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = userData.distance,
                        fontSize = 20.sp,
                        color = getDistanceColor(userData.distance),
                        fontWeight = FontWeight.Medium
                    )
                }
                
                Spacer(modifier = Modifier.weight(1f))
                
                // 채팅하기 버튼
                Button(
                    onClick = {
                        // 채팅 화면으로 이동
                        navController.navigate("direct_chat/${userData.userId}")
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LanternYellow
                    )
                ) {
                    Text(
                        text = "채팅하기",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.Black
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp)) // 하단 여백
            }
        }
    }
}

// 테스트를 위한 미리보기
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreenPreview() {
    LanternsTheme {
        ProfileScreen(
            navController = androidx.navigation.compose.rememberNavController(),
            userData = UserProfileData(
                userId = "1",
                name = "도경원",
                distance = "35m"
            )
        )
    }
} 