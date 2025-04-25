package com.example.ble_kotlin.BleManager

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flow

/**
 * BLE 사용에 필요한 권한 관리 클래스.
 * Activity Result API를 사용하여 Android 버전에 따라 필요한 권한을 처리합니다.
 *
 * @property registry ActivityResultRegistry
 * @property lifecycleOwner LifecycleOwner
 */
class BlePermissionsManager(
    private val registry: ActivityResultRegistry,
    private val lifecycleOwner: LifecycleOwner
) : DefaultLifecycleObserver {

    private var permissionsLauncher: ActivityResultLauncher<Array<String>>? = null
    
    // 권한 부여 결과를 통지하기 위한 Flow
    private val _permissionsGranted = MutableSharedFlow<Boolean>(replay = 1)
    val permissionsGranted: SharedFlow<Boolean> = _permissionsGranted.asSharedFlow()
    
    init {
        lifecycleOwner.lifecycle.addObserver(this)
    }
    
    override fun onCreate(owner: LifecycleOwner) {
        permissionsLauncher = registry.register(
            "ble_permissions",
            owner,
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val allGranted = permissions.entries.all { it.value }
            _permissionsGranted.tryEmit(allGranted)
        }
    }
    
    /**
     * 필요한 BLE 권한들을 요청합니다.
     * Android 12 이상에서는 BLUETOOTH_SCAN, BLUETOOTH_ADVERTISE, BLUETOOTH_CONNECT 권한이 추가로 필요합니다.
     */
    fun requestBlePermissions() {
        permissionsLauncher?.launch(getRequiredPermissions())
    }

    /**
     * Android 버전에 따라 필요한 권한 배열 반환
     * 
     * @return 필요한 권한 배열
     */
    fun getRequiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12 이상
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10 이상
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            // Android 9 이하
            arrayOf(
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        }
    }
    
    /**
     * 필요한 모든 권한이 부여되었는지 확인
     * 
     * @param context 애플리케이션 컨텍스트
     * @return 모든 권한 부여 여부
     */
    fun areAllPermissionsGranted(context: Context): Boolean {
        return getRequiredPermissions().all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    /**
     * 각 권한의 부여 상태를 Flow로 반환
     * 
     * @param context 애플리케이션 컨텍스트
     * @return 권한 상태 Flow (권한명, 부여 여부)
     */
    fun checkPermissionStates(context: Context): Flow<Map<String, Boolean>> = flow {
        val permissionStates = getRequiredPermissions().associateWith { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
        emit(permissionStates)
    }
    
    /**
     * 부여되지 않은 권한 목록 반환
     * 
     * @param context 애플리케이션 컨텍스트
     * @return 부여되지 않은 권한 배열
     */
    fun getNotGrantedPermissions(context: Context): Array<String> {
        return getRequiredPermissions().filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()
    }
    
    override fun onDestroy(owner: LifecycleOwner) {
        permissionsLauncher = null
    }
} 