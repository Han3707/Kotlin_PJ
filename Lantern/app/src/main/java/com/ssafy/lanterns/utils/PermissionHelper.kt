package com.ssafy.lanterns.utils

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * BLE 관련 권한 검사 및 요청 헬퍼 클래스
 */
class PermissionHelper(private val activity: Activity) {

    private val TAG = "PermissionHelper"
    
    // 필요한 Android 12 이상 블루투스 권한
    private val permissions = arrayOf(
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.BLUETOOTH_ADVERTISE,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )
    
    // 오래된 Android 버전용 권한
    private val oldPermissions = arrayOf(
        Manifest.permission.BLUETOOTH,
        Manifest.permission.BLUETOOTH_ADMIN,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    // 기기의 블루투스 어댑터 가져오기
    fun getBluetoothAdapter(): BluetoothAdapter? {
        val bluetoothManager = activity.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return bluetoothManager?.adapter
    }
    
    // 블루투스 활성화 여부 확인
    fun isBluetoothEnabeld(): Boolean {
        val bluetoothAdapter = getBluetoothAdapter()
        val isEnabled = bluetoothAdapter?.isEnabled == true
        Log.d(TAG, "블루투스 상태: ${if (isEnabled) "활성화됨" else "비활성화됨"}")
        return isEnabled
    }
    
    // 블루투스 활성화 요청 다이얼로그 표시
    fun requestEnableBluetooth(requestCode: Int){
        val bluetoothAdapter = getBluetoothAdapter() ?: return
        
        if(!bluetoothAdapter.isEnabled){
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            activity.startActivityForResult(enableBtIntent, requestCode)
            Log.d(TAG, "블루투스 활성화 요청")
        }
    }
    
    // 필요한 모든 권한을 가지고 있는지 확인
    fun hasPermission(): Boolean {
        val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions
        } else {
            oldPermissions
        }
        
        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
        }
        
        val hasAll = missingPermissions.isEmpty()
        
        if (hasAll) {
            Log.d(TAG, "모든 블루투스 권한 보유 확인")
        } else {
            Log.d(TAG, "누락된 권한: ${missingPermissions.joinToString()}")
        }
        
        return hasAll
    }
    
    // 필요한 모든 권한 요청
    fun requestPermissions(requestCode: Int) {
        val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions
        } else {
            oldPermissions
        }
        
        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                activity,
                missingPermissions.toTypedArray(),
                requestCode
            )
            Log.d(TAG, "권한 요청: ${missingPermissions.joinToString()}")
        }
    }
}