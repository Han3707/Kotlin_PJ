package com.ssafy.lanterns.ui.screens.main.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ssafy.lanterns.utils.getConnectionColorByDistance

// 이징 애니메이션 커브 (모달 애니메이션용)
private val EaseOutQuart = CubicBezierEasing(0.25f, 1f, 0.5f, 1f)
private val EaseInQuad = CubicBezierEasing(0.55f, 0.085f, 0.68f, 0.53f)

/**
 * 주변 사람 목록 모달
 */
@Composable
fun NearbyPersonListModal(
    people: List<NearbyPerson>,
    onDismiss: () -> Unit,
    onPersonClick: (userId: String) -> Unit,
    onCallClick: (userId: String) -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            // 배경 어둡게 처리 (탭해서 닫기 가능)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0.5f)
                    .background(MaterialTheme.colorScheme.scrim)
                    .clickable(onClick = onDismiss)
            )
            
            // 모달 콘텐츠 - 아래에서 위로 슬라이드 애니메이션
            AnimatedVisibility(
                visible = true,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(300, easing = EaseOutQuart)
                ),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(200, easing = EaseInQuad)
                )
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 500.dp)
                        .clickable(enabled = false) {},
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        // 헤더 영역
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "주변에 탐지된 사람 (${people.size})",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            
                            IconButton(onClick = onDismiss) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "닫기",
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                            }
                        }
                        
                        Divider(color = MaterialTheme.colorScheme.outlineVariant)
                        
                        // 사람 목록
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            items(people.sortedByDescending { it.signalLevel }) { person ->
                                PersonListItemWithButtons(
                                    person = person,
                                    onChatClick = { onPersonClick(person.bleId) },
                                    onCallClick = { onCallClick(person.bleId) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 주변 사람 목록 아이템 (채팅 및 통화 버튼 포함)
 * - 간소화된 UI: 프로필 이미지, 닉네임, 신호 강도, 채팅/통화 버튼 표시
 */
@Composable
fun PersonListItemWithButtons(
    person: NearbyPerson,
    onChatClick: () -> Unit,
    onCallClick: () -> Unit
) {
    // 신호 강도 레벨에 따른 색상 및 활성화 상태 결정
    val signalColor = when (person.signalLevel) {
        3 -> Color.Green     // 강한 신호
        2 -> Color.Yellow    // 중간 신호
        else -> Color.Red    // 약한 신호
    }
    
    // 통화 버튼은 신호 강도 2 이상인 사용자에게만 활성화
    val isCallEnabled = person.signalLevel >= 2
    
    // 신호 강도 텍스트
    val signalText = when (person.signalLevel) {
        3 -> "강한 신호"
        2 -> "중간 신호"
        else -> "약한 신호"
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 프로필 이미지
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "프로필 이미지",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            // 사용자 정보 영역
            Column(
                modifier = Modifier.weight(1f)
            ) {
                // 이름
                Text(
                    text = person.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // 신호 강도 정보 (RSSI 기반)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 신호 강도 표시기
                    SignalStrengthIndicator(
                        level = person.signalLevel
                    )
                    
                    // 신호 강도 텍스트
                    Text(
                        text = signalText,
                        style = MaterialTheme.typography.bodySmall,
                        color = signalColor
                    )
                }
            }
            
            // 버튼 영역
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 채팅 버튼
                IconButton(
                    onClick = onChatClick,
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            shape = CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Filled.Chat,
                        contentDescription = "채팅하기",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                
                // 통화 버튼 (신호 강도에 따라 활성화)
                IconButton(
                    onClick = onCallClick,
                    enabled = isCallEnabled,
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            color = if (isCallEnabled) {
                                Color(0xFF21AA73).copy(alpha = 0.1f)
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                            },
                            shape = CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Filled.Call,
                        contentDescription = "전화하기",
                        tint = if (isCallEnabled) {
                            Color(0xFF21AA73)
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        },
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
} 