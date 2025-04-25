package com.example.ble_kotlin.UI

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ble_kotlin.BleManager.ConnectionState
import com.example.ble_kotlin.BleManager.PermissionState
import com.example.ble_kotlin.ViewModel.BleEvent
import com.example.ble_kotlin.ViewModel.BleStatus
import com.example.ble_kotlin.ViewModel.BleViewModel
import com.example.ble_kotlin.ui.theme.BleKotlinTheme
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * BLE 앱의 메인 화면 Composable.
 * 모든 UI 컴포넌트를 조합하고 ViewModel과 통합합니다.
 */
@Composable
fun AppScreen() {
    BleKotlinTheme {
        // ViewModel 인스턴스 가져오기
        val viewModel: BleViewModel = viewModel()
        
        // ViewModel의 StateFlow 수집
        val uiState by viewModel.uiState.collectAsState()
        val scannedDevices by viewModel.scannedDevices.collectAsState()
        val chatMessages by viewModel.chatMessages.collectAsState()
        
        // SnackBar 상태 관리
        val snackbarHostState = remember { SnackbarHostState() }
        val coroutineScope = rememberCoroutineScope()
        
        // 생명주기 관리
        val lifecycleOwner = LocalLifecycleOwner.current
        
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> {
                        // 앱이 포그라운드로 돌아올 때
                        viewModel.resumeOperations()
                    }
                    Lifecycle.Event.ON_STOP -> {
                        // 앱이 백그라운드로 갈 때 (화면이 보이지 않을 때)
                        viewModel.pauseOperations()
                    }
                    else -> { /* 다른 이벤트는 처리하지 않음 */ }
                }
            }
            
            // 옵저버 등록
            lifecycleOwner.lifecycle.addObserver(observer)
            
            // 정리(cleanup) 작업
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }
        
        // 초기 권한 체크는 MainActivity에서 수행하므로 여기선 생략
        
        // 이벤트 처리
        LaunchedEffect(Unit) {
            viewModel.events.collectLatest { event ->
                when (event) {
                    is BleEvent.ShowMessage -> {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(event.message)
                        }
                    }
                    is BleEvent.RequestBluetoothEnable -> {
                        // MainActivity에서 처리
                    }
                    is BleEvent.RequestPermissions -> {
                        // MainActivity에서 처리
                    }
                }
            }
        }
        
        // UI 레이아웃
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "BLE 테스트 앱",
                            fontWeight = FontWeight.Bold,
                            color = AppColors.TextPrimary
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = AppColors.ActionButtonBackground
                    )
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = AppColors.Background
        ) { paddingValues ->
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                color = AppColors.Background
            ) {
                MainContent(
                    viewModel = viewModel,
                    uiState = uiState,
                    scannedDevices = scannedDevices,
                    chatMessages = chatMessages
                )
            }
        }
    }
}

/**
 * 메인 콘텐츠 컴포넌트.
 * BLE 상태, 기기 목록, GATT 채팅 섹션을 포함합니다.
 */
@Composable
private fun MainContent(
    viewModel: BleViewModel,
    uiState: com.example.ble_kotlin.ViewModel.BleUiState,
    scannedDevices: List<com.example.ble_kotlin.Utils.ScannedDevice>,
    chatMessages: List<com.example.ble_kotlin.Utils.ChatMessage>
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // BLE 상태 섹션
        BleStatusSection(
            uiState = uiState,
            onScanToggle = { viewModel.toggleScan() },
            onAdvertiseToggle = { viewModel.toggleAdvertising() },
            onServerToggle = { viewModel.toggleGattServer() },
            onDisconnect = { viewModel.disconnectGatt() }
        )
        
        // 기기 목록 섹션 (스캔 중이거나 BLE가 준비된 경우에만 표시)
        if (uiState.isScanning || uiState.bleStatus == BleStatus.READY) {
            DeviceListSection(
                devices = scannedDevices,
                onDeviceClick = { device -> viewModel.connectToDevice(device) },
                isScanning = uiState.isScanning
            )
        }
        
        // 채팅 섹션
        GattChatSection(
            uiState = uiState,
            messages = chatMessages,
            onSendMessage = { message -> viewModel.sendMessage(message) },
            onDisconnect = { viewModel.disconnectGatt() }
        )
    }
} 