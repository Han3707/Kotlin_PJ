package com.example.bletest.ui.view.chat

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bletest.ui.view.components.MessageBubble
import com.example.bletest.ui.view.components.MessageInput
import com.example.bletest.ui.viewmodel.ChatUiEvent
import com.example.bletest.ui.viewmodel.ChatViewModel

/**
 * 채팅 화면
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onNavigateBack: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val messageText by viewModel.messageText.collectAsStateWithLifecycle()
    val targetId by viewModel.targetId.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    
    // 스크롤 상태 - 자동 스크롤을 위해 사용
    val listState = rememberLazyListState()
    
    // 자동 스크롤 - 새 메시지가 추가될 때
    LaunchedEffect(messages) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }
    
    // UI 이벤트 수집
    LaunchedEffect(key1 = true) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                is ChatUiEvent.ShowToast -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                title = { Text("BLE 메시 채팅") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "뒤로 가기",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 메시지 목록
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (messages.isEmpty()) {
                    // 메시지가 없는 경우
                    Text(
                        text = "메시지가 없습니다. 채팅을 시작해보세요!",
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp)
                    )
                } else {
                    // 메시지 목록 표시
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp)
                    ) {
                        items(messages) { message ->
                            MessageBubble(
                                message = message,
                                isOwnMessage = viewModel.isOwnMessage(message)
                            )
                        }
                        
                        // 추가 스크롤 공간
                        item { Spacer(modifier = Modifier.height(70.dp)) }
                    }
                }
            }
            
            // 구분선
            Divider()
            
            // 메시지 입력 영역
            MessageInput(
                message = messageText,
                targetId = targetId,
                onMessageChange = viewModel::updateMessageText,
                onTargetIdChange = viewModel::updateTargetId,
                onSendClick = viewModel::sendMessage,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            )
        }
    }
}

// 추후 구현할 ChatScreen Composable 함수가 위치할 파일입니다. 