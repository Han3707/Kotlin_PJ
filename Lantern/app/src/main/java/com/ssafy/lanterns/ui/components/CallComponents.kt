package com.ssafy.lanterns.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallMissed
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ssafy.lanterns.R
import com.ssafy.lanterns.data.model.FriendCallItem
import com.ssafy.lanterns.ui.theme.LanternsTheme
import com.ssafy.lanterns.ui.theme.CallHistoryMissingCall
import com.ssafy.lanterns.ui.theme.LanternYellow
import androidx.compose.foundation.background

/**
 * 통화 기록 아이템 컴포넌트
 */
@Composable
fun FriendCallItemComponent(
    friend: FriendCallItem,
    onItemClick: (Int) -> Unit = {} 
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onItemClick(friend.id) } 
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ProfileAvatar(
            profileId = friend.id,
            name = friend.name, 
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = friend.name,
                color = if (friend.callType == "부재중전화") CallHistoryMissingCall 
                        else MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium, 
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                val callTypeIcon = when (friend.callType) {
                    "발신전화" -> Icons.Default.CallMade
                    "수신전화" -> Icons.Default.CallReceived
                    else -> Icons.Default.CallMissed
                }
                val callTypeColor = when (friend.callType) {
                    "부재중전화" -> CallHistoryMissingCall
                    "발신전화" -> LanternYellow 
                    else -> MaterialTheme.colorScheme.primary 
                }
                Icon(
                    imageVector = callTypeIcon,
                    contentDescription = "Call Type",
                    tint = callTypeColor.copy(alpha = 0.8f), 
                    modifier = Modifier.size(16.dp)
                )
                
                Spacer(modifier = Modifier.width(4.dp))
                
                Text(
                    text = friend.callType,
                    color = callTypeColor.copy(alpha = 0.8f), 
                    style = MaterialTheme.typography.bodySmall 
                )
            }
        }
        
        Spacer(modifier = Modifier.width(8.dp))
        
        Column(
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = friend.timestamp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall 
            )
        }
    }
}

/**
 * 프로필 필드 컴포넌트
 */
@Composable
fun SimpleProfileField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    isEditing: Boolean,
    modifier: Modifier = Modifier 
) {
    Column(
        modifier = modifier 
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium 
        )
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            if (!isEditing) {
                Text(
                    text = value,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyLarge, 
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    textStyle = MaterialTheme.typography.bodyLarge.copy( 
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary) 
                )
            }
        }
        
        HorizontalDivider(
            modifier = Modifier.padding(top = 4.dp),
            color = if (isEditing) MaterialTheme.colorScheme.primary 
                    else MaterialTheme.colorScheme.outlineVariant, 
            thickness = 1.dp
        )
    }
}

@Preview(showBackground = true)
@Composable
fun FriendCallItemComponentPreview() {
    val call1 = FriendCallItem(1, "김철수", R.drawable.lantern_image, "발신전화", "10:25 am", true)
    val call2 = FriendCallItem(2, "이영희", R.drawable.lantern_image, "수신전화", "어제", false)
    val call3 = FriendCallItem(3, "박지민", R.drawable.lantern_image, "부재중전화", "2일 전", true)
    LanternsTheme {
        Column(Modifier.background(MaterialTheme.colorScheme.surface)) {
            FriendCallItemComponent(friend = call1)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            FriendCallItemComponent(friend = call2)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            FriendCallItemComponent(friend = call3)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SimpleProfileFieldPreview() {
    LanternsTheme {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.padding(16.dp)) {
            Column {
                var textValue by remember { mutableStateOf("기본값") }
                SimpleProfileField(
                    label = "이름 (편집 불가)",
                    value = "김랜턴",
                    onValueChange = {},
                    isEditing = false
                )
                Spacer(modifier = Modifier.height(16.dp))
                SimpleProfileField(
                    label = "상태 메시지 (편집 중)",
                    value = textValue,
                    onValueChange = { textValue = it },
                    isEditing = true
                )
            }
        }
    }
}