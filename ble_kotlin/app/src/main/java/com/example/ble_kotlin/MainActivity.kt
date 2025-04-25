package com.example.ble_kotlin

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import com.example.ble_kotlin.BleManager.BleServiceManager
import com.example.ble_kotlin.UI.AppScreen
import com.example.ble_kotlin.ViewModel.BleEvent
import com.example.ble_kotlin.ViewModel.BleViewModel
import kotlinx.coroutines.flow.collectLatest

/**
 * 앱의 메인 액티비티.
 * 권한 요청 및 블루투스 활성화 요청을 처리합니다.
 */
class MainActivity : ComponentActivity() {
    
    private val viewModel: BleViewModel by viewModels()
    private val serviceManager = BleServiceManager(application)
    
    private lateinit var bluetoothEnableLauncher: ActivityResultLauncher<Intent>
    private lateinit var permissionsLauncher: ActivityResultLauncher<Array<String>>
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setupActivityResultLaunchers()
        
        setContent {
            // LaunchedEffect를 사용해 ViewModel 이벤트를 처리
            LaunchedEffect(Unit) {
                viewModel.events.collectLatest { event ->
                    when (event) {
                        is BleEvent.RequestBluetoothEnable -> {
                            bluetoothEnableLauncher.launch(serviceManager.getEnableBluetoothIntent())
                        }
                        is BleEvent.RequestPermissions -> {
                            permissionsLauncher.launch(event.permissions)
                        }
                        else -> {} // 다른 이벤트는 AppScreen에서 처리
                    }
                }
            }
            
            // 메인 앱 화면 표시
            AppScreen()
        }
        
        // 초기 BLE 상태 및 권한 체크
        viewModel.checkBleSupportAndPermissions(this)
    }
    
    private fun setupActivityResultLaunchers() {
        // Bluetooth 활성화 요청 결과 처리
        bluetoothEnableLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            viewModel.handleBluetoothEnableResult(result.resultCode == RESULT_OK)
        }
        
        // 권한 요청 결과 처리
        permissionsLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val allGranted = permissions.entries.all { it.value }
            if (allGranted) {
                viewModel.handlePermissionResult(true)
            } else {
                viewModel.handlePermissionResult(false)
                // 일부 권한 거부 시 안내 메시지
                Toast.makeText(
                    this,
                    "이 앱은 BLE 기능을 사용하기 위해 모든 권한이 필요합니다.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
} 