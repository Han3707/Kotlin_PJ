package com.ssafy.lantern.ui.screens.login

import android.app.Activity
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
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
import androidx.hilt.navigation.compose.hiltViewModel
import com.ssafy.lantern.R
import com.ssafy.lantern.ui.theme.LanternTheme
import kotlinx.coroutines.launch

/**
 * 로그인 화면
 */
@Composable
fun LoginScreen(
    viewModel: LoginViewModel = hiltViewModel(),
    onSignUpClick: () -> Unit,
    onMyPageClick: () -> Unit,
    onFriendListClick: () -> Unit,
    onIncomingCallClick: () -> Unit,
    onHomeClick: () -> Unit,
    onLoginSuccess: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scaffoldState = rememberScaffoldState()
    val coroutineScope = rememberCoroutineScope()

    // Google Sign In Launcher
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.handleSignInResult(result.data)
        } else {
            Log.w("LoginScreen", "Google Sign In failed or cancelled by user. Result code: ${result.resultCode}")
            viewModel.resetStateToIdle()
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
                viewModel.resetStateToIdle()
            }
            else -> { /* Idle, Loading */ }
        }
    }

    Scaffold(
        scaffoldState = scaffoldState,
        backgroundColor = MaterialTheme.colors.background
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.lantern_image),
                    contentDescription = "Lantern Logo",
                    modifier = Modifier
                        .size(200.dp)
                        .padding(bottom = 16.dp)
                )
                
                Text(
                    text = "LANTERN",
                    color = MaterialTheme.colors.primary,
                    style = MaterialTheme.typography.h4.copy(fontWeight = FontWeight.Bold)
                )
                
                Spacer(modifier = Modifier.height(48.dp))
                
                // 구글 로그인 버튼
                OutlinedButton(
                    enabled = uiState !is LoginUiState.Loading,
                    onClick = {
                        val signInIntent = viewModel.getSignInIntent()
                        googleSignInLauncher.launch(signInIntent)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        backgroundColor = MaterialTheme.colors.surface,
                        contentColor = MaterialTheme.colors.onSurface
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.12f))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.google_logo),
                            contentDescription = "Google Logo",
                            modifier = Modifier.size(24.dp)
                        )
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        Text(
                            text = "Google로 로그인",
                            style = MaterialTheme.typography.button
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                val testButtonColor = MaterialTheme.colors.primary
                OutlinedButton(
                    onClick = onMyPageClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        backgroundColor = Color.Transparent,
                        contentColor = testButtonColor
                    ),
                    border = BorderStroke(1.dp, testButtonColor),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "마이페이지 (테스트)",
                        style = MaterialTheme.typography.button
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedButton(
                    onClick = onFriendListClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        backgroundColor = Color.Transparent,
                        contentColor = testButtonColor
                    ),
                    border = BorderStroke(1.dp, testButtonColor),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "최근 통화 (테스트)",
                        style = MaterialTheme.typography.button
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedButton(
                    onClick = onIncomingCallClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        backgroundColor = Color.Transparent,
                        contentColor = testButtonColor
                    ),
                    border = BorderStroke(1.dp, testButtonColor),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "전화 수신 화면 (테스트)",
                        style = MaterialTheme.typography.button
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedButton(
                    onClick = onHomeClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        backgroundColor = Color.Transparent,
                        contentColor = testButtonColor
                    ),
                    border = BorderStroke(1.dp, testButtonColor),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "홈 화면 (테스트)",
                        style = MaterialTheme.typography.button
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                TextButton(
                    onClick = onSignUpClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "아이디가 없으신가요? 회원가입하기",
                        color = MaterialTheme.colors.primary,
                        style = MaterialTheme.typography.button,
                        textAlign = TextAlign.Center
                    )
                }
            }

            if (uiState is LoginUiState.Loading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colors.primary
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun LoginScreenPreview() {
    LanternTheme {
        LoginScreen(
            onSignUpClick = {}, onMyPageClick = {}, onFriendListClick = {},
            onIncomingCallClick = {}, onHomeClick = {}, onLoginSuccess = {}
        )
    }
}