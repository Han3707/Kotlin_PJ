package com.ssafy.lantern.ui.screens.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavController
import com.ssafy.lantern.R
import com.ssafy.lantern.ui.components.ChatMessageBubble
import com.ssafy.lantern.ui.theme.LanternTheme

// 더미 메시지 데이터 모델
data class Message(
    val id: Int,
    val sender: String,
    val text: String,
    val time: String,
    val isMe: Boolean = false,
    val senderProfileId: Int? = null
)

@Composable
fun PublicChatScreen(
    navController: NavController
) {
    val messages = remember {
        listOf(
            Message(1, "도경원", "안녕. 나는 도경원이야.", "10:21 PM", true, senderProfileId = 1),
            Message(2, "도경원2", "엥. 나도 도경원인데? 너 누구야?? ㅡㅡ", "10:21 PM", false, senderProfileId = 2),
            Message(3, "도경원", "내가 진짜 도경원이어야", "10:21 PM", true, senderProfileId = 1),
            Message(4, "여자친구", "너희 둘 도대체 뭐하는 거야?", "10:21 PM", false, senderProfileId = 3)
        )
    }
    val (input, setInput) = remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background)
    ) {
        TopAppBar(
            title = { Text("공용 채팅") },
            navigationIcon = {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                IconButton(onClick = { /* 검색 */ }) {
                    Icon(Icons.Filled.Search, contentDescription = "Search")
                }
                IconButton(onClick = { /* 옵션 */ }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Options")
                }
            },
            backgroundColor = MaterialTheme.colors.surface,
            contentColor = MaterialTheme.colors.onSurface
        )
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
        ) {
            items(messages) { msg ->
                ChatMessageBubble(
                    senderName = msg.sender,
                    text = msg.text,
                    time = msg.time,
                    isMe = msg.isMe,
                    senderProfileId = msg.senderProfileId
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF232323))
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = input,
                onValueChange = setInput,
                textStyle = MaterialTheme.typography.body1.copy(color = MaterialTheme.colors.onSurface),
                modifier = Modifier
                    .weight(1f)
                    .background(Color.Transparent)
                    .padding(8.dp),
                decorationBox = { innerTextField ->
                    if (input.isEmpty()) {
                        Text("메시지 입력", color = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium), style = MaterialTheme.typography.body1)
                    }
                    innerTextField()
                }
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = { /* 메시지 전송 */ },
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colors.primary)
            ) {
                Icon(
                    imageVector = Icons.Filled.Send,
                    contentDescription = "Send",
                    tint = MaterialTheme.colors.onPrimary
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PublicChatScreenPreview() {
    LanternTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colors.background
        ) {
            val dummyNavController = NavController(LocalContext.current)
            PublicChatScreen(navController = dummyNavController)
        }
    }
}
