package com.ssafy.lantern.ui.screens.mypage

import android.widget.Toast // 토스트 메시지용
import androidx.annotation.DrawableRes
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import com.ssafy.lantern.R
import com.ssafy.lantern.data.model.User
import com.ssafy.lantern.ui.theme.LanternTheme
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.material.ContentAlpha

@Composable
fun MyPageScreen(
    viewModel: MyPageViewModel = hiltViewModel(),
    onNavigateToLogin: () -> Unit,
    popBackStack: () -> Unit,
    paddingValues: PaddingValues // MainScaffold로부터 전달받는 패딩
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showImageSelectionDialog by remember { mutableStateOf(false) }

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
            // TODO: 필요시 에러 메시지 상태 초기화 viewModel.clearErrorMessage()
        }
    }

    // 이미지 선택 다이얼로그
    if (showImageSelectionDialog) {
        ProfileImageSelectionDialog(
            availableImages = uiState.availableProfileImages,
            onDismiss = { showImageSelectionDialog = false },
            onImageSelected = { selectedResId ->
                viewModel.updateProfileImage(selectedResId)
                showImageSelectionDialog = false
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background)
            .padding(paddingValues) // 하단 네비게이션 바 고려
    ) {
        // Top Bar
        Row(
             modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colors.onBackground,
                modifier = Modifier.size(24.dp).clickable { popBackStack() }
            )
            Text(
                 text = "프로필 수정",
                color = MaterialTheme.colors.onBackground, fontSize = 25.sp, fontWeight = FontWeight.Normal,
                letterSpacing = (-0.25).sp
            )
            Text(
                 text = if (uiState.isEditing) "완료" else "수정",
                color = MaterialTheme.colors.primary, fontSize = 15.sp, fontWeight = FontWeight.Normal,
                letterSpacing = (-0.15).sp,
                modifier = Modifier.clickable {
                    if (uiState.isEditing) viewModel.saveProfileChanges()
                    else viewModel.toggleEditMode()
                }
            )
        }

        // Content Area
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp)
                .padding(top = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Profile Image Section
            Box(contentAlignment = Alignment.Center) {
                Image(
                    painter = painterResource(id = uiState.profileImageResId),
                    contentDescription = "Profile Image",
                    modifier = Modifier
                        .size(160.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.disabled))
                        .clickable(enabled = uiState.isEditing) {
                            if (uiState.isEditing) {
                                showImageSelectionDialog = true
                            }
                        },
                    contentScale = ContentScale.Crop
                )

                 if (uiState.isEditing) {
                     Box(
                         modifier = Modifier
                             .size(40.dp)
                             .clip(CircleShape)
                             .background(MaterialTheme.colors.primary)
                             .align(Alignment.BottomEnd)
                             .offset(x = 8.dp, y = 8.dp)
                             .clickable { showImageSelectionDialog = true }
                             .zIndex(1f),
                         contentAlignment = Alignment.Center
                     ) {
                         Icon(
                             imageVector = Icons.Default.Edit,
                             contentDescription = "Edit Profile Image",
                             tint = MaterialTheme.colors.onPrimary,
                             modifier = Modifier.size(20.dp)
                         )
                     }
                 }
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Nickname Field
            ProfileEditField(
                label = "닉네임",
                value = uiState.nicknameInput,
                onValueChange = viewModel::updateNickname,
                isEditing = uiState.isEditing
            )

            Spacer(modifier = Modifier.height(24.dp))

             ProfileDisplayField(
                 label = "이메일",
                 value = "이메일 정보 없음 (임시)"
             )

            Spacer(modifier = Modifier.weight(1f))

            // Log Out Button
            Button(
                onClick = { viewModel.logout() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.primary)
            ) {
                Text(
                    text = "로그아웃",
                    fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colors.onPrimary
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // 로딩 인디케이터
        if (uiState.isLoading) {
             Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colors.background.copy(alpha=0.5f)), contentAlignment = Alignment.Center) {
                 CircularProgressIndicator(color = MaterialTheme.colors.primary)
             }
        }
    }
}

@Composable
fun ProfileEditField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    isEditing: Boolean,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colors.onBackground
        )
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            Text(
                text = value.ifEmpty { " " },
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colors.onBackground,
                modifier = Modifier.alpha(if (isEditing) 0f else 1f)
            )
            if (isEditing) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    textStyle = TextStyle(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colors.onBackground
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    cursorBrush = SolidColor(MaterialTheme.colors.primary)
                )
            }
        }
        Divider(
            color = MaterialTheme.colors.onBackground,
            thickness = 1.dp,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
fun ProfileDisplayField(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colors.onBackground
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = value,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colors.onBackground.copy(alpha = ContentAlpha.medium)
        )
        Divider(
            color = MaterialTheme.colors.onBackground,
            thickness = 1.dp,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
fun ProfileImageSelectionDialog(
    availableImages: List<Int>,
    onDismiss: () -> Unit,
    onImageSelected: (Int) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colors.surface
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("프로필 이미지 선택", fontSize = 20.sp, color = MaterialTheme.colors.onSurface, modifier = Modifier.padding(bottom = 16.dp))
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 80.dp),
                    contentPadding = PaddingValues(4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(availableImages) { imageResId ->
                        Image(
                            painter = painterResource(id = imageResId),
                            contentDescription = "Profile Option $imageResId",
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .clickable { onImageSelected(imageResId) }
                                .border(BorderStroke(1.dp, MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.disabled)), CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("취소")
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFF)
@Composable
fun MyPageScreenPreview() {
    LanternTheme {
         // FIXME: 아래 User 객체 생성은 실제 data/model/User.kt 정의와 일치해야 합니다.
         //        현재는 컴파일 오류를 피하기 위해 임의의 값을 사용하고 있습니다.
         val previewUser = User(
             userId = 0L, // FIXME: String 타입 오류 -> 임시로 Long 타입 0L 전달. 실제 타입 확인 필요.
             nickname = "@previewUser",
             deviceId = "preview_device_id", // FIXME: 누락된 파라미터 -> 임시로 문자열 전달. 실제 타입 확인 필요.
             // --- 아래 필드들은 실제 User 모델에 정의되어 있는지 확인하고, 타입에 맞춰 주석 해제 또는 수정 필요 --- 
             // email = "preview@example.com",
             // name = "프리뷰",
             // profileImageResId = R.drawable.lantern_image,
         )
         val previewUiState = MyPageUiState(
             user = previewUser,
             nicknameInput = "@previewUser",
             profileImageResId = R.drawable.lantern_image,
             availableProfileImages = defaultProfileImages
         )
         val isEditing = remember { mutableStateOf(false) }
         var currentImage by remember { mutableStateOf(previewUiState.profileImageResId) }
         var currentNickname by remember { mutableStateOf(previewUiState.nicknameInput)}
         var showDialog by remember { mutableStateOf(false) }

        if(showDialog) {
             ProfileImageSelectionDialog(
                 availableImages = previewUiState.availableProfileImages,
                 onDismiss = { showDialog = false },
                 onImageSelected = { currentImage = it; showDialog = false} )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colors.background)
        ) {
             Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.ArrowBack, "Back", tint=MaterialTheme.colors.onBackground, modifier = Modifier.clickable { })
                Text("프로필 수정", color = MaterialTheme.colors.onBackground, fontSize = 25.sp)
                Text(if (isEditing.value) "완료" else "수정", color = MaterialTheme.colors.primary, fontSize = 15.sp, modifier = Modifier.clickable { isEditing.value = !isEditing.value })
            }
             Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                 Box(contentAlignment = Alignment.Center) {
                     Image(
                         painterResource(id = currentImage), "", // 기본값 사용
                         modifier = Modifier.size(160.dp).clip(CircleShape).clickable(enabled = isEditing.value) { if(isEditing.value) showDialog = true },
                         contentScale = ContentScale.Crop
                     )
                     if (isEditing.value) {
                          Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colors.primary)
                                    .align(Alignment.BottomEnd)
                                    .offset(x = 8.dp, y = 8.dp)
                                    .clickable { showDialog = true }
                                    .zIndex(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Edit, "", tint = MaterialTheme.colors.onPrimary, modifier = Modifier.size(20.dp))
                            }
                      }
                 }
                Spacer(modifier = Modifier.height(48.dp))
                ProfileEditField("닉네임", currentNickname, { currentNickname = it }, isEditing.value)
                Spacer(modifier = Modifier.height(24.dp))
                // FIXME: User 모델에 email 필드가 정의되면 아래 코드로 대체해야 합니다.
                // ProfileDisplayField("이메일", previewUser.email ?: "이메일 정보 없음")
                ProfileDisplayField("이메일", "이메일 정보 없음 (임시)")
                Spacer(modifier = Modifier.weight(1f))
                Button(onClick = { }, modifier = Modifier.fillMaxWidth().height(60.dp), shape=RoundedCornerShape(20.dp), colors = ButtonDefaults.buttonColors(MaterialTheme.colors.primary)) {
                    Text("로그아웃", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colors.onPrimary)
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}