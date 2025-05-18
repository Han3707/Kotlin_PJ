package com.ssafy.lanterns.ui.screens.chat

// import androidx.annotation.DrawableRes // Removed, moved to ImageUtils
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.rememberNavController
import com.ssafy.lanterns.R
import com.ssafy.lanterns.ui.components.ProfileAvatar
import com.ssafy.lanterns.ui.navigation.AppDestinations
import com.ssafy.lanterns.ui.theme.LanternsTheme
import com.ssafy.lanterns.ui.theme.NavyTop
import com.ssafy.lanterns.ui.theme.NavyBottom
import com.ssafy.lanterns.ui.theme.TextWhite
import com.ssafy.lanterns.ui.theme.TextWhite70
import com.ssafy.lanterns.ui.theme.BleBlue1
import com.ssafy.lanterns.ui.theme.BleBlue2
import com.ssafy.lanterns.ui.theme.ConnectionNear
import com.ssafy.lanterns.ui.theme.ConnectionMedium
import com.ssafy.lanterns.ui.theme.ConnectionFar
import com.ssafy.lanterns.ui.theme.LanternYellow

// Data model for Chat List item
data class ChatItem(
    val id: Int,
    val name: String,
    val lastMessage: String,
    val time: String,
    val unread: Boolean = false,
    val distance: Float = 0f // 거리 정보 재추가 (미터 단위)
)

// Data model for Nearby section item (simplified)
data class NearbyUser(
    val id: Int, // 프로필 이미지용 ID
    val name: String = "사용자 $id",
    val distance: Float = 0f // 거리 정보 재추가 (미터 단위)
)

// Removed the local getProfileImageResId function, it's now in ImageUtils.kt

/**
 * 거리에 따른 색상을 반환합니다 (프로필 화면과 동일한 로직).
 */
private fun getDistanceColorForChat(distance: Float): Color {
    return when {
        distance < 100f -> ConnectionNear
        distance < 300f -> ConnectionMedium
        else -> ConnectionFar
    }
}

@Composable
fun ChatListScreen(
    paddingValues: PaddingValues = PaddingValues(),
    navController: NavController,
    viewModel: ChatListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    ChatListScreenContent(
        uiState = uiState,
        navController = navController,
        paddingValues = paddingValues
    )
}

@Composable
fun ChatListScreenContent(
    uiState: ChatListUiState,
    navController: NavController,
    paddingValues: PaddingValues = PaddingValues()
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(paddingValues)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.secondary)
                }
            } else if (uiState.errorMessage != null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("오류: ${uiState.errorMessage}", color = MaterialTheme.colorScheme.onBackground)
                }
            } else {
                Text(
                    text = "채팅",
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 16.dp)
                )

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    item {
                        PublicChatListItem(navController = navController)
                    }

                    if (uiState.chatList.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "채팅 기록이 없습니다.",
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    } else {
                        items(uiState.chatList) { chat ->
                            ChatListItem(chat = chat, navController = navController)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PublicChatListItem(navController: NavController) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable { navController.navigate(AppDestinations.PUBLIC_CHAT_ROUTE) },
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 공개 채팅 아이콘 (파란색 그라데이션 배경)
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(BleBlue1, BleBlue2)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                // 메가폰 아이콘
                Image(
                    painter = painterResource(id = R.drawable.public_1),
                    contentDescription = "공개 채팅방 아이콘",
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "모두의 광장",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = "주변의 모든 사람들과 대화해보세요",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun ChatListItem(chat: ChatItem, navController: NavController) {
    val connectionColor = getDistanceColorForChat(chat.distance)
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable {
                navController.navigate(
                    AppDestinations.DIRECT_CHAT_ROUTE
                        .replace("{userId}", chat.id.toString())
                )
            },
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 프로필 이미지 (프로필 화면으로 이동 가능)
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable {
                        // 프로필 화면으로 이동
                        val route = AppDestinations.PROFILE_ROUTE
                            .replace("{userId}", chat.id.toString())
                            .replace("{name}", chat.name)
                            .replace("{distance}", "${chat.distance.toInt()}m")
                        navController.navigate(route)
                    }
            ) {
                ProfileAvatar(
                    profileId = chat.id,
                    size = 48.dp,
                    hasBorder = true,
                    borderColor = connectionColor
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // 채팅 정보
            Column(
                modifier = Modifier.weight(1f)
            ) {
                // 이름과 시간
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = chat.name,
                        color = MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    
                    Text(
                        text = chat.time,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // 마지막 메시지와 거리
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = chat.lastMessage,
                        color = if (chat.unread) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (chat.unread) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    
                    // 거리 표시 (배경 추가)
                    Box(
                        modifier = Modifier
                            .background(
                                color = connectionColor.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "${chat.distance.toInt()}m",
                            color = connectionColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, name = "Chat List Screen - Populated")
@Composable
fun ChatListScreenPopulatedPreview() {
    val navController = rememberNavController()
    val sampleChats = listOf(
        ChatItem(1, "김철수", "안녕하세요! 반갑습니다.", "오후 2:30", unread = true, distance = 50f),
        ChatItem(2, "박영희", "다음에 또 만나요~~", "오전 11:15", distance = 250f),
        ChatItem(3, "이민준", "내일 시간 어떠세요?", "어제", unread = true, distance = 800f)
    )
    val uiState = ChatListUiState(
        isLoading = false,
        chatList = sampleChats,
        errorMessage = null
    )
    LanternsTheme {
        ChatListScreenContent(
            uiState = uiState,
            navController = navController,
            paddingValues = PaddingValues(0.dp)
        )
    }
}

@Preview(showBackground = true, name = "Chat List Screen - Empty")
@Composable
fun ChatListScreenEmptyPreview() {
    val navController = rememberNavController()
    val uiState = ChatListUiState(
        isLoading = false,
        chatList = emptyList(),
        errorMessage = null
    )
    LanternsTheme {
        ChatListScreenContent(
            uiState = uiState,
            navController = navController,
            paddingValues = PaddingValues(0.dp)
        )
    }
}

@Preview(showBackground = true, name = "Chat List Screen - Loading")
@Composable
fun ChatListScreenLoadingPreview() {
    val navController = rememberNavController()
    val uiState = ChatListUiState(isLoading = true)
    LanternsTheme {
        ChatListScreenContent(
            uiState = uiState,
            navController = navController,
            paddingValues = PaddingValues(0.dp)
        )
    }
}

@Preview(showBackground = true, name = "Chat List Screen - Error")
@Composable
fun ChatListScreenErrorPreview() {
    val navController = rememberNavController()
    val uiState = ChatListUiState(errorMessage = "채팅 목록을 불러오는데 실패했습니다.")
    LanternsTheme {
        ChatListScreenContent(
            uiState = uiState,
            navController = navController,
            paddingValues = PaddingValues(0.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ChatListItemPreview() {
    LanternsTheme {
        Surface(
            color = NavyTop
        ) {
            val dummyNavController = NavController(LocalContext.current)
            ChatListItem(
                chat = ChatItem(
                    id = 1,
                    name = "도경원",
                    lastMessage = "안녕하세요, 반갑습니다!",
                    time = "오전 10:30",
                    unread = true,
                    distance = 50f
                ),
                navController = dummyNavController
            )
        }
    }
}
