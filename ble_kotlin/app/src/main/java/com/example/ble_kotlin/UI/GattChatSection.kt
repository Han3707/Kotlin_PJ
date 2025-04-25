package com.example.ble_kotlin.UI

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ble_kotlin.BleManager.ConnectionState
import com.example.ble_kotlin.Utils.ChatMessage
import com.example.ble_kotlin.Utils.ScannedDevice
import com.example.ble_kotlin.ViewModel.BleUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * GATT 채팅 섹션 컴포저블
 *
 * @param uiState BLE UI 상태
 * @param messages 채팅 메시지 목록
 * @param onSendMessage 메시지 전송 콜백
 * @param onDisconnect 연결 해제 콜백
 */
@Composable
fun GattChatSection(
    uiState: BleUiState,
    messages: List<ChatMessage>,
    onSendMessage: (String) -> Unit,
    onDisconnect: () -> Unit
) {
    // 채팅이 가능한지 확인 (클라이언트 또는 서버 모드)
    val canChat = uiState.connectionState == ConnectionState.READY || 
                  (uiState.isServerRunning && uiState.connectedDevice != null)
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.BackgroundSecondary)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // 섹션 제목 및 연결 상태
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "GATT 채팅",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.TextPrimary
                )
                
                // 연결 상태 및 해제 버튼
                ConnectionStatusWithButton(
                    uiState = uiState,
                    onDisconnect = onDisconnect
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 메시지 목록
            ChatMessageList(
                messages = messages,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .height(240.dp)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 메시지 입력
            MessageInputField(
                onSendMessage = onSendMessage,
                enabled = canChat
            )
        }
    }
}

/**
 * 연결 상태 및 연결 해제 버튼 컴포저블
 *
 * @param uiState BLE UI 상태
 * @param onDisconnect 연결 해제 콜백
 */
@Composable
private fun ConnectionStatusWithButton(
    uiState: BleUiState,
    onDisconnect: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 연결 상태 텍스트
        val (statusText, statusColor) = when (uiState.connectionState) {
            ConnectionState.CONNECTED -> Pair("연결됨", AppColors.AmberStatus)
            ConnectionState.CONNECTING -> Pair("연결 중...", AppColors.AmberStatus)
            ConnectionState.READY -> Pair("준비됨", AppColors.GreenStatus)
            ConnectionState.ERROR -> Pair("오류", AppColors.RedStatus)
            else -> Pair("연결 안됨", AppColors.MidGray)
        }
        
        Text(
            text = statusText,
            color = statusColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
        
        Spacer(modifier = Modifier.width(8.dp))
        
        // 연결된 경우 연결 해제 버튼 표시
        if (uiState.connectionState == ConnectionState.READY || 
            uiState.connectionState == ConnectionState.CONNECTED) {
            Button(
                onClick = onDisconnect,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppColors.RedStatus,
                    contentColor = AppColors.White
                ),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.height(28.dp)
            ) {
                Text(
                    text = "연결 해제",
                    fontSize = 12.sp
                )
            }
        }
    }
}

/**
 * 채팅 메시지 목록 컴포저블
 *
 * @param messages 메시지 목록
 * @param modifier 수정자
 */
@Composable
private fun ChatMessageList(
    messages: List<ChatMessage>,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    
    // 새 메시지가 추가되면 스크롤 이동
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }
    
    Box(
        modifier = modifier
            .background(
                color = AppColors.White,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(8.dp)
    ) {
        if (messages.isEmpty()) {
            Text(
                text = "메시지가 없습니다",
                color = AppColors.TextSecondary,
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth()
            ) {
                items(messages) { message ->
                    ChatMessageItem(message)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

/**
 * 개별 채팅 메시지 컴포저블
 *
 * @param message 채팅 메시지
 */
@Composable
private fun ChatMessageItem(message: ChatMessage) {
    val isSelf = message.sender == "Me"
    val messageTimeFormatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    val timeString = messageTimeFormatter.format(Date(message.timestamp))
    
    // 나의 메시지는 오른쪽, 다른 사람의 메시지는 왼쪽 정렬
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isSelf) Alignment.End else Alignment.Start
    ) {
        // 발신자 정보
        Text(
            text = if (isSelf) "나" else message.sender,
            color = AppColors.TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
        
        // 메시지 내용
        Box(
            modifier = Modifier
                .padding(top = 2.dp)
                .background(
                    color = if (isSelf) AppColors.LanternAccent.copy(alpha = 0.2f) else AppColors.LightGray,
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text = message.message,
                color = AppColors.TextPrimary,
                fontSize = 14.sp
            )
        }
        
        // 타임스탬프
        Text(
            text = timeString,
            color = AppColors.TextSecondary,
            fontSize = 10.sp,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

/**
 * 메시지 입력 필드 컴포저블
 *
 * @param onSendMessage 메시지 전송 콜백
 * @param enabled 활성화 여부
 */
@Composable
private fun MessageInputField(
    onSendMessage: (String) -> Unit,
    enabled: Boolean
) {
    var message by remember { mutableStateOf("") }
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 메시지 입력 필드
        OutlinedTextField(
            value = message,
            onValueChange = { message = it },
            placeholder = { Text("메시지를 입력하세요") },
            enabled = enabled,
            modifier = Modifier.weight(1f),
            colors = TextFieldDefaults.colors(
                unfocusedContainerColor = AppColors.White,
                focusedContainerColor = AppColors.White,
                disabledContainerColor = AppColors.LightGray,
                unfocusedIndicatorColor = AppColors.MidGray,
                focusedIndicatorColor = AppColors.LanternAccent
            ),
            shape = RoundedCornerShape(8.dp),
            singleLine = true
        )
        
        Spacer(modifier = Modifier.width(8.dp))
        
        // 전송 버튼
        IconButton(
            onClick = {
                if (message.isNotBlank()) {
                    onSendMessage(message)
                    message = ""
                }
            },
            enabled = enabled && message.isNotBlank(),
            modifier = Modifier
                .background(
                    color = if (enabled && message.isNotBlank()) AppColors.LanternAccent else AppColors.MidGray,
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Send,
                contentDescription = "전송",
                tint = if (enabled && message.isNotBlank()) AppColors.TextPrimary else AppColors.White
            )
        }
    }
}

/**
 * 미리보기
 */
@Preview(showBackground = true)
@Composable
private fun GattChatSectionPreview() {
    val mockMessages = listOf(
        ChatMessage(
            sender = "11:22:33:44:55:66",
            message = "안녕하세요!",
            timestamp = System.currentTimeMillis() - 60000
        ),
        ChatMessage(
            sender = "Me",
            message = "반갑습니다! BLE 테스트 중입니다.",
            timestamp = System.currentTimeMillis() - 45000
        ),
        ChatMessage(
            sender = "11:22:33:44:55:66",
            message = "테스트가 잘 되네요.",
            timestamp = System.currentTimeMillis() - 30000
        )
    )
    
    val uiState = BleUiState(
        connectionState = ConnectionState.READY,
        connectedDevice = ScannedDevice(
            deviceName = "Test Device",
            deviceAddress = "11:22:33:44:55:66",
            rssi = -65,
            scanRecord = byteArrayOf()
        )
    )
    
    GattChatSection(
        uiState = uiState,
        messages = mockMessages,
        onSendMessage = {},
        onDisconnect = {}
    )
} 