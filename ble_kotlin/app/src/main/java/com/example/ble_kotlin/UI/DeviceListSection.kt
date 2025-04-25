package com.example.ble_kotlin.UI

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.DeviceUnknown
import androidx.compose.material.icons.filled.SignalCellular4Bar
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ble_kotlin.Utils.DistanceEstimator
import com.example.ble_kotlin.Utils.ScannedDevice
import java.text.DecimalFormat

/**
 * 스캔된 BLE 기기 목록 섹션 컴포저블
 *
 * @param devices 스캔된 기기 목록
 * @param onDeviceClick 기기 클릭 콜백
 * @param isScanning 스캔 중 여부
 */
@Composable
fun DeviceListSection(
    devices: List<ScannedDevice>,
    onDeviceClick: (ScannedDevice) -> Unit,
    isScanning: Boolean
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
            // 제목 및 스캔 상태
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "기기 목록",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.TextPrimary
                )
                
                Text(
                    text = if (isScanning) "스캔 중..." else "스캔 중지됨",
                    color = if (isScanning) AppColors.GreenStatus else AppColors.MidGray,
                    fontSize = 14.sp
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 기기 목록 또는 안내 메시지
            if (devices.isEmpty()) {
                Text(
                    text = if (isScanning) "기기를 찾는 중입니다..." else "발견된 기기가 없습니다",
                    color = AppColors.TextSecondary,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                ) {
                    items(devices) { device ->
                        DeviceListItem(
                            device = device,
                            onConnectClick = { onDeviceClick(device) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * 기기 목록 항목 컴포저블
 *
 * @param device 스캔된 기기 정보
 * @param onConnectClick 연결 버튼 클릭 콜백
 */
@Composable
private fun DeviceListItem(
    device: ScannedDevice,
    onConnectClick: () -> Unit
) {
    val distanceMeters = DistanceEstimator.calculateDistance(device.rssi)
    val displayName = device.deviceName ?: "알 수 없는 기기"
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.White)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 기기 정보
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = displayName,
                    fontWeight = FontWeight.SemiBold,
                    color = AppColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Text(
                    text = device.deviceAddress,
                    color = AppColors.TextSecondary,
                    fontSize = 12.sp
                )
                
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "RSSI: ${device.rssi}dBm",
                        color = AppColors.TextSecondary,
                        fontSize = 12.sp
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Text(
                        text = "거리: ${String.format("%.2f", distanceMeters)}m",
                        color = AppColors.TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
            
            // 연결 버튼
            Button(
                onClick = onConnectClick,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppColors.LanternAccent,
                    contentColor = AppColors.TextPrimary
                ),
                modifier = Modifier.height(36.dp)
            ) {
                Text(text = "연결")
            }
        }
    }
}

/**
 * 미리보기
 */
@Preview(showBackground = true)
@Composable
private fun DeviceListSectionPreview() {
    val mockDevices = listOf(
        ScannedDevice(
            deviceName = "Test Device 1",
            deviceAddress = "AA:BB:CC:DD:EE:FF",
            rssi = -65,
            scanRecord = byteArrayOf()
        ),
        ScannedDevice(
            deviceName = "Test Device with a Very Long Name That Should Be Truncated",
            deviceAddress = "11:22:33:44:55:66",
            rssi = -80,
            scanRecord = byteArrayOf()
        ),
        ScannedDevice(
            deviceName = null,
            deviceAddress = "99:88:77:66:55:44",
            rssi = -90,
            scanRecord = byteArrayOf()
        )
    )
    
    DeviceListSection(
        devices = mockDevices,
        onDeviceClick = {},
        isScanning = true
    )
} 