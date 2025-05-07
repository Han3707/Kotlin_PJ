package com.ssafy.lantern.ui.screens.chat

// import androidx.annotation.DrawableRes // Removed, moved to ImageUtils
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forum // Icon for Public Chat
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.modifier.modifierLocalConsumer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.lantern.R
import com.ssafy.lantern.ui.components.ProfileAvatar
import com.ssafy.lantern.ui.theme.LanternTheme
import com.ssafy.lantern.ui.util.getProfileImageResId // Import the utility function
import androidx.navigation.NavController
import com.ssafy.lantern.ui.navigation.AppDestinations
import androidx.compose.ui.platform.LocalContext

// Data model for Chat List item
data class ChatItem(
    val id: Int,
    val name: String,
    val lastMessage: String,
    val time: String,
    val unread: Boolean = false
)

// Data model for Nearby section item (simplified)
data class NearbyUser(
    val id: Int // Use ID to fetch profile image
)

// Removed the local getProfileImageResId function, it's now in ImageUtils.kt

@Composable
fun ChatListScreen(
    paddingValues: PaddingValues = PaddingValues(),
    navController: NavController,
    onChatClick: () -> Unit = {} // BLE 채팅 클릭 이벤트
) {
    // Dummy data
    val nearbyUsers = remember { List(8) { NearbyUser(id = it + 1) } } // 8 nearby users
    val chatList = remember {
        listOf(
            ChatItem(1, "내가진짜도경원", "와, 와이파이 없이 대화 신기하당 ㅎㅎ", "11:20 am", true),
            ChatItem(2, "귀요미", "난 귀요미", "10:20 am"),
            ChatItem(3, "백성욱", "메시지 입력해봐..", "어제"),
            ChatItem(4, "박수민", "나만의 채로서 일타강사.", "어제"),
            ChatItem(5, "천세욱1", "여긴 어디? 난 누구?", "어제"),
            ChatItem(6, "천세욱2", "여긴 어디? 난 누구?", "어제")
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background)
            .padding(paddingValues) // Apply padding from Scaffold
    ) {
        // Search Bar (Removed as per user's last change, keep it removed)

        // Nearby Section
        NearbySection(nearbyUsers = nearbyUsers)

        Spacer(modifier = Modifier.height(24.dp)) // Increased space between Nearby and Chat sections

        // Chat Section Label
        Text(
            text = "채팅",
            color = MaterialTheme.colors.onBackground,
            style = MaterialTheme.typography.h6.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(start = 16.dp, bottom = 16.dp) // Style matches "주변"
        )

        // Chat List (including Public Chat at the top)
        LazyColumn(
            modifier = Modifier.weight(1f) // Takes remaining space
        ) {
            // BLE 채팅 항목 (모든 기능 통합)
            item {
                BleChatListItem(onClick = onChatClick)
                Divider(
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.12f),
                    thickness = 1.dp,
                    modifier = Modifier.padding(start = 76.dp, end = 16.dp)
                )
            }
            
            // Public Chat Item
            item {
                PublicChatListItem(navController = navController)
                Divider(
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.12f),
                    thickness = 1.dp,
                    // Indent divider like other items, starting after the icon area
                    modifier = Modifier.padding(start = 76.dp, end = 16.dp)
                )
            }

            // Private Chat List Items
            items(chatList) { chat ->
                ChatListItem(chat = chat, navController = navController)
                Divider(
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.12f),
                    thickness = 1.dp,
                    modifier = Modifier.padding(start = 76.dp, end = 16.dp) // Indent divider
                )
            }
        }
    }
}

@Composable
fun NearbySection(nearbyUsers: List<NearbyUser>) {
    Column(modifier = Modifier.padding(top = 16.dp)) { // Keep top padding for space after potential search bar
        Text(
            text = "주변",
            color = MaterialTheme.colors.onBackground,
            style = MaterialTheme.typography.h6.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(start = 16.dp, bottom = 16.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(nearbyUsers) { user ->
                NearbyUserAvatar(user = user)
            }
        }
    }
}

@Composable
fun NearbyUserAvatar(user: NearbyUser) {
    ProfileAvatar(
        profileId = user.id,
        size = 64.dp, // Nearby 섹션은 더 큰 아바타 사용
        onClick = { /* TODO: Handle click on nearby user */ }
    )
}

// New Composable for the Public Chat list item
@Composable
fun PublicChatListItem(navController: NavController) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { navController.navigate(AppDestinations.PUBLIC_CHAT_ROUTE) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // White circular background Box
        Box(
            modifier = Modifier
                .size(48.dp) // Same size as profile images
                .clip(CircleShape)
                .background(Color.White), // White background
            contentAlignment = Alignment.Center // Center the image inside
        ) {
            // Image for Public Chat (Megaphone)
            Image(
                painter = painterResource(id = R.drawable.public_chat_icon),
                contentDescription = "Public Chat Icon",
                // Adjust size or padding as needed for the megaphone image within the circle
                modifier = Modifier
                    .size(32.dp) // Make image smaller than the 48dp circle
                    // .padding(8.dp) // Alternative: Add padding around the image
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Public Chat Title and Description
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "공용 채팅",
                color = MaterialTheme.colors.onSurface,
                style = MaterialTheme.typography.subtitle1.copy(fontWeight = FontWeight.Bold),
                fontSize = 16.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "주변 사용자들과 대화해보세요!", // Example description
                color = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium),
                style = MaterialTheme.typography.body2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        // No timestamp or unread indicator needed for the public chat item
    }
}

// ChatListItem Composable remains the same
@Composable
fun ChatListItem(chat: ChatItem, navController: NavController) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                // userId 파라미터에 chat.id 를 문자열로 변환하여 전달 (라우트 정의에 맞게)
                navController.navigate("${AppDestinations.DIRECT_CHAT_ROUTE.substringBefore('{')}${chat.id}")
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ProfileAvatar(profileId = chat.id) // ProfileAvatar 사용
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = chat.name,
                color = MaterialTheme.colors.onSurface,
                style = MaterialTheme.typography.subtitle1.copy(fontWeight = FontWeight.Bold),
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = chat.lastMessage,
                color = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium),
                style = MaterialTheme.typography.body2,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = chat.time,
                color = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium),
                style = MaterialTheme.typography.caption,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .height(8.dp)
                    .width(8.dp)
            ) {
                if (chat.unread) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(Color(0xFFFFC107), CircleShape)
                    )
                }
            }
        }
    }
}

// BLE 채팅 목록 아이템 (일반 및 Mesh 통합)
@Composable
fun BleChatListItem(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 아이콘 배경
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color.Blue.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            // BLE 아이콘
            Image(
                painter = painterResource(id = R.drawable.bluetooth),
                contentDescription = "BLE Chat",
                modifier = Modifier.size(32.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // 설명 텍스트
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "BLE 채팅",
                color = MaterialTheme.colors.onSurface,
                style = MaterialTheme.typography.subtitle1.copy(fontWeight = FontWeight.Bold),
                fontSize = 16.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "무선 네트워크 없이 BLE로 주변 기기와 대화",
                color = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium),
                style = MaterialTheme.typography.body2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// --- Previews ---

@Preview(showBackground = true, backgroundColor = 0xFF181818)
@Composable
fun ChatListScreenPreview() {
    LanternTheme {
        // Preview에서는 NavController를 직접 생성하거나 가짜(mock) 객체를 전달해야 할 수 있음
        // 여기서는 일단 기본 화면만 표시되도록 navController 없이 호출 (실제 앱에서는 NavHost가 관리)
        // ChatListScreen() // navController 없으면 오류 발생
        // 임시로 가짜 NavController 사용 (실제 동작 없음)
        val dummyNavController = NavController(LocalContext.current)
        ChatListScreen(navController = dummyNavController)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF181818)
@Composable
fun NearbySectionPreview() {
    LanternTheme {
        NearbySection(nearbyUsers = List(5) { NearbyUser(id = it + 1) })
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF181818)
@Composable
fun PublicChatListItemPreview() {
    LanternTheme {
        Surface(color = MaterialTheme.colors.background) {
             Column {
                // PublicChatListItem() // navController 없으면 오류 발생
                val dummyNavController = NavController(LocalContext.current)
                PublicChatListItem(navController = dummyNavController)
                Divider(color = MaterialTheme.colors.onSurface.copy(alpha = 0.12f), thickness = 1.dp, modifier = Modifier.padding(start = 76.dp, end = 16.dp))
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF181818)
@Composable
fun ChatListItemPreview_Unread() {
    LanternTheme {
        Surface(color = MaterialTheme.colors.background) {
            Column {
                val dummyNavController = NavController(LocalContext.current)
                ChatListItem(ChatItem(1, "내가진짜도경원", "와, 와이파이 없이 대화 신기하당 ㅎㅎ 와이파이 없이 대화 신기하당 ㅎㅎ", "11:20 am", true), navController = dummyNavController)
                Divider(color = MaterialTheme.colors.onSurface.copy(alpha = 0.12f), thickness = 1.dp, modifier = Modifier.padding(start = 76.dp, end = 16.dp))
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF181818)
@Composable
fun ChatListItemPreview_Read() {
    LanternTheme {
        Surface(color = MaterialTheme.colors.background) {
             Column {
                val dummyNavController = NavController(LocalContext.current)
                ChatListItem(ChatItem(2, "귀요미 이름이 엄청 길 경우엔 이렇게 보이게 됩니다", "난 귀요미", "10:20 am", false), navController = dummyNavController)
                Divider(color = MaterialTheme.colors.onSurface.copy(alpha = 0.12f), thickness = 1.dp, modifier = Modifier.padding(start = 76.dp, end = 16.dp))
            }
        }
    }
}
