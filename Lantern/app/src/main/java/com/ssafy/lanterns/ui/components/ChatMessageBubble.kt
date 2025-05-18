package com.ssafy.lanterns.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.ssafy.lanterns.ui.navigation.AppDestinations
import com.ssafy.lanterns.ui.theme.*
import com.ssafy.lanterns.ui.util.getProfileImageResId
import java.text.SimpleDateFormat
import java.util.*

/**
 * 채팅 메시지 버블 컴포넌트
 *
 * @param senderName 발신자 이름
 * @param text 메시지 내용
 * @param time 전송 시간 (밀리초 타임스탬프)
 * @param isMe 내가 보낸 메시지인지 여부
 * @param senderProfileId 발신자 프로필 ID (색상 지정용)
 * @param navController 선택사항: 프로필 클릭 시 프로필 화면으로 이동하기 위한 네비게이션 컨트롤러
 * @param distance 선택사항: 거리 정보 (미터 단위)
 * @param chatBubbleColor 채팅 버블의 배경색
 * @param textColor 메시지 텍스트의 색상
 * @param metaTextColor 메타 텍스트(발신자 이름, 전송 시간)의 색상
 * @param avatarBorderColor 프로필 아바타의 테두리 색상
 */
@Composable
fun ChatMessageBubble(
    senderName: String,
    text: String,
    time: Long,
    isMe: Boolean = false,
    senderProfileId: Int? = null,
    navController: NavController? = null,
    distance: Float = 50f,
    chatBubbleColor: Color = if (isMe) 
                               MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
                               .compositeOver(MaterialTheme.colorScheme.primaryContainer)
                            else 
                               MaterialTheme.colorScheme.surface
                               .copy(alpha = 0.9f),
    textColor: Color = if (isMe) 
                         MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f)
                      else 
                         MaterialTheme.colorScheme.onSurface,
    metaTextColor: Color = if (isMe)
                             MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                           else
                             MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
    avatarBorderColor: Color = if (isMe)
                                 MaterialTheme.colorScheme.secondary
                               else
                                 MaterialTheme.colorScheme.primary
) {
    val bubbleShape = RoundedCornerShape(
        topStart = 16.dp,
        topEnd = 16.dp,
        bottomStart = if (isMe) 16.dp else 4.dp,
        bottomEnd = if (isMe) 4.dp else 16.dp
    )
    
    // 타임스탬프 포맷팅
    val formattedTime = formatTime(time)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 8.dp),
        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!isMe) {
            // 프로필 아바타를 직접 구현하여 흰색 테두리 적용
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        color = Color.White, // 흰색 테두리
                        shape = CircleShape
                    )
                    .padding(2.dp) // 테두리 두께
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.background)
                    .clickable(
                        enabled = navController != null && senderProfileId != null,
                        onClick = if (navController != null && senderProfileId != null) {
                            {
                                val route = AppDestinations.PROFILE_ROUTE
                                    .replace("{userId}", senderProfileId.toString())
                                    .replace("{name}", senderName)
                                    .replace("{distance}", "${distance.toInt()}m")
                                navController.navigate(route)
                            }
                        } else ({})
                    )
            ) {
                Image(
                    painter = painterResource(id = getProfileImageResId(senderProfileId ?: 1)),
                    contentDescription = "프로필 이미지: $senderName",
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(
            horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
        ) {
            if (!isMe) {
                Text(
                    text = senderName,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), 
                    color = metaTextColor, 
                    modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                )
            }
            
            androidx.compose.material3.Surface(
                shape = bubbleShape,
                color = chatBubbleColor,
                shadowElevation = 4.dp,
                tonalElevation = 2.dp,
                border = if (!isMe) androidx.compose.foundation.BorderStroke(
                    width = 0.5.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                ) else null,
                modifier = Modifier
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = text,
                        color = textColor,
                        style = MaterialTheme.typography.bodyLarge 
                    )
                    
                    Spacer(modifier = Modifier.height(2.dp))
                    
                    Text(
                        text = formattedTime,
                        style = MaterialTheme.typography.labelSmall, 
                        color = metaTextColor,
                        modifier = Modifier.align(if (isMe) Alignment.Start else Alignment.End)
                    )
                }
            }
        }
        
        if (isMe) {
            Spacer(modifier = Modifier.width(8.dp))
            // 프로필 아바타를 직접 구현하여 흰색 테두리 적용
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(
                        color = Color.White, // 흰색 테두리
                        shape = CircleShape
                    )
                    .padding(2.dp) // 테두리 두께
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.background)
                    .clickable(
                        enabled = navController != null && senderProfileId != null,
                        onClick = if (navController != null && senderProfileId != null) {
                            {
                                // 내 프로필 화면으로
                                // val route = ...
                                // navController.navigate(route)
                            }
                        } else ({})
                    )
            ) {
                Image(
                    painter = painterResource(id = getProfileImageResId(senderProfileId ?: 0)),
                    contentDescription = "내 프로필 이미지",
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            }
        }
    }
}

/**
 * 시간 포맷팅 함수
 */
private fun formatTime(timestamp: Long): String {
    val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    return dateFormat.format(Date(timestamp))
}

/**
 * 사용자 프로필 아바타 컴포넌트 - 채팅에서 사용하는 버전
 * 
 * @deprecated 기존 코드와의 호환성을 위해 유지. ProfileAvatar를 대신 사용하세요.
 */
@Composable
@Deprecated("ProfileAvatar를 사용하세요", ReplaceWith("ProfileAvatar(profileId, size, modifier, connectionColor, true, onClick)"))
fun ChatProfileAvatar(
    profileId: Int,
    connectionColor: Color = ConnectionNear,
    size: Dp = 40.dp,
    onClick: (() -> Unit)? = null
) {
    ProfileAvatar(
        profileId = profileId,
        name = "User $profileId", // ProfileAvatar에 name이 필요하므로 임시 이름 추가
        size = size,
        borderColor = connectionColor,
        hasBorder = true,
        onClick = onClick
    )
}

@Preview(showBackground = true)
@Composable
fun ChatMessageBubblePreview() {
    LanternsTheme { 
        Column(Modifier.background(MaterialTheme.colorScheme.background).padding(8.dp)) {
            val dummyNavController = rememberNavController()
            val currentTime = System.currentTimeMillis()

            ChatMessageBubble(
                senderName = "나",
                text = "안녕하세요. 이것은 내가 보낸 메시지입니다. 테마 기본 색상이 적용됩니다.",
                time = currentTime,
                isMe = true,
                senderProfileId = 0,
                navController = dummyNavController
            )

            Spacer(modifier = Modifier.height(8.dp))

            ChatMessageBubble(
                senderName = "상대방",
                text = "반갑습니다! 이것은 다른 사람이 보낸 메시지입니다. 테마 기본 색상이 적용됩니다.",
                time = currentTime - 60000, // 1분 전
                isMe = false,
                senderProfileId = 2,
                navController = dummyNavController
            )
            Spacer(modifier = Modifier.height(8.dp))

            ChatMessageBubble(
                senderName = "나 (커스텀 색상)",
                text = "이 메시지는 외부에서 지정된 색상(ChatBubbleMine, TextPrimary)을 사용합니다.",
                time = currentTime - 120000, // 2분 전
                isMe = true,
                senderProfileId = 0,
                navController = dummyNavController,
                chatBubbleColor = ChatBubbleMine, 
                textColor = TextPrimary,
                metaTextColor = TextPrimary.copy(alpha = 0.7f)
            )
        }
    }
} 