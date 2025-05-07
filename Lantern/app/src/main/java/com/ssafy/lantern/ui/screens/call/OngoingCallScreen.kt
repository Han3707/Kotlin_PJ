package com.ssafy.lantern.ui.screens.call

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.ContentAlpha
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.lantern.R
import com.ssafy.lantern.ui.theme.LanternTheme
import com.ssafy.lantern.ui.util.getProfileImageResId
import kotlinx.coroutines.delay

// 통화 중 화면
@Composable
fun OngoingCallScreen(
    callerName: String,
    callerId: Int = 1,
    onEndCallClick: () -> Unit
) {
    var callDuration by remember { mutableStateOf(0) }
    var isSpeakerOn by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(false) }
    
    // 통화 시간 업데이트
    LaunchedEffect(Unit) {
        while(true) {
            delay(1000)
            callDuration++
        }
    }
    
    // 통화 시간 포맷팅
    val formattedDuration = remember(callDuration) {
        val minutes = callDuration / 60
        val seconds = callDuration % 60
        "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 상단 정보
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 32.dp)
            ) {
                Text(
                    text = callerName,
                    color = MaterialTheme.colors.onBackground,
                    style = MaterialTheme.typography.h5.copy(fontWeight = FontWeight.Bold)
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = formattedDuration,
                    color = MaterialTheme.colors.onBackground.copy(alpha = ContentAlpha.medium),
                    style = MaterialTheme.typography.body1
                )
            }
            
            // 중앙 프로필
            Image(
                painter = painterResource(id = getProfileImageResId(callerId)),
                contentDescription = "Caller Profile",
                modifier = Modifier
                    .size(200.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colors.surface)
            )
            
            // 하단 통화 컨트롤
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(bottom = 32.dp)
            ) {
                // 통화 기능 버튼들
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    val inactiveButtonColor = MaterialTheme.colors.onSurface.copy(alpha = 0.12f)
                    val activeButtonColor = MaterialTheme.colors.primary
                    val inactiveIconColor = MaterialTheme.colors.onSurface
                    val activeIconColor = MaterialTheme.colors.onPrimary

                    // 스피커 버튼
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSpeakerOn) activeButtonColor else inactiveButtonColor,
                                )
                                .clickable { 
                                    isSpeakerOn = !isSpeakerOn
                                    if (isSpeakerOn) {
                                        isMuted = false
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.VolumeUp,
                                contentDescription = "Speaker",
                                tint = if (isSpeakerOn) activeIconColor else inactiveIconColor,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = "스피커",
                            color = MaterialTheme.colors.onBackground,
                            style = MaterialTheme.typography.caption
                        )
                    }
                    
                    // 음소거 버튼
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isMuted) activeButtonColor else inactiveButtonColor,
                                )
                                .clickable { 
                                    isMuted = !isMuted
                                    if (isMuted) {
                                        isSpeakerOn = false
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.MicOff,
                                contentDescription = "Mute",
                                tint = if (isMuted) activeIconColor else inactiveIconColor,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = "음소거",
                            color = MaterialTheme.colors.onBackground,
                            style = MaterialTheme.typography.caption
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // 통화 종료 버튼
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colors.error)
                        .clickable(onClick = onEndCallClick),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.CallEnd,
                        contentDescription = "End Call",
                        tint = MaterialTheme.colors.onError,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun OngoingCallScreenPreview() {
    LanternTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colors.background
        ) {
            OngoingCallScreen(
                callerName = "도경원",
                callerId = 1,
                onEndCallClick = {}
            )
        }
    }
}