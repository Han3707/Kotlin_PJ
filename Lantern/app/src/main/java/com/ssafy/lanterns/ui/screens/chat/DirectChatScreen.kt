package com.ssafy.lanterns.ui.screens.chat

import android.util.Log
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import androidx.hilt.navigation.compose.hiltViewModel
import com.ssafy.lanterns.ui.components.ChatMessageBubble
import com.ssafy.lanterns.ui.theme.*
import android.widget.Toast
import androidx.compose.ui.draw.shadow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.runtime.DisposableEffect
import com.ssafy.lanterns.data.model.Message
import com.ssafy.lanterns.data.model.User
import com.ssafy.lanterns.ui.screens.main.MainViewModel
import com.ssafy.lanterns.service.ble.GlobalBleManager
import android.app.Activity
import com.ssafy.lanterns.data.model.MessageStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * BLE를 이용한 1대1 채팅 구현 주석
 * 
 * 1대1 채팅에서는 다음과 같은 BLE 기능을 활용할 수 있습니다:
 * 
 * 1. 타겟 기기 선택:
 *    - 사용자 목록에서 선택하거나 QR 코드를 통해 직접 연결
 *    - 블루투스 주소나 고유 식별자를 사용하여 특정 기기와 통신
 * 
 * 2. 연결 관리:
 *    - 지정된 기기와의 GATT 연결 유지
 *    - 연결 상태 모니터링 및 자동 재연결 메커니즘
 * 
 * 3. 데이터 보안:
 *    - 1대1 통신에 적합한 보안 메커니즘 구현
 *    - 페어링 또는 암호화 키 교환 가능
 * 
 * 4. 메시지 동기화:
 *    - 오프라인 메시지 저장 및 연결 시 동기화
 *    - 메시지 수신 확인 메커니즘
 * 
 * 5. 연결 최적화:
 *    - 배터리 소모 최소화를 위한 연결 파라미터 조정
 *    - 양방향 통신에 최적화된 BLE 설정
 */

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun DirectChatScreen(
    userId: String,
    navController: NavController,
    viewModel: DirectChatViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lazyListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // 에러 메시지 표시
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            // ViewModel에서 에러 메시지를 null로 초기화하는 로직이 이미 있음
        }
    }

    // 새 메시지 수신 시 자동 스크롤
    LaunchedEffect(uiState.messages) {
        if (uiState.messages.isNotEmpty()) {
            coroutineScope.launch {
                lazyListState.animateScrollToItem(uiState.messages.lastIndex)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.participantUser?.nickname ?: "채팅",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로 가기")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        bottomBar = {
            ChatInputBar(viewModel = viewModel, uiState = uiState)
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (uiState.isLoading && uiState.messages.isEmpty()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
                    reverseLayout = false, // 일반적으로 채팅은 아래부터 쌓이지만, 새 메시지가 아래에 추가되도록 함
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {                    
                    items(uiState.messages, key = { message -> message.messageId }) { message ->
                         MessageBubble(message = message, currentUserId = uiState.currentUser?.userId ?: -1L)
                    }
                }
            }
        }
    }
}

@Composable
fun ChatInputBar(viewModel: DirectChatViewModel, uiState: DirectChatUiState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .navigationBarsPadding(), // IME 조절을 위해
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = uiState.inputText,
            onValueChange = { viewModel.onInputTextChanged(it) },
            modifier = Modifier.weight(1f),
            placeholder = { Text("메시지 입력...") },
            shape = RoundedCornerShape(24.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                cursorColor = MaterialTheme.colorScheme.primary
            ),
            maxLines = 3
        )
        Spacer(modifier = Modifier.width(8.dp))
        IconButton(
            onClick = { viewModel.sendMessage() },
            enabled = uiState.inputText.isNotBlank(),
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(if (uiState.inputText.isNotBlank()) LanternOrange else Color.Gray)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Send,
                contentDescription = "메시지 전송",
                tint = Color.White
            )
        }
    }
}

@Composable
fun MessageBubble(
    message: Message,
    currentUserId: Long
) {
    val isMyMessage = message.senderId == currentUserId
    val backgroundColor = if (isMyMessage) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
    val textColor = if (isMyMessage) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
    val horizontalAlignment = if (isMyMessage) Alignment.End else Alignment.Start

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = horizontalAlignment
    ) {
        // 메시지 작성자 이름 (나의 메시지가 아닐 경우에만 표시)
        if (!isMyMessage) {
            Text(
                text = "상대방", // 실제 구현에서는 메시지에서 이름을 가져와야 함
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(start = 12.dp, bottom = 2.dp)
            )
        }

        // 메시지 내용과 시간
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = if (isMyMessage) Arrangement.End else Arrangement.Start,
            modifier = Modifier.fillMaxWidth()
        ) {
            // 상대방 메시지인 경우 시간을 왼쪽에 표시
            if (!isMyMessage) {
                Text(
                    text = formatMessageTime(message.timestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 4.dp, bottom = 2.dp)
                )
            }

            // 메시지 버블
            Card(
                colors = CardDefaults.cardColors(containerColor = backgroundColor),
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (isMyMessage) 16.dp else 0.dp,
                    bottomEnd = if (isMyMessage) 0.dp else 16.dp
                ),
                modifier = Modifier.widthIn(max = 280.dp)
            ) {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor,
                    modifier = Modifier.padding(all = 12.dp)
                )
            }

            // 내 메시지인 경우 시간을 오른쪽에 표시
            if (isMyMessage) {
                Text(
                    text = formatMessageTime(message.timestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                )
            }
        }
    }
}

// 메시지 시간을 포맷팅하는 함수
private fun formatMessageTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
