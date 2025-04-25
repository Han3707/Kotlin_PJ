package com.example.ble_kotlin.BleManager

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.os.Build
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * BLE 서비스 가용성 및 상태를 관리하는 클래스.
 * 기기의 Bluetooth 지원 여부 확인 및 활성화 관련 기능을 제공합니다.
 * 
 * @property context 애플리케이션 컨텍스트
 */
class BleServiceManager(private val context: Context) {
    
    private val bluetoothManager: BluetoothManager? by lazy {
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    }
    
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        bluetoothManager?.adapter
    }
    
    /**
     * 기기가 BLE를 지원하는지 확인합니다.
     * 
     * @return BLE 지원 여부
     */
    fun isBleSupported(): Boolean {
        // Bluetooth 매니저가 초기화 가능하고 어댑터가 제공되면 BLE가 지원됨
        return bluetoothManager != null && bluetoothAdapter != null
    }
    
    /**
     * Bluetooth 어댑터가 활성화되어 있는지 확인합니다.
     * 
     * @return Bluetooth 활성화 여부
     */
    fun isBleEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }
    
    /**
     * Bluetooth 어댑터 초기화 및 사용 가능 여부 확인
     * 
     * @return 어댑터 사용 가능 여부
     */
    fun initAdapter(): Boolean {
        return bluetoothAdapter != null
    }
    
    /**
     * Bluetooth 활성화 요청 상태를 Flow로 반환
     * 
     * @return Bluetooth 활성화 상태 Flow
     */
    fun enableBluetooth(): Flow<Boolean> = flow {
        val isEnabled = bluetoothAdapter?.isEnabled ?: false
        emit(isEnabled)
    }
    
    /**
     * Bluetooth가 비활성화된 경우 사용자에게 활성화를 요청합니다.
     * Activity Result API 사용을 위해 ActivityResultLauncher를 사용하는 것이 권장됩니다.
     * 
     * @param activity Bluetooth 활성화 요청을 시작할 Activity
     * @param requestCode 결과 콜백을 식별하기 위한 요청 코드
     */
    fun enableBle(activity: Activity, requestCode: Int) {
        if (!isBleEnabled()) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            activity.startActivityForResult(enableBtIntent, requestCode)
        }
    }
    
    /**
     * 필요한 Bluetooth 권한 확인
     * 
     * @return 권한 상태 Flow
     */
    fun checkPermissions(): Flow<PermissionState> = flow {
        // 초기 상태 전달
        emit(PermissionState.CHECKING)
        
        // API 31+ (Android 12)에서 필요한 새로운 권한들
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // BLUETOOTH_SCAN, BLUETOOTH_ADVERTISE, BLUETOOTH_CONNECT 권한 필요
            emit(PermissionState.BLUETOOTH_PERMISSIONS_REQUIRED)
        } 
        // 이전 버전에서는 위치 권한 필요
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // FINE_LOCATION 권한 필요
            emit(PermissionState.LOCATION_PERMISSION_REQUIRED)
        }
        // 모든 권한이 있거나 낮은 API 레벨
        else {
            emit(PermissionState.GRANTED)
        }
    }
    
    /**
     * Bluetooth 활성화 인텐트 생성
     * 
     * @return Bluetooth 활성화 요청 인텐트
     */
    fun getEnableBluetoothIntent(): Intent {
        return Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
    }
}

/**
 * 권한 상태를 나타내는 Sealed 클래스
 */
sealed class PermissionState {
    object CHECKING : PermissionState()
    object GRANTED : PermissionState()
    object DENIED : PermissionState()
    object BLUETOOTH_PERMISSIONS_REQUIRED : PermissionState()
    object LOCATION_PERMISSION_REQUIRED : PermissionState()
} 