package com.example.bletest.utils

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.result.ActivityResultLauncher

object BluetoothUtils {
    /**
     * 블루투스가 활성화되어 있는지 확인
     * 
     * @param context 블루투스 매니저를 가져올 컨텍스트
     * @return 블루투스가 활성화된 경우 true, 아닌 경우 false 또는 지원하지 않는 경우 null
     */
    fun isBluetoothEnabled(context: Context): Boolean? {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val bluetoothAdapter = bluetoothManager?.adapter
        
        return bluetoothAdapter?.isEnabled
    }
    
    /**
     * 블루투스 어댑터 가져오기
     * 
     * @param context 블루투스 매니저를 가져올 컨텍스트
     * @return 블루투스 어댑터 또는 지원하지 않는 경우 null
     */
    fun getBluetoothAdapter(context: Context): BluetoothAdapter? {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return bluetoothManager?.adapter
    }
    
    /**
     * 블루투스 활성화 요청 인텐트 생성
     * 
     * @return 블루투스 활성화 요청 인텐트
     */
    fun createEnableBluetoothIntent(): Intent {
        return Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
    }
    
    /**
     * 블루투스 활성화 요청
     * 
     * @param activityResultLauncher 활성화 요청을 위한 액티비티 결과 런처
     */
    fun requestBluetoothEnable(activityResultLauncher: ActivityResultLauncher<Intent>) {
        val enableBtIntent = createEnableBluetoothIntent()
        activityResultLauncher.launch(enableBtIntent)
    }
    
    /**
     * 기기가 BLE를 지원하는지 확인
     * 
     * @param context 패키지 관리자를 가져올 컨텍스트
     * @return BLE를 지원하는 경우 true, 아닌 경우 false
     */
    fun isBleSupported(context: Context): Boolean {
        return context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_BLUETOOTH_LE)
    }
} 