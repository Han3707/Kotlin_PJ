package com.ssafy.lantern.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallMissed
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.Info
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.lantern.data.model.FriendCallItem
import com.ssafy.lantern.R
import com.ssafy.lantern.ui.components.ProfileAvatar
import com.ssafy.lantern.ui.util.getProfileImageResId

/**
 * 통화 기록 아이템 컴포넌트
 */
@Composable
fun FriendCallItem(friend: FriendCallItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ProfileAvatar(profileId = friend.id)
        
        Spacer(modifier = Modifier.width(12.dp))
        
        // Friend Info
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = friend.name,
                color = if (friend.callType == "부재중전화") MaterialTheme.colors.error else MaterialTheme.colors.onSurface,
                style = MaterialTheme.typography.subtitle1.copy(fontWeight = FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = when (friend.callType) {
                        "발신전화" -> Icons.Default.CallMade
                        "수신전화" -> Icons.Default.CallReceived
                        else -> Icons.Default.CallMissed
                    },
                    contentDescription = "Call Type",
                    tint = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium),
                    modifier = Modifier.size(16.dp)
                )
                
                Spacer(modifier = Modifier.width(4.dp))
                
                Text(
                    text = friend.callType,
                    color = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium),
                    style = MaterialTheme.typography.caption
                )
            }
        }
        
        Spacer(modifier = Modifier.width(8.dp))
        
        // Timestamp & Info
        Column(
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = friend.timestamp,
                color = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium),
                style = MaterialTheme.typography.caption
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // Info Button
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = "Info",
                tint = MaterialTheme.colors.primary,
                modifier = Modifier.size(20.dp)
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
    isEditing: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        // Label
        Text(
            text = label,
            color = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium),
            style = MaterialTheme.typography.caption
        )
        
        // Content Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            // 일반 모드 (편집 불가)
            Text(
                text = value,
                color = MaterialTheme.colors.onSurface,
                style = MaterialTheme.typography.body1,
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (isEditing) 0f else 1f)
            )
            
            // 편집 모드
            if (isEditing) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    textStyle = MaterialTheme.typography.body1.copy(
                        color = MaterialTheme.colors.onSurface
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        
        // Divider
        Divider(
            modifier = Modifier.padding(top = 4.dp),
            color = if (isEditing) MaterialTheme.colors.primary else MaterialTheme.colors.onSurface.copy(alpha = 0.12f),
            thickness = 1.dp
        )
    }
}