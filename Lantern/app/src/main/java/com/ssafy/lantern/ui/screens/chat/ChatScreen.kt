package com.ssafy.lantern.ui.screens.chat

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import com.google.accompanist.permissions.*
import com.ssafy.lantern.data.model.ChatMessage as MeshChatMessage
import com.ssafy.lantern.ui.theme.LanternTheme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel = hiltViewModel(),
    navController: NavController? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val messagesListState = rememberLazyListState()
    val scannedDevicesListState = rememberLazyListState()
    var textState by remember { mutableStateOf("") }
    
    // 탭 상태 - 채팅 모드 선택용
    var tabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("일반 BLE 채팅", "BLE Mesh 채팅")
    
    // Mesh 채팅 관련 상태
    var meshTabIndex by remember { mutableStateOf(0) }
    val meshTabs = listOf("공용 채팅", "개인 채팅")
    
    // 사용자 이름 입력 상태 (Mesh 모드용)
    var showNameDialog by remember { mutableStateOf(false) }
    var nameInput by remember { mutableStateOf("") }

    // 시스템 패딩 계산
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navigationBarHeight = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val imeHeight = WindowInsets.ime.asPaddingValues().calculateBottomPadding()

    // --- 권한 처리 --- //
    val requiredPermissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            listOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
    }
    val permissionState = rememberMultiplePermissionsState(permissions = requiredPermissions) { permissionsResultMap ->
        viewModel.updatePermissionStatus(permissionsResultMap.values.all { it })
    }

    // 블루투스 활성화 요청 Launcher
    val enableBluetoothLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.updateBluetoothState(result.resultCode == android.app.Activity.RESULT_OK)
    }

    // --- 생명주기 및 초기화 --- //
    // 권한 상태 최초 확인
    LaunchedEffect(Unit) {
        viewModel.updatePermissionStatus(permissionState.permissions.all { it.status.isGranted })
    }

    // 채팅 모드가 변경될 때마다 적절한 작업 수행
    LaunchedEffect(tabIndex) {
        val mode = if (tabIndex == 0) ChatMode.CLASSIC_BLE else ChatMode.MESH_BLE
        viewModel.setChatMode(mode)
        
        // Mesh 모드로 전환 시 사용자 이름 다이얼로그 표시 (이름이 설정되지 않은 경우)
        if (mode == ChatMode.MESH_BLE && uiState.meshUserName.isBlank()) {
            showNameDialog = true
        }
    }

    LaunchedEffect(key1 = uiState.requiredPermissionsGranted, key2 = uiState.isBluetoothEnabled, key3 = uiState.chatMode) {
        if (uiState.requiredPermissionsGranted && uiState.isBluetoothEnabled) {
            viewModel.startBleOperations()
        } else {
            viewModel.stopBleOperations()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    if (uiState.requiredPermissionsGranted && uiState.isBluetoothEnabled) {
                         viewModel.startBleOperations() // 화면 돌아왔을 때 재시작
                     }
                }
                Lifecycle.Event.ON_PAUSE -> {
                    viewModel.stopBleOperations()
                }
                else -> { /* Do nothing */ }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    // Mesh 채팅 사용자 이름 입력 다이얼로그
    if (showNameDialog) {
        Dialog(onDismissRequest = {}) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "채팅에 사용할 이름을 입력해주세요",
                        style = MaterialTheme.typography.h6
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("이름") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            if (nameInput.isNotBlank()) {
                                viewModel.setUserName(nameInput)
                                showNameDialog = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("확인")
                    }
                }
            }
        }
    }

    // --- UI --- //
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("랜턴 채팅") },
                navigationIcon = navController?.let {
                    {
                        IconButton(onClick = { navController.navigateUp() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "뒤로 가기")
                        }
                    }
                },
                modifier = Modifier.padding(top = statusBarHeight)
            )
        },
        modifier = Modifier.fillMaxSize()
    ) { paddingValues ->
        // 메인 컨텐츠 영역
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp)
                    .padding(bottom = navigationBarHeight) // 하단 내비게이션 바 패딩 추가
            ) {
                // 채팅 모드 선택 탭
                TabRow(selectedTabIndex = tabIndex) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            text = { Text(title, maxLines = 1) },
                            selected = tabIndex == index,
                            onClick = { tabIndex = index }
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // 권한 및 블루투스 상태
                PermissionAndBluetoothStatus(permissionState, uiState.isBluetoothEnabled) {
                    val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                    enableBluetoothLauncher.launch(enableBtIntent)
                }
                
                // 모드에 따라 다른 UI 표시
                when (uiState.chatMode) {
                    ChatMode.CLASSIC_BLE -> {
                        // 기존 BLE 채팅 UI
                        ClassicBleChat(
                            uiState = uiState,
                            scannedDevicesListState = scannedDevicesListState,
                            messagesListState = messagesListState,
                            textState = textState,
                            onTextChange = { textState = it },
                            onConnectToDevice = { viewModel.connectToDevice(it) },
                            onSendMessage = {
                                if (textState.isNotBlank()) {
                                    viewModel.sendMessage(textState)
                                    textState = ""
                                }
                            },
                            imeHeight = imeHeight
                        )
                    }
                    ChatMode.MESH_BLE -> {
                        // Nordic BLE Mesh 채팅 UI
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .weight(1f)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize()
                            ) {
                                // 네트워크 연결 상태
                                if (!uiState.isMeshNetworkConnected) {
                                    ConnectionStatusBar(connected = false)
                                }
                                
                                // Mesh 채팅 탭 (공용/개인)
                                TabRow(selectedTabIndex = meshTabIndex) {
                                    meshTabs.forEachIndexed { index, title ->
                                        Tab(
                                            text = { Text(title) },
                                            selected = meshTabIndex == index,
                                            onClick = { 
                                                meshTabIndex = index
                                                if (index == 0) {
                                                    viewModel.selectPublicChat()
                                                }
                                            }
                                        )
                                    }
                                }
                                
                                // 탭에 따른 채팅 내용 표시
                                Box(modifier = Modifier.weight(1f)) {
                                    when (meshTabIndex) {
                                        0 -> MeshPublicChatContent(
                                            messages = uiState.meshPublicMessages,
                                            localAddress = uiState.meshLocalAddress,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        1 -> MeshPrivateChatContent(
                                            messages = uiState.meshPrivateMessages,
                                            currentPartner = uiState.meshCurrentChatPartner,
                                            localAddress = uiState.meshLocalAddress,
                                            onSelectPartner = { viewModel.selectChatPartner(it) },
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                                
                                // 메시지 입력
                                MessageInput(
                                    text = textState,
                                    onTextChange = { textState = it },
                                    onSendClick = {
                                        if (textState.isNotBlank()) {
                                            viewModel.sendMessage(textState)
                                            textState = ""
                                        }
                                    },
                                    enabled = (meshTabIndex == 0) || (uiState.meshCurrentChatPartner != null),
                                    imeHeight = imeHeight
                                )
                            }
                        }
                    }
                }

                // 에러 메시지 표시
                uiState.errorMessage?.let {
                    DisposableEffect(it) {
                        Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                        // 에러 메시지 표시 후 초기화
                        viewModel.clearErrorMessage()
                        onDispose { }
                    }
                }
            }
        }
    }
}

// 기존 BLE 채팅 UI
@Composable
private fun ClassicBleChat(
    uiState: ChatUiState,
    scannedDevicesListState: LazyListState,
    messagesListState: LazyListState,
    textState: String,
    onTextChange: (String) -> Unit,
    onConnectToDevice: (String) -> Unit,
    onSendMessage: () -> Unit,
    imeHeight: androidx.compose.ui.unit.Dp = 0.dp
) {
    Column {
        Spacer(modifier = Modifier.height(8.dp))

        // 스캔된 기기 목록 및 연결 버튼
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "검색된 기기: ${uiState.scannedDevices.size}개", 
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            
            // 스캔 상태 표시
            if (uiState.isScanning) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("스캔 중...", style = MaterialTheme.typography.caption)
                }
            }
        }
        
        if (uiState.scannedDevices.isEmpty()) {
            // 검색된 기기가 없을 때
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .padding(vertical = 8.dp),
                elevation = 2.dp,
                backgroundColor = MaterialTheme.colors.surface
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "검색된 기기가 없습니다.\n블루투스 기기를 켜고 근처에 두세요.",
                        textAlign = TextAlign.Center,
                        color = Color.Gray
                    )
                }
            }
        } else {
            // 검색된 기기 목록
            LazyColumn(
                modifier = Modifier
                    .height(120.dp)
                    .fillMaxWidth()
                    .padding(vertical = 4.dp), 
                state = scannedDevicesListState
            ) {
                items(uiState.scannedDevices.toList()) { (address, name) ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { onConnectToDevice(address) },
                        elevation = 2.dp,
                        backgroundColor = if (uiState.connectedDevice?.address == address) 
                            MaterialTheme.colors.primary.copy(alpha = 0.1f)
                        else 
                            MaterialTheme.colors.surface
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = name,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.subtitle1
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = address,
                                    style = MaterialTheme.typography.caption,
                                    color = Color.Gray
                                )
                                
                                // 제조사 표시 (이름에 포함되어 있으면 추출)
                                val manufacturer = name.substringAfter('[').substringBefore(']', "")
                                if (manufacturer.isNotBlank()) {
                                    Text(
                                        text = "제조사: $manufacturer",
                                        style = MaterialTheme.typography.caption,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF2196F3) // 파란색
                                    )
                                }
                                
                                // RSSI 표시 추가
                                val deviceRssi = uiState.deviceRssiMap[address] ?: 0
                                
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "신호 강도: ",
                                        style = MaterialTheme.typography.caption
                                    )
                                    // RSSI 값에 따른 신호 강도 표시
                                    val signalColor = when {
                                        deviceRssi > -60 -> Color(0xFF4CAF50) // 강함 (녹색)
                                        deviceRssi > -80 -> Color(0xFFFFC107) // 중간 (노란색)
                                        else -> Color(0xFFF44336) // 약함 (빨간색)
                                    }
                                    val signalText = when {
                                        deviceRssi > -60 -> "강함"
                                        deviceRssi > -80 -> "중간"
                                        else -> "약함"
                                    }
                                    Text(
                                        text = "$signalText ($deviceRssi dBm)",
                                        style = MaterialTheme.typography.caption,
                                        color = signalColor
                                    )
                                }
                            }
                            
                            Button(
                                onClick = { onConnectToDevice(address) },
                                colors = ButtonDefaults.buttonColors(
                                    backgroundColor = if (uiState.connectedDevice?.address == address)
                                        MaterialTheme.colors.primary.copy(alpha = 0.5f)
                                    else {
                                        // RSSI에 따라 버튼 색상 조정 (신호가 약하면 시각적 힌트 제공)
                                        val rssi = uiState.deviceRssiMap[address] ?: 0
                                        when {
                                            rssi > -80 -> MaterialTheme.colors.primary
                                            rssi > -90 -> Color(0xFFFF9800) // 주황색 (약한 신호)
                                            else -> Color(0xFFFF5722) // 진한 주황색 (매우 약한 신호)
                                        }
                                    }
                                ),
                                modifier = Modifier.padding(start = 8.dp)
                            ) {
                                Text(
                                    if (uiState.connectedDevice?.address == address) "연결됨" else "연결", 
                                    color = Color.White
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 연결 상태 표시
        ConnectionStatus(uiState = uiState)

        Spacer(modifier = Modifier.height(8.dp))

        // 채팅 로그
        Text("채팅 로그:", fontWeight = FontWeight.Bold)
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            state = messagesListState,
            reverseLayout = true
        ) {
            if (uiState.messages.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "메시지가 없습니다.\n연결된 기기와 대화를 시작하세요!",
                            textAlign = TextAlign.Center,
                            color = Color.Gray
                        )
                    }
                }
            } else {
                items(uiState.messages.reversed()) { message ->
                    ChatMessageItem(message)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 메시지 입력 및 전송
        MessageInput(
            text = textState,
            onTextChange = onTextChange,
            onSendClick = onSendMessage,
            enabled = uiState.connectionState == BluetoothProfile.STATE_CONNECTED,
            imeHeight = imeHeight
        )
    }
}

// 권한 및 블루투스 상태 UI
@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun PermissionAndBluetoothStatus(
    permissionState: MultiplePermissionsState,
    isBluetoothEnabled: Boolean,
    onRequestBluetoothEnable: () -> Unit
) {
    Column {
        if (!permissionState.allPermissionsGranted) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                elevation = 4.dp,
                backgroundColor = MaterialTheme.colors.error.copy(alpha = 0.1f)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("BLE 사용 권한이 필요합니다", fontWeight = FontWeight.Bold, color = MaterialTheme.colors.error)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { permissionState.launchMultiplePermissionRequest() },
                        colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.error)
                    ) {
                        Text("권한 요청", color = Color.White)
                    }
                }
            }
        }
        
        if (!isBluetoothEnabled) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                elevation = 4.dp,
                backgroundColor = MaterialTheme.colors.primary.copy(alpha = 0.1f)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("블루투스가 비활성화되어 있습니다", fontWeight = FontWeight.Bold, color = MaterialTheme.colors.primary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = onRequestBluetoothEnable) {
                        Text("블루투스 활성화")
                    }
                }
            }
        }
    }
}

// 연결 상태 표시 UI
@Composable
private fun ConnectionStatus(uiState: ChatUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = 4.dp,
        backgroundColor = when (uiState.connectionState) {
            BluetoothProfile.STATE_CONNECTED -> Color(0xFFE8F5E9) // Light Green
            BluetoothProfile.STATE_CONNECTING -> Color(0xFFFFF9C4) // Light Yellow
            else -> Color(0xFFFFEBEE) // Light Red
        }
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when (uiState.connectionState) {
                            BluetoothProfile.STATE_CONNECTED -> "연결됨"
                            BluetoothProfile.STATE_CONNECTING -> "연결 중..."
                            BluetoothProfile.STATE_DISCONNECTING -> "연결 해제 중..."
                            else -> "연결 안됨"
                        },
                        color = when (uiState.connectionState) {
                            BluetoothProfile.STATE_CONNECTED -> Color(0xFF388E3C) // Dark Green
                            BluetoothProfile.STATE_CONNECTING -> Color(0xFFFBC02D) // Dark Yellow
                            else -> Color(0xFFD32F2F) // Dark Red
                        },
                        fontWeight = FontWeight.Bold
                    )
                    
                    // 연결된 기기 정보 표시
                    if (uiState.connectionState == BluetoothProfile.STATE_CONNECTED && uiState.connectedDevice != null) {
                        val device = uiState.connectedDevice
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "기기 이름: ${device.name ?: "이름 없음"}",
                            style = MaterialTheme.typography.body2
                        )
                        Text(
                            text = "MAC 주소: ${device.address}",
                            style = MaterialTheme.typography.body2
                        )
                        
                        // BluetoothDevice 타입 상수 직접 비교
                        val deviceTypeStr = when(device.type) {
                            BluetoothDevice.DEVICE_TYPE_CLASSIC -> "기본 블루투스"
                            BluetoothDevice.DEVICE_TYPE_LE -> "블루투스 LE"
                            BluetoothDevice.DEVICE_TYPE_DUAL -> "듀얼 모드"
                            else -> "알 수 없음"
                        }
                        Text(
                            text = "기기 타입: $deviceTypeStr",
                            style = MaterialTheme.typography.body2
                        )
                    }
                }
                
                if (uiState.isConnecting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                }
            }
        }
    }
}

// Mesh 네트워크 연결 상태 표시줄
@Composable
private fun ConnectionStatusBar(connected: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .background(
                if (connected) Color(0xFFE8F5E9) else Color(0xFFFFEBEE), 
                RoundedCornerShape(4.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (connected) "메시 네트워크에 연결됨" else "메시 네트워크에 연결되지 않음",
            color = if (connected) Color(0xFF388E3C) else Color(0xFFD32F2F),
            modifier = Modifier.padding(8.dp)
        )
    }
}

// 메시지 아이템 UI
@Composable
private fun ChatMessageItem(message: ChatMessage) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = if (message.sender == "나") Arrangement.End else Arrangement.Start
    ) {
        Card(
            modifier = Modifier.widthIn(max = 280.dp),
            backgroundColor = if (message.sender == "나") MaterialTheme.colors.primary else Color.LightGray,
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(8.dp)
            ) {
                Text(
                    text = message.sender,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (message.sender == "나") Color.White else Color.Black
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = message.text,
                    color = if (message.sender == "나") Color.White else Color.Black
                )
                Text(
                    text = formatTimestamp(message.timestamp),
                    fontSize = 10.sp,
                    color = if (message.sender == "나") Color.White.copy(alpha = 0.7f) else Color.Black.copy(alpha = 0.7f),
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}

// 메시지 입력 UI
@Composable
private fun MessageInput(
    text: String,
    onTextChange: (String) -> Unit,
    onSendClick: () -> Unit,
    enabled: Boolean = true,
    imeHeight: androidx.compose.ui.unit.Dp = 0.dp
) {
    val keyboardPadding = if (imeHeight > 0.dp) imeHeight else 16.dp
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .padding(bottom = if (imeHeight > 0.dp) 0.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            placeholder = { Text("메시지 입력") },
            enabled = enabled,
            modifier = Modifier
                .weight(1f)
                .heightIn(max = 120.dp), // 최대 높이 제한
            maxLines = 3 // 최대 3줄까지만 표시
        )
        
        Spacer(modifier = Modifier.width(8.dp))
        
        Button(
            onClick = onSendClick,
            enabled = enabled && text.isNotBlank(),
            modifier = Modifier.padding(vertical = 4.dp)
        ) {
            Text("전송")
        }
    }
}

// Mesh 공용 채팅 내용
@Composable
private fun MeshPublicChatContent(
    messages: List<MeshChatMessage>,
    localAddress: Int?,
    modifier: Modifier = Modifier
) {
    if (messages.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("아직 메시지가 없습니다.")
        }
    } else {
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalArrangement = Arrangement.Bottom
        ) {
            items(messages) { message ->
                val isMyMessage = message.fromAddress == localAddress
                MeshChatMessageItem(
                    message = message,
                    isMyMessage = isMyMessage,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

// Mesh 개인 채팅 내용
@Composable
private fun MeshPrivateChatContent(
    messages: Map<Int, List<MeshChatMessage>>,
    currentPartner: Int?,
    localAddress: Int?,
    onSelectPartner: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        if (currentPartner == null) {
            // 채팅 상대 선택 화면
            Text(
                text = "채팅 상대 선택",
                style = MaterialTheme.typography.subtitle1,
                modifier = Modifier.padding(16.dp)
            )
            
            if (messages.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("아직 메시지가 없습니다.")
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(messages.keys.toList()) { partnerAddress ->
                        PartnerItem(
                            address = partnerAddress,
                            lastMessage = messages[partnerAddress]?.lastOrNull(),
                            onClick = { onSelectPartner(partnerAddress) }
                        )
                    }
                }
            }
        } else {
            // 1:1 채팅 화면
            val partnerMessages = messages[currentPartner] ?: emptyList()
            
            if (partnerMessages.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("아직 메시지가 없습니다.")
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    verticalArrangement = Arrangement.Bottom
                ) {
                    items(partnerMessages) { message ->
                        val isMyMessage = message.fromAddress == localAddress
                        MeshChatMessageItem(
                            message = message,
                            isMyMessage = isMyMessage,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

// Mesh 채팅 상대 항목
@Composable
private fun PartnerItem(
    address: Int,
    lastMessage: MeshChatMessage?,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .clickable(onClick = onClick),
        elevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = if (lastMessage?.senderName?.isNotBlank() == true) {
                    lastMessage.senderName
                } else {
                    "주소: ${address.toString(16).uppercase()}"
                },
                style = MaterialTheme.typography.subtitle1,
                fontWeight = FontWeight.Bold
            )
            
            lastMessage?.let {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = it.content,
                    style = MaterialTheme.typography.body2,
                    maxLines = 1
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                        .format(it.dateTime),
                    style = MaterialTheme.typography.caption,
                    color = Color.Gray
                )
            }
        }
    }
}

// Mesh 채팅 메시지 아이템
@Composable
private fun MeshChatMessageItem(
    message: MeshChatMessage,
    isMyMessage: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(vertical = 4.dp),
        horizontalArrangement = if (isMyMessage) Arrangement.End else Arrangement.Start
    ) {
        if (isMyMessage) {
            Spacer(modifier = Modifier.weight(0.15f))
        }
        
        Column(
            modifier = Modifier.weight(0.85f),
            horizontalAlignment = if (isMyMessage) Alignment.End else Alignment.Start
        ) {
            // 발신자 이름 (자신의 메시지가 아닌 경우에만 표시)
            if (!isMyMessage && message.senderName.isNotBlank()) {
                Text(
                    text = message.senderName,
                    style = MaterialTheme.typography.caption,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp, end = 8.dp)
                )
            }
            
            // 메시지 내용
            Card(
                shape = RoundedCornerShape(8.dp),
                backgroundColor = if (isMyMessage) 
                    MaterialTheme.colors.primary.copy(alpha = 0.8f) 
                else 
                    Color.LightGray,
                modifier = Modifier.padding(vertical = 2.dp)
            ) {
                Text(
                    text = message.content,
                    color = if (isMyMessage) Color.White else Color.Black,
                    modifier = Modifier.padding(8.dp)
                )
            }
            
            // 시간
            Text(
                text = SimpleDateFormat("HH:mm", Locale.getDefault())
                    .format(message.dateTime),
                style = MaterialTheme.typography.caption,
                color = Color.Gray,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }
        
        if (!isMyMessage) {
            Spacer(modifier = Modifier.weight(0.15f))
        }
    }
}

// 타임스탬프 포맷팅 유틸리티 함수
private fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

@Preview(showBackground = true)
@Composable
private fun ChatMessageItemPreview() {
    LanternTheme {
        Column {
            ChatMessageItem(ChatMessage("나", "안녕하세요! 테스트 메시지입니다."))
            Spacer(modifier = Modifier.height(8.dp))
            ChatMessageItem(ChatMessage("상대방", "반갑습니다! 저도 테스트 중입니다."))
        }
    }
} 