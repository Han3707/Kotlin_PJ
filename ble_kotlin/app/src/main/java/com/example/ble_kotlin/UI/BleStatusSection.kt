package com.example.ble_kotlin.UI

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ble_kotlin.BleManager.BleStatus
import com.example.ble_kotlin.ViewModel.BleUiState
import com.example.ble_kotlin.ViewModel.ConnectionState

/**
 * BLE 상태 및 제어 섹션 컴포저블
 *
 * @param uiState BLE UI 상태
 * @param onScanToggle 스캔 토글 콜백
 * @param onAdvertiseToggle 광고 토글 콜백
 * @param onServerToggle 서버 토글 콜백
 * @param onDisconnect 연결 해제 콜백
 */
@Composable
fun BleStatusSection(
    uiState: BleUiState,
    onScanToggle: () -> Unit,
    onAdvertiseToggle: () -> Unit,
    onServerToggle: () -> Unit,
    onDisconnect: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.BackgroundSecondary)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // 제목
            Text(
                text = "BLE 상태",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = AppColors.TextPrimary
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 상태 표시
            StatusIndicator(
                title = "BLE",
                isActive = uiState.bleStatus == BleStatus.READY,
                activeText = "활성화됨 ✓",
                inactiveText = when (uiState.bleStatus) {
                    BleStatus.DISABLED -> "비활성화됨 ✗"
                    BleStatus.NOT_AVAILABLE -> "지원되지 않음 ✗"
                    else -> "알 수 없음 ?"
                }
            )
            
            StatusIndicator(
                title = "권한",
                isActive = uiState.permissionGranted,
                activeText = "허용됨 ✓",
                inactiveText = "거부됨 ✗"
            )
            
            StatusIndicator(
                title = "스캔",
                isActive = uiState.isScanning,
                activeText = "진행 중... ◉",
                inactiveText = "중지됨 ◎"
            )
            
            StatusIndicator(
                title = "광고",
                isActive = uiState.isAdvertising,
                activeText = "진행 중... ◉",
                inactiveText = "중지됨 ◎"
            )
            
            // GATT 서버 상태 표시
            StatusIndicator(
                title = "GATT 서버",
                isActive = uiState.isServerRunning,
                activeText = "실행 중... ◉",
                inactiveText = "중지됨 ◎"
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 컨트롤 버튼
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // 스캔 버튼
                val isReadyForScan = uiState.bleStatus == BleStatus.READY && uiState.permissionGranted
                
                BleActionButton(
                    text = if (uiState.isScanning) "스캔 중지" else "스캔 시작",
                    onClick = onScanToggle,
                    enabled = isReadyForScan || uiState.isScanning
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                // 광고 버튼
                val isReadyForAdvertise = uiState.bleStatus == BleStatus.READY && uiState.permissionGranted
                
                BleActionButton(
                    text = if (uiState.isAdvertising) "광고 중지" else "광고 시작",
                    onClick = onAdvertiseToggle,
                    enabled = isReadyForAdvertise || uiState.isAdvertising
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 서버 및 연결 해제 버튼
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // GATT 서버 버튼
                BleActionButton(
                    text = if (uiState.isServerRunning) "서버 중지" else "서버 시작",
                    onClick = onServerToggle,
                    enabled = uiState.bleStatus == BleStatus.READY && uiState.permissionGranted
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                // 연결 해제 버튼 (연결된 경우에만 활성화)
                BleActionButton(
                    text = "연결 해제",
                    onClick = onDisconnect,
                    enabled = uiState.connectionState == ConnectionState.READY || 
                              uiState.connectionState == ConnectionState.CONNECTED
                )
            }
        }
    }
}

/**
 * 상태 표시기 컴포저블
 *
 * @param title 상태 제목
 * @param isActive 활성화 여부
 * @param activeText 활성화 시 표시할 텍스트
 * @param inactiveText 비활성화 시 표시할 텍스트
 */
@Composable
private fun StatusIndicator(
    title: String,
    isActive: Boolean,
    activeText: String,
    inactiveText: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = "$title: ",
            color = AppColors.TextPrimary,
            fontWeight = FontWeight.SemiBold
        )
        
        Text(
            text = if (isActive) activeText else inactiveText,
            color = if (isActive) AppColors.GreenStatus else AppColors.RedStatus
        )
    }
}

/**
 * BLE 액션 버튼 컴포저블
 *
 * @param text 버튼 텍스트
 * @param onClick 클릭 이벤트 핸들러
 * @param enabled 활성화 여부
 */
@Composable
private fun BleActionButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = AppColors.LanternAccent,
            contentColor = AppColors.TextPrimary,
            disabledContainerColor = AppColors.MidGray,
            disabledContentColor = AppColors.White
        ),
        modifier = Modifier.height(40.dp)
    ) {
        Text(text = text)
    }
}

/**
 * 미리보기
 */
@Preview(showBackground = true)
@Composable
private fun BleStatusSectionPreview() {
    BleStatusSection(
        uiState = BleUiState(
            bleStatus = BleStatus.READY,
            permissionGranted = true,
            isScanning = true,
            isAdvertising = false
        ),
        onScanToggle = {},
        onAdvertiseToggle = {},
        onServerToggle = {},
        onDisconnect = {}
    )
} 