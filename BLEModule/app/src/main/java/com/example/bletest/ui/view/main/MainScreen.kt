package com.example.bletest.ui.view.main

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
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Chat
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bletest.data.model.ConnectionState
import com.example.bletest.ui.view.components.ConnectionStatusView
import com.example.bletest.ui.view.components.LogView
import com.example.bletest.ui.viewmodel.BleViewModel

/**
 * 메인 화면
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onNavigateToChat: () -> Unit,
    viewModel: BleViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val deviceId by viewModel.deviceId.collectAsStateWithLifecycle("")
    val isRunning by viewModel.meshRunningState.collectAsState()
    
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // 연결 상태 표시
            ConnectionStatusView(
                isConnected = isConnected,
                deviceName = connectionState.device?.name ?: connectionState.address.takeIf { it.isNotEmpty() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            )
            
            // 서비스 제어 카드
            Card(
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "서비스 제어",
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // 디바이스 ID 입력 필드 (서비스가 실행 중이 아닐 때만 활성화)
                    OutlinedTextField(
                        value = deviceIdInput,
                        onValueChange = { 
                            if (it.length <= 1) deviceIdInput = it.uppercase() 
                        },
                        label = { Text("기기 ID (A, B, C)") },
                        enabled = !isRunning,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // 서비스 시작/중지 버튼
                        Button(
                            onClick = {
                                if (!isRunning) {
                                    if (deviceIdInput.isNotEmpty() && deviceIdInput in listOf("A", "B", "C")) {
                                        viewModel.setDeviceId(deviceIdInput)
                                        val result = viewModel.startMesh()
                                        if (!result) {
                                            Toast.makeText(context, "서비스 시작 실패", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        Toast.makeText(context, "유효한 기기 ID(A, B, C)를 입력하세요", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    viewModel.stopMesh()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (isRunning) "서비스 중지" else "서비스 시작")
                        }
                        
                        Spacer(modifier = Modifier.padding(horizontal = 8.dp))
                        
                        // 채팅 화면으로 이동 버튼
                        Button(
                            onClick = onNavigateToChat,
                            enabled = isRunning,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Chat,
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                            Text("채팅")
                        }
                    }
                }
            }
            
            // 로그 메시지 카드
            Card(
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "로그",
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    LogView(
                        logs = logs,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                    )
                }
            }
        }
    }
}

// 추후 구현할 MainScreen Composable 함수가 위치할 파일입니다. 