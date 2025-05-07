package com.ssafy.lantern.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.lantern.R // 임시 R 임포트, ProfileAvatar에서 필요할 수 있음

/**
 * 채팅 메시지 말풍선 컴포넌트
 *
 * @param senderName 보낸 사람 이름 (null이 아니면 표시됨, 주로 공용 채팅에서 사용)
 * @param text 메시지 내용
 * @param time 메시지 시간
 * @param isMe 내가 보낸 메시지인지 여부
 * @param senderProfileId 보낸 사람 프로필 ID (null이면 프로필 이미지 표시 안 함)
 */
@Composable
fun ChatMessageBubble(
    senderName: String? = null,
    text: String,
    time: String,
    isMe: Boolean,
    senderProfileId: Int? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
    ) {
        // 상대방 프로필 이미지 (내가 보낸 메시지가 아니고, 프로필 ID가 있을 때)
        if (!isMe && senderProfileId != null) {
            ProfileAvatar(profileId = senderProfileId, size = 36.dp)
            Spacer(modifier = Modifier.width(8.dp))
        }

        // 말풍선 및 시간
        Column(
            horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
        ) {
            // 보낸 사람 이름 (공용 채팅 등에서 필요 시 표시)
            if (senderName != null && !isMe) {
                Text(
                    text = senderName,
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                )
            }

            // 말풍선
            Box(
                modifier = Modifier
                    .background(
                        color = if (isMe) MaterialTheme.colors.primary else MaterialTheme.colors.onSurface,
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(
                    text = text,
                    // 내가 보낸 메시지는 onPrimary, 상대방은 onSurface
                    color = MaterialTheme.colors.onPrimary,
                    style = MaterialTheme.typography.body1
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            // 시간
            Text(
                text = time,
                style = MaterialTheme.typography.overline,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
            )
        }

        // 내 프로필 이미지 (내가 보낸 메시지이고, 프로필 ID가 있을 때 - 선택 사항)
        // 일반적으로 1:1 채팅에서는 내 프로필을 표시하지 않음
        /*
        if (isMe && senderProfileId != null) {
            Spacer(modifier = Modifier.width(8.dp))
            ProfileAvatar(profileId = senderProfileId, size = 36.dp)
        }
         */
    }
} 