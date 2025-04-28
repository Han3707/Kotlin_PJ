package com.example.bletest.ui.view.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.bletest.data.model.MessageData
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 로그 표시 컴포넌트
 */
@Composable
fun LogView(
    logs: List<String>,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        items(logs) { log ->
            Text(
                text = log,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 2.dp)
            )
        }
    }
}

/**
 * 메시지 입력 컴포넌트
 */
@Composable
fun MessageInput(
    message: String,
    targetId: String,
    onMessageChange: (String) -> Unit,
    onTargetIdChange: (String) -> Unit,
    onSendClick: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "메시지 입력...",
    targetPlaceholder: String = "대상 ID"
) {
    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = targetId,
            onValueChange = onTargetIdChange,
            label = { Text(targetPlaceholder) },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 1
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = message,
                onValueChange = onMessageChange,
                label = { Text(placeholder) },
                modifier = Modifier.weight(1f)
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            IconButton(
                onClick = onSendClick,
                enabled = message.isNotBlank() && targetId.isNotBlank()
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "전송"
                )
            }
        }
    }
}

/**
 * 메시지 버블 컴포넌트
 */
@Composable
fun MessageBubble(
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
            shape = RoundedCornerShape(12.dp),
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

/**
 * 연결 상태 표시 컴포넌트
 */
@Composable
fun ConnectionStatusView(
    isConnected: Boolean,
    deviceName: String?,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isConnected) Color(0xFF4CAF50) else Color(0xFFE53935)
    val statusText = if (isConnected) "연결됨" else "연결 안됨"
    
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .background(backgroundColor, RoundedCornerShape(4.dp))
            .padding(8.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = null,
            tint = Color.White
        )
        
        Spacer(modifier = Modifier.width(8.dp))
        
        Column {
            Text(
                text = statusText,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            
            deviceName?.let {
                Text(
                    text = it,
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

/**
 * 로딩 표시 컴포넌트
 */
@Composable
fun LoadingView(
    message: String = "로딩 중...",
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
        }
    }
}

// 추후 구현할 공통 컴포넌트 함수가 위치할 파일입니다. 