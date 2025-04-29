package com.example.bletest.ui.view.chat

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bletest.data.model.MessageData
import com.example.bletest.ui.view.components.MessageBubble
import com.example.bletest.ui.viewmodel.BleViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 공통 채팅방 화면
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PublicChatScreen(
    onNavigateBack: () -> Unit,
    viewModel: BleViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val isRunning by viewModel.meshRunningState.collectAsState()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val myDeviceId by viewModel.deviceId.collectAsStateWithLifecycle("")
    val knownDevices by viewModel.knownDevices.collectAsState()
    
    // 권한 및 서비스 상태 확인
    val permissionHelper = remember { com.example.bletest.utils.PermissionHelper }
    val hasRequiredPermissions = permissionHelper.hasRequiredPermissions(context)
    val isServiceConnected by viewModel.isServiceConnected.collectAsState()
    
    // 메시지 입력 상태
    var messageText by remember { mutableStateOf("") }
    
    // 스크롤 상태 - 자동 스크롤을 위해 사용
    val listState = rememberLazyListState()
    
    // 필요한 권한이 없거나 서비스가 연결되지 않은 경우 알림
    LaunchedEffect(key1 = hasRequiredPermissions, key2 = isServiceConnected) {
        if (!hasRequiredPermissions) {
            Toast.makeText(
                context,
                "BLE 통신을 위해 필요한 권한이 없습니다. 설정에서 권한을 허용해주세요.",
                Toast.LENGTH_LONG
            ).show()
            onNavigateBack()
            return@LaunchedEffect
        }
        
        if (!isServiceConnected) {
            Toast.makeText(
                context,
                "BLE 서비스에 연결할 수 없습니다. 잠시 후 다시 시도해주세요.",
                Toast.LENGTH_LONG
            ).show()
        }
    }
    
    // 화면 진입 시 자동으로 공통 채팅방 시작 (서비스가 연결된 경우에만)
    LaunchedEffect(key1 = isServiceConnected) {
        if (isServiceConnected && !isRunning) {
            // 랜덤 디바이스 ID 생성 (설정되지 않은 경우)
            if (myDeviceId.isEmpty()) {
                val randomId = java.util.UUID.randomUUID().toString().take(8)
                viewModel.setDeviceId(randomId)
            }
            
            Log.d("PublicChatScreen", "LaunchedEffect: startPublicChatRoom 호출 시도")
            // 공통 채팅방 시작
            val result = viewModel.startPublicChatRoom()
            if (result) {
                Toast.makeText(context, "공통 채팅방이 시작되었습니다", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "공통 채팅방 시작에 실패했습니다", Toast.LENGTH_SHORT).show()
                // 시작 실패 시 재시도 (권한이나 블루투스 상태 등의 문제일 수 있음)
                kotlinx.coroutines.delay(1000)
                Log.d("PublicChatScreen", "LaunchedEffect: startPublicChatRoom 재시도")
                val retryResult = viewModel.startPublicChatRoom()
                if (retryResult) {
                    Toast.makeText(context, "공통 채팅방이 시작되었습니다", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "채팅방을 시작할 수 없습니다. 블루투스 설정을 확인하세요.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    // 자동 스크롤 - 새 메시지가 추가될 때
    LaunchedEffect(messages) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }
    
    // 화면이 종료될 때 채팅방 종료 (DisposableEffect를 사용하여 정리)
    DisposableEffect(key1 = Unit) {
        onDispose {
            // 이미 실행 중이라면 종료
            if (isRunning) {
                viewModel.stopPublicChatRoom()
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
                title = { Text("공통 채팅방") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "뒤로 가기",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                },
                actions = {
                    Button(
                        onClick = {
                            Log.d("PublicChatScreen", "공용 채팅방 시작 버튼 클릭됨")
                            if (isRunning) {
                                viewModel.stopPublicChatRoom()
                                Toast.makeText(context, "공통 채팅방이 종료되었습니다", Toast.LENGTH_SHORT).show()
                            } else {
                                // 공통 채팅방 시작 로직 호출
                                val result = viewModel.startPublicChatRoom()
                                if (result) {
                                    Toast.makeText(context, "공통 채팅방이 시작되었습니다", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "공통 채팅방 시작에 실패했습니다", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(if (isRunning) "채팅방 종료" else "채팅방 시작")
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
            // 현재 접속자 정보
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(8.dp)
                ) {
                    Text(
                        text = "내 ID: $myDeviceId",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Text(
                        text = "접속 중인 사용자: ${knownDevices.size}명",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            
            // 메시지 목록
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (!isRunning) {
                    // 채팅방이 활성화되지 않은 경우
                    Text(
                        text = "채팅방이 시작되지 않았습니다.\n상단의 '채팅방 시작' 버튼을 눌러 시작하세요.",
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp),
                        textAlign = TextAlign.Center
                    )
                } else if (messages.isEmpty()) {
                    // 메시지가 없는 경우
                    Text(
                        text = "메시지가 없습니다. 대화를 시작해보세요!",
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
                            PublicChatMessageBubble(
                                message = message,
                                isOwnMessage = message.sourceId == myDeviceId
                            )
                            
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                        
                        // 추가 스크롤 공간
                        item { Spacer(modifier = Modifier.height(70.dp)) }
                    }
                }
            }
            
            // 구분선
            HorizontalDivider()
            
            // 메시지 입력 영역
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                OutlinedTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    label = { Text("메시지 입력...") },
                    modifier = Modifier.weight(1f)
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                IconButton(
                    onClick = {
                        if (messageText.isNotBlank() && isRunning) {
                            viewModel.sendBroadcastMessage(messageText)
                            messageText = ""
                        } else if (!isRunning) {
                            Toast.makeText(context, "채팅방이 활성화되지 않았습니다.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = messageText.isNotBlank() && isRunning
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "전송"
                    )
                }
            }
        }
    }
}

/**
 * 공통 채팅방용 메시지 버블 컴포넌트
 */
@Composable
fun PublicChatMessageBubble(
    message: MessageData,
    isOwnMessage: Boolean,
    modifier: Modifier = Modifier
) {
    val alignment = if (isOwnMessage) Alignment.End else Alignment.Start
    val backgroundColor = if (isOwnMessage) 
        MaterialTheme.colorScheme.primary 
    else 
        MaterialTheme.colorScheme.secondary
    val textColor = if (isOwnMessage) 
        MaterialTheme.colorScheme.onPrimary 
    else 
        MaterialTheme.colorScheme.onSecondary
    
    val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())
    val time = timeFormatter.format(Date(message.timestamp))
    
    Column(
        horizontalAlignment = alignment,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        // 발신자 ID (내 메시지가 아닌 경우만 표시)
        if (!isOwnMessage) {
            Text(
                text = message.sourceId,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(start = 8.dp, bottom = 2.dp)
            )
        }
        
        // 메시지 버블
        Card(
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = backgroundColor),
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            message.content?.let {
                Text(
                    text = it,
                    color = textColor,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
        
        // 시간
        Text(
            text = time,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
} 