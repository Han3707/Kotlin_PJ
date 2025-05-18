package com.ssafy.lanterns.ui.screens.mypage

import android.widget.Toast // 토스트 메시지용
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import com.ssafy.lanterns.R
import com.ssafy.lanterns.data.model.User
import com.ssafy.lanterns.ui.theme.*
import com.ssafy.lanterns.ui.theme.LanternsTheme
import com.ssafy.lanterns.ui.util.getAllProfileImageResources
import com.ssafy.lanterns.ui.util.getProfileImageByNumber
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyPageScreen(
    viewModel: MyPageViewModel = hiltViewModel(),
    onNavigateToLogin: () -> Unit,
    paddingValues: PaddingValues // MainScaffold로부터 전달받는 패딩
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showImageSelectionDialog by remember { mutableStateOf(false) }
    var showLogoutConfirmDialog by remember { mutableStateOf(false) }

    // 로그아웃 이벤트 감지
    LaunchedEffect(key1 = viewModel.logoutEvent) {
        viewModel.logoutEvent.collectLatest {
            Toast.makeText(context, "로그아웃 되었습니다.", Toast.LENGTH_SHORT).show()
            onNavigateToLogin()
        }
    }

    // 에러 메시지 표시
    LaunchedEffect(key1 = uiState.errorMessage) {
        uiState.errorMessage?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            viewModel.clearErrorMessage()
        }
    }

    // 이미지 선택 다이얼로그
    if (showImageSelectionDialog) {
        ProfileImageSelectionDialog(
            availableImageResources = uiState.availableProfileImageResources,
            onDismiss = { showImageSelectionDialog = false },
            onImageSelected = { selectedNumber ->
                viewModel.updateSelectedProfileImageNumber(selectedNumber)
                showImageSelectionDialog = false
            },
        )
    }

    // 로그아웃 확인 다이얼로그
    if (showLogoutConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirmDialog = false },
            title = { Text("로그아웃") },
            text = { Text("정말 로그아웃 하시겠습니까?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutConfirmDialog = false
                        viewModel.logout()
                    }
                ) {
                    Text("확인", color = LanternYellow)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showLogoutConfirmDialog = false }
                ) {
                    Text("취소")
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    val textColor = MaterialTheme.colorScheme.onBackground
    val textColorSecondary = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(paddingValues) // 하단 네비게이션 바 고려
    ) {
        // 로딩 중이면 로딩 인디케이터 표시
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = LanternYellow) // LanternYellow로 고정
            }
        }

        // Content Area
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp)
                .padding(top = 24.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 상단 헤더
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "프로필",
                    color = textColor,
                    fontSize = 25.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.25).sp
                )
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 로그아웃 아이콘 버튼
                    IconButton(
                        onClick = { showLogoutConfirmDialog = true },
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = "로그아웃",
                            tint = textColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    
                    // 편집 버튼
                    Button(
                        onClick = {
                            if (uiState.isEditing) viewModel.saveProfileChanges()
                            else viewModel.toggleEditMode()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = LanternYellow, // LanternYellow로 고정
                            contentColor = Color.Black // 검정색 글자로 고정
                        ),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.height(40.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = if (uiState.isEditing) "완료" else "수정",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Profile Image Section
            Box(contentAlignment = Alignment.Center) {
                val avatarSize = 180.dp
                val subtleBorderThickness = 1.dp
                val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                val imageBackground = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)

                Box(
                    modifier = Modifier
                        .size(avatarSize)
                        .clip(CircleShape)
                        .background(imageBackground)
                        .border(
                            width = subtleBorderThickness,
                            color = borderColor,
                            shape = CircleShape
                        )
                ) {
                    val actualImageResId = getProfileImageByNumber(uiState.selectedProfileImageNumber)
                    Image(
                        painter = painterResource(id = actualImageResId),
                        contentDescription = "Profile Image",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .padding(subtleBorderThickness)
                            .clickable(enabled = uiState.isEditing) {
                                if (uiState.isEditing) {
                                    showImageSelectionDialog = true
                                }
                            },
                        contentScale = ContentScale.Crop
                    )
                }

                if (uiState.isEditing) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(LanternYellow) // LanternYellow로 고정
                            .align(Alignment.BottomEnd)
                            .offset(x = 8.dp, y = 8.dp)
                            .clickable { showImageSelectionDialog = true }
                            .zIndex(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Profile Image",
                            tint = Color.Black, // 검정색 글자로 고정
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Nickname Field
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "닉네임",
                    color = textColorSecondary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                if (uiState.isEditing) {
                    OutlinedTextField(
                        value = uiState.nicknameInput,
                        onValueChange = viewModel::updateNickname,
                        textStyle = TextStyle(
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = textColor
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                RoundedCornerShape(8.dp)
                            ),
                        shape = RoundedCornerShape(8.dp),
                        placeholder = {
                            Text(
                                "닉네임을 입력하세요",
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                fontSize = 18.sp
                            )
                        },
                        singleLine = true
                    )
                } else {
                    Text(
                        text = uiState.nicknameInput.ifEmpty { "-" },
                        color = textColor,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(vertical = 16.dp, horizontal = 16.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                    thickness = 1.dp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Email Field
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "이메일",
                    color = textColorSecondary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = uiState.email,
                    color = textColor,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(vertical = 16.dp, horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                    thickness = 1.dp
                )
            }
        }
    }
}

@Composable
fun ProfileImageSelectionDialog(
    availableImageResources: Map<Int, Int>,
    onDismiss: () -> Unit,
    onImageSelected: (Int) -> Unit,
) {
    val backgroundColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
    val dialogTextColor = MaterialTheme.colorScheme.onSurface

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(animationSpec = tween(300)) + 
                   slideInVertically(
                       animationSpec = tween(300, easing = FastOutSlowInEasing),
                       initialOffsetY = { it / 2 }
                   ),
            exit = fadeOut(animationSpec = tween(300)) +
                   slideOutVertically(
                       animationSpec = tween(300, easing = FastOutSlowInEasing),
                       targetOffsetY = { it / 2 }
                   )
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = backgroundColor,
                border = BorderStroke(
                    width = 1.dp,
                    color = LanternYellow // LanternYellow로 고정
                )
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "프로필 이미지 선택",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = dialogTextColor,
                        modifier = Modifier.padding(bottom = 20.dp)
                    )
                    
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .heightIn(max = 300.dp) 
                            .padding(8.dp)
                    ) {
                        items(availableImageResources.entries.toList()) { entry ->
                            val number = entry.key
                            val imageResId = entry.value
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(CircleShape)
                                    .border(
                                        BorderStroke(
                                            2.dp,
                                            LanternYellow // LanternYellow로 고정
                                        ),
                                        CircleShape
                                    )
                                    .clickable { onImageSelected(number) },
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(id = imageResId),
                                    contentDescription = "Profile Option $number",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = LanternYellow // LanternYellow로 고정
                        )
                    ) {
                        Text(
                            "취소",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black // 검정색 글자로 고정
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun getThemeAccentColor(): Color {
    return LanternYellow
}

@Composable
fun getButtonTextColor(): Color {
    return Color.Black
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true, backgroundColor = 0xFF0A0F2C)
@Composable
fun MyPageScreenPreview() {
    val previewUiState = MyPageUiState(
        user = User(userId = 1L, email = "user@example.com", nickname = "랜턴이", selectedProfileImageNumber = 1, deviceId = "preview-device-id"),
        nicknameInput = "랜턴이",
        email = "user@example.com"
    )

    LanternsTheme(darkTheme = true) { 
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            val textColor = MaterialTheme.colorScheme.onBackground
            val textColorSecondary = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(PaddingValues(0.dp))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 32.dp)
                        .padding(top = 24.dp, bottom = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 상단 헤더
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 24.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "프로필",
                            color = textColor,
                            fontSize = 25.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.25).sp
                        )
                        
                        // 상단 버튼 그룹
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // 로그아웃 아이콘 버튼
                            IconButton(
                                onClick = { },
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        shape = CircleShape
                                    )
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Logout,
                                    contentDescription = "로그아웃",
                                    tint = textColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            
                            // 편집 버튼
                            Button(
                                onClick = { },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = LanternYellow,
                                    contentColor = Color.Black
                                ),
                                shape = RoundedCornerShape(24.dp),
                                modifier = Modifier.height(40.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text("수정", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(32.dp))

                    Box(contentAlignment = Alignment.Center) {
                        val avatarSize = 180.dp
                        val subtleBorderThickness = 1.dp
                        val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        val imageBackground = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)

                        Box(
                            modifier = Modifier
                                .size(avatarSize)
                                .clip(CircleShape)
                                .background(imageBackground)
                                .border(width = subtleBorderThickness, color = borderColor, shape = CircleShape)
                        ) {
                            Image(
                                painter = painterResource(id = getProfileImageByNumber(previewUiState.selectedProfileImageNumber)),
                                contentDescription = "Profile Image",
                                modifier = Modifier.fillMaxSize().clip(CircleShape).padding(subtleBorderThickness),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(48.dp))

                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text("닉네임", color = textColorSecondary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            previewUiState.nicknameInput.ifEmpty { "-" },
                            color = textColor, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp)).padding(vertical = 16.dp, horizontal = 16.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), thickness = 1.dp)
                    }
                    Spacer(modifier = Modifier.height(24.dp))

                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text("이메일", color = textColorSecondary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            previewUiState.email,
                            color = textColor, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp)).padding(vertical = 16.dp, horizontal = 16.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), thickness = 1.dp)
                    }
                }
            }
        }
    }
}