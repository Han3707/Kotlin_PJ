package com.ssafy.lanterns.ui.screens.login

import android.app.Activity
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.outlined.MovieFilter
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.ssafy.lanterns.R
import com.ssafy.lanterns.ui.theme.LanternsTheme
import kotlinx.coroutines.launch

private const val TAG = "LoginScreenImpl"

/**
 * 로그인 화면
 */
@UnstableApi
@Composable
fun LoginScreen(
    viewModel: LoginViewModel = hiltViewModel(),
    onLoginSuccess: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentVideoUri by viewModel.currentVideoUri.collectAsState()
    val isVideoASelected by viewModel.isVideoASelected.collectAsState()

    val context = LocalContext.current
    val scaffoldState = rememberScaffoldState()
    val coroutineScope = rememberCoroutineScope()

    // 시스템 UI(상단바, 하단바) 상태를 전체 화면으로 설정
    val activity = context as? Activity
    LaunchedEffect(Unit) {
        activity?.let {
            WindowCompat.setDecorFitsSystemWindows(it.window, false)
        }
    }

    // ExoPlayer 인스턴스
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true // 자동 재생
            repeatMode = Player.REPEAT_MODE_ONE // 단일 항목 반복
        }
    }

    // 비디오 URI가 변경될 때 ExoPlayer 업데이트
    LaunchedEffect(currentVideoUri) {
        if (currentVideoUri != android.net.Uri.EMPTY) {
            val mediaItem = MediaItem.fromUri(currentVideoUri)
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            Log.d(TAG, "Playing video: $currentVideoUri")
        } else {
            Log.d(TAG, "Video URI is empty, ExoPlayer not prepared.")
        }
    }

    // 화면에서 벗어날 때 ExoPlayer 해제
    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
            Log.d(TAG, "ExoPlayer released")
        }
    }

    // Google Sign In Launcher
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val resultCode = result.resultCode
        val data = result.data

        if (resultCode == Activity.RESULT_OK) {
            Log.d(TAG, "Google Sign-In successful, handling result.")
            viewModel.handleSignInResult(data)
        } else {
            Log.w(TAG, "Google Sign-In failed or cancelled. Result code: $resultCode")
            if (data != null) {
                // 실패 시에도 ViewModel에서 오류를 처리하도록 전달 가능
                viewModel.handleSignInResult(data) 
            } else {
                // 또는 단순히 UI 상태를 초기화
                viewModel.resetStateToIdle()
            }
        }
    }

    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is LoginUiState.Success -> {
                Toast.makeText(context, "${state.user.nickname}님 환영합니다!", Toast.LENGTH_SHORT).show()
                onLoginSuccess()
            }
            is LoginUiState.Error -> {
                coroutineScope.launch {
                    scaffoldState.snackbarHostState.showSnackbar(
                        message = state.message,
                        duration = SnackbarDuration.Short
                    )
                }
                // 에러 발생 후 Idle 상태로 자동 전환은 ViewModel에서 관리하거나 여기서 명시적으로 호출
                 viewModel.resetStateToIdle() // 예: 로그인 실패 시 다시 시도 가능하도록
            }
            else -> { /* Idle, Loading */ }
        }
    }

    Scaffold(
        scaffoldState = scaffoldState,
        backgroundColor = Color.Black // 비디오 로딩 전 또는 비디오 없는 경우 배경색
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .systemBarsPadding() // 시스템 바 영역 패딩 추가
        ) {
            // 비디오 플레이어 뷰 (배경)
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false // 컨트롤러 UI 숨김
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM // 화면을 꽉 채움
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // 상단 LANTERN 로고 텍스트
            Text(
                text = "LANTERN",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 40.sp, // 글자 크기 키움 (기존 32.sp에서 증가)
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 80.dp) // 상단 여백 증가
            )

            // 오른쪽 상단 비디오 전환 버튼
            IconButton(
                onClick = { viewModel.toggleVideo() },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            ) {
                Icon(
                    imageVector = if (isVideoASelected) Icons.Filled.Movie else Icons.Outlined.MovieFilter,
                    contentDescription = "Toggle Video",
                    tint = Color.White // 아이콘 색상을 비디오 위에서 잘 보이도록 흰색으로 설정
                )
            }

            // 하단 중앙 구글 로그인 버튼
            OutlinedButton(
                enabled = uiState !is LoginUiState.Loading, // 로딩 중 아닐 때만 활성화
                onClick = {
                    val signInIntent = viewModel.getSignInIntent()
                    googleSignInLauncher.launch(signInIntent)
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(0.8f) // 너비는 화면의 80%
                    .padding(bottom = 80.dp) // 하단 여백 더 증가 (기존 96.dp에서 증가)
                    .height(56.dp), // 버튼 높이 유지
                colors = ButtonDefaults.outlinedButtonColors(
                    backgroundColor = Color.Black.copy(alpha = 0.7f), // 약간 더 어두운 배경
                    contentColor = Color.White // 버튼 내부 글자/아이콘 색상
                ),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.6f))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center, // 가운데 정렬
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.google_logo),
                        contentDescription = "Google Logo",
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp)) // 로고와 텍스트 사이 간격 조정
                    Text(
                        text = "Google로 로그인",
                        style = MaterialTheme.typography.button.copy(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }

            // 로딩 인디케이터 (화면 중앙)
            if (uiState is LoginUiState.Loading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White
                )
            }
        }
    }
}

@UnstableApi
@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun LoginScreenPreview() {
    LanternsTheme {
        // Preview에서는 Hilt ViewModel을 직접 주입하기 어려우므로,
        // 실제 ViewModel의 기능을 모방하거나, 필요한 최소한의 데이터를 가진
        // 가짜 ViewModel을 만들어 사용하는 것이 일반적입니다.
        // 여기서는 onLoginSuccess 콜백만 전달하는 가장 기본적인 형태로 둡니다.
        // 실제 앱 실행 시 Hilt가 올바른 ViewModel을 주입합니다.
        LoginScreen(onLoginSuccess = { Log.d("Preview", "Login Success Clicked") })
    }
}