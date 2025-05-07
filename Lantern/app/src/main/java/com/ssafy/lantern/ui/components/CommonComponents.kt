package com.ssafy.lantern.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ssafy.lantern.R
import com.ssafy.lantern.ui.util.getProfileImageResId

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
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholderText, color = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium)) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search Icon", tint = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium)) },
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp) // 패딩 조정 (기존 FriendListScreen 기준)
            .height(48.dp),
        shape = RoundedCornerShape(50), // 둥근 모서리
        colors = TextFieldDefaults.outlinedTextFieldColors(
            backgroundColor = Color(0xFF232323), // FriendListScreen과 통일된 배경색
            unfocusedBorderColor = Color.Transparent,
            focusedBorderColor = Color.Transparent,
            textColor = MaterialTheme.colors.onSurface,
            cursorColor = MaterialTheme.colors.primary,
            leadingIconColor = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium),
            placeholderColor = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium)
        ),
        singleLine = true
    )
}

/**
 * 공통 프로필 아바타 컴포넌트
 */
@Composable
fun ProfileAvatar(
    profileId: Int, // 프로필 이미지 ID (getProfileImageResId 와 함께 사용)
    size: Dp = 48.dp, // 기본 크기
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null // 클릭 이벤트 처리 (선택 사항)
) {
    Image(
        painter = painterResource(id = getProfileImageResId(profileId)),
        contentDescription = "Profile Avatar",
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            // 배경색은 이미지 자체에 포함되어 있거나 투명할 경우를 대비하여 설정
            .background(MaterialTheme.colors.surface)
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
            ),
        contentScale = ContentScale.Crop // 이미지가 원 안에 꽉 차도록
    )
} 