package com.example.bletest.ui.view.main

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bletest.data.model.ConnectionState
import com.example.bletest.ui.view.components.ConnectionStatusView
import com.example.bletest.ui.view.components.LogView
import com.example.bletest.ui.viewmodel.BleViewModel
import com.example.bletest.utils.PermissionHelper

/**
 * 메인 화면
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onNavigateToChat: () -> Unit,
    onNavigateToPublicChat: () -> Unit,
    viewModel: BleViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val deviceId by viewModel.deviceId.collectAsStateWithLifecycle("")
    
    // 권한 확인 및 요청 로직
    val permissionHelper = remember { PermissionHelper }
    val hasRequiredPermissions = permissionHelper.hasRequiredPermissions(context)
    
    // 앱에 필요한 권한이 없는 경우 알림
    LaunchedEffect(key1 = Unit) {
        if (!hasRequiredPermissions) {
            // 사용자에게 필요한 권한이 왜 필요한지 알려주는 토스트 메시지 표시
            Toast.makeText(
                context,
                "일부 권한이 없어 BLE 기능이 제한될 수 있습니다.",
                Toast.LENGTH_LONG
            ).show()
        }
    }
    
    // 로그 메시지 관리
    val logs = remember { mutableStateListOf<String>() }
    
    // 단일 로그 메시지 수집
    LaunchedEffect(key1 = true) {
        viewModel.logEvents.collect { message ->
            if (message.isNotEmpty()) {
                logs.add(message)
                // 로그가 너무 많아지면 오래된 것부터 삭제
                if (logs.size > 100) {
                    logs.removeAt(0)
                }
            }
        }
    }
    
    // 연결 상태 관찰
    val connectionState by viewModel.connectionState.collectAsState()
    val isConnected by remember {
        derivedStateOf {
            connectionState.state == ConnectionState.CONNECTED
        }
    }
    
    // 디바이스 ID 입력
    var deviceIdInput by remember { mutableStateOf("") }
    
    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                title = { Text("BLE 메시 네트워크") }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            // 채팅방 입장 버튼 영역
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "채팅방",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        // 1:1 채팅 버튼
                        Button(
                            onClick = onNavigateToChat,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "1:1 채팅")
                            Spacer(modifier = Modifier.padding(4.dp))
                            Text("1:1 채팅")
                        }
                        
                        Spacer(modifier = Modifier.padding(8.dp))
                        
                        // 공통 채팅방 버튼
                        Button(
                            onClick = { 
                                Log.d("MainScreen", "공통 채팅방 버튼 클릭됨 - onNavigateToPublicChat 호출")
                                onNavigateToPublicChat() 
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Forum, contentDescription = "공통 채팅방")
                            Spacer(modifier = Modifier.padding(4.dp))
                            Text("공통 채팅방")
                        }
                    }
                }
            }
            
            // 메시 시작/종료 상태 및 버튼
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    // 메시 네트워킹 제어
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val isMeshRunning by viewModel.meshRunningState.collectAsStateWithLifecycle()
                        
                        Text(
                            text = if (isMeshRunning) "메시 네트워크 실행 중" else "메시 네트워크 중지됨",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        
                        Button(
                            onClick = {
                                Log.d("MainScreen", "메시 시작/종료 버튼 클릭됨")
                                if (isMeshRunning) {
                                    viewModel.stopPublicChatRoom() // 메시 네트워킹 중지 함수 호출
                                    Toast.makeText(context, "메시 네트워킹 중지됨", Toast.LENGTH_SHORT).show()
                                } else {
                                    // 메시 네트워킹 시작 함수 호출
                                    val result = viewModel.startPublicChatRoom()
                                    if (result) {
                                        Toast.makeText(context, "메시 네트워킹 시작 중...", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "메시 네트워킹 시작 실패", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        ) {
                            Text(if (isMeshRunning) "메시 종료" else "메시 시작")
                        }
                    }
                }
            }
            
            // 연결 상태 카드
            ConnectionStatusView(
                connectionState = connectionState,
                isConnected = isConnected,
                deviceName = connectionState.name ?: connectionState.address.takeIf { it.isNotEmpty() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
            
            // 발견된 디바이스 목록
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "디바이스 ID 설정",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = deviceIdInput,
                            onValueChange = { deviceIdInput = it },
                            label = { Text("디바이스 ID") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.None
                            )
                        )
                        
                        Spacer(modifier = Modifier.padding(8.dp))
                        
                        Button(
                            onClick = {
                                if (deviceIdInput.isNotBlank()) {
                                    viewModel.setDeviceId(deviceIdInput)
                                    Toast.makeText(context, "디바이스 ID가 설정되었습니다: $deviceIdInput", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "유효한 디바이스 ID를 입력하세요", Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Text("설정")
                        }
                    }
                    
                    if (deviceId.isNotEmpty()) {
                        Text(
                            text = "현재 ID: $deviceId",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
            
            // 로그 뷰
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "로그",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    LogView(
                        logs = logs,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                    )
                }
            }
            
            // 여백
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// 추후 구현할 MainScreen Composable 함수가 위치할 파일입니다. 