package com.ssafy.lanterns.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import com.ssafy.lanterns.ui.theme.*
import com.ssafy.lanterns.ui.util.getProfileImageResId

/**
 * 앱 전체에서 사용될 수 있는 공통 검색 바
 */
@Composable
fun CommonSearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    placeholderText: String = "검색",
    modifier: Modifier = Modifier
) {
    androidx.compose.material3.TextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = {
            Text(
                text = placeholderText,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = "Search Icon",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(
                        imageVector = Icons.Default.Clear, 
                        contentDescription = "Clear",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp) 
            .height(56.dp), 
        shape = CircleShape, 
        colors = TextFieldDefaults.colors(
            focusedContainerColor = SurfaceLight, 
            unfocusedContainerColor = SurfaceLight,
            disabledContainerColor = SurfaceLight.copy(alpha = 0.5f),
            cursorColor = MaterialTheme.colorScheme.primary,
            focusedIndicatorColor = Color.Transparent, 
            unfocusedIndicatorColor = Color.Transparent, 
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            focusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
            focusedTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unfocusedTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        singleLine = true
    )
}

/**
 * 공통 프로필 아바타 컴포넌트
 * 
 * @param profileId 프로필 이미지 ID (getProfileImageResId 함수와 함께 사용)
 * @param name 프로필 이름 (선택 사항)
 * @param size 아바타 크기 (기본 48dp)
 * @param modifier 추가 모디파이어 
 * @param borderColor 경계선 색상 (hasBorder가 true일 때 사용)
 * @param borderGradientEnd 경계선 그라데이션 색상 (hasBorder가 true일 때 사용)
 * @param hasBorder 경계선 표시 여부
 * @param onClick 클릭 이벤트 처리 (선택 사항)
 */
@Composable
fun ProfileAvatar(
    profileId: Int,
    name: String? = "Profile Avatar",
    size: Dp = 48.dp,
    modifier: Modifier = Modifier,
    borderColor: Color = Color.White,  // 기본 테두리 색상을 흰색으로 변경
    borderGradientEnd: Color = Color.White,  // 그라데이션 끝 색상도 흰색으로 통일
    hasBorder: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val imageModifier = Modifier
        .size(size)
        .clip(CircleShape)
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    
    Box(
        modifier = modifier.then(
            if (hasBorder) {
                imageModifier
                    .background(
                        color = borderColor  // 단색 테두리 적용 (기본 흰색)
                    )
                    .padding(2.dp)  // 테두리 두께
                    .clip(CircleShape)  // 이미지를 원형으로 유지
                    .background(MaterialTheme.colorScheme.background)  // 내부 배경색
            } else {
                imageModifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.background)
            }
        )
    ) {
        Image(
            painter = painterResource(id = getProfileImageResId(profileId)),
            contentDescription = name ?: "Profile image for ID $profileId",
            modifier = Modifier.fillMaxSize().clip(CircleShape),  // 이미지도 원형으로 클립
            contentScale = ContentScale.Crop  // 이미지 비율 유지하며 꽉 채우기
        )
    }
}

@Preview(showBackground = true)
@Composable
fun CommonSearchBarPreview() {
    LanternsTheme {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.padding(16.dp)) {
            var text by remember { mutableStateOf("") }
            CommonSearchBar(value = text, onValueChange = { text = it })
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ProfileAvatarPreview() {
    LanternsTheme {
        Surface(color = MaterialTheme.colorScheme.background) { 
            Column(
                modifier = Modifier
                    .padding(16.dp)
            ) {
                Text("기본 프로필 아바타 (테두리 없음):", style = MaterialTheme.typography.labelMedium, color=MaterialTheme.colorScheme.onBackground)
                Spacer(modifier = Modifier.height(8.dp))
                ProfileAvatar(
                    profileId = 1,
                    name = "김싸피",
                    size = 60.dp
                )

                Spacer(modifier = Modifier.height(16.dp))
                Text("테두리 있는 프로필 아바타 (기본 그라데이션):", style = MaterialTheme.typography.labelMedium, color=MaterialTheme.colorScheme.onBackground)
                Spacer(modifier = Modifier.height(8.dp))
                ProfileAvatar(
                    profileId = 2,
                    name = "이랜턴",
                    size = 60.dp,
                    hasBorder = true
                    // modifier = Modifier.padding(top = 8.dp) // 이 패딩은 Box에 적용, Column 패딩으로 관리하는 것이 좋음
                )

                Spacer(modifier = Modifier.height(16.dp))
                Text("테두리 있는 프로필 아바타 (커스텀 테두리 색상):", style = MaterialTheme.typography.labelMedium, color=MaterialTheme.colorScheme.onBackground)
                Spacer(modifier = Modifier.height(8.dp))
                ProfileAvatar(
                    profileId = 3,
                    name = "박테마",
                    size = 60.dp,
                    borderColor = ConnectionNear, 
                    borderGradientEnd = LanternTeal, 
                    hasBorder = true
                    // modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
} 