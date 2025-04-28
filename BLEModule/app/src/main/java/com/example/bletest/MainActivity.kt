package com.example.bletest

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.bletest.ui.theme.BleMeshTheme
import com.example.bletest.ui.view.BleMeshNavigation
import com.example.bletest.ui.viewmodel.BleViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * BLE 메시 통신 앱의 메인 화면
 * 
 * Compose UI를 사용하며, 블루투스 권한 및 상태 관리는 기존 코드를 유지합니다.
 */
@AndroidEntryPoint
@SuppressLint("MissingPermission")
class MainActivity : AppCompatActivity() {
    // ViewModel 주입
    private val viewModel: BleViewModel by viewModels()
    
    // 로그 태그
    private val TAG = "MainActivity"
    
    // 블루투스 관련 객체
    private lateinit var bluetoothManager: android.bluetooth.BluetoothManager
    private var bluetoothAdapter: BluetoothAdapter? = null

    // --- ActivityResultLauncher (권한 및 블루투스 활성화 결과 처리) ---
    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            if (permissions.all { it.value }) {
                log("모든 권한이 허용되었습니다.")
                checkBluetoothAvailability()
            } else {
                log("필수 권한 중 일부 또는 전체가 거부되었습니다.")
                Toast.makeText(this, "앱 실행을 위해 권한이 필요합니다.", Toast.LENGTH_LONG).show()
            }
        }

    private val requestEnableBluetoothLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                log("블루투스가 활성화되었습니다.")
                checkPermissionsAndRequestIfNeeded()
            } else {
                log("블루투스 활성화가 거부되었습니다.")
                Toast.makeText(this, "메시 서비스를 사용하려면 블루투스를 켜야 합니다.", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 블루투스 매니저 및 어댑터 초기화
        try {
            log("블루투스 매니저 및 어댑터 초기화 시작")
            bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager
            bluetoothAdapter = bluetoothManager.adapter
            
            if (bluetoothAdapter == null) {
                log("블루투스 어댑터를 가져올 수 없음 (null)")
            } else {
                log("블루투스 매니저 및 어댑터 초기화 성공")
            }
        } catch (e: Exception) {
            log("블루투스 매니저 및 어댑터 초기화 실패: ${e.message}")
        }
        
        // Compose UI 설정
        setContent {
            BleMeshTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BleMeshNavigation()
                }
            }
        }
        
        // 블루투스 상태 및 권한 확인
        checkBluetoothAvailability()
    }
    
    private fun checkBluetoothAvailability() {
        // 이미 초기화된 bluetoothAdapter 사용
        if (bluetoothAdapter == null) {
            // 만약 아직 초기화되지 않았다면 다시 한번 시도
            try {
                bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager
                bluetoothAdapter = bluetoothManager.adapter
            } catch (e: Exception) {
                log("블루투스 어댑터 초기화 실패: ${e.message}")
            }
            
            // 여전히 null이면 오류 처리
            if (bluetoothAdapter == null) {
                log("블루투스 어댑터를 가져올 수 없음 (null): API=${Build.VERSION.SDK_INT}")
                Toast.makeText(this, "블루투스를 지원하지 않는 기기입니다.", Toast.LENGTH_LONG).show()
                return
            }
        }
        
        // 블루투스 활성화 확인
        val adapter = bluetoothAdapter ?: return
        log("블루투스 어댑터 확인됨. 활성화 상태: ${adapter.isEnabled}")
        
        if (!adapter.isEnabled) {
            log("블루투스 활성화 요청...")
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            requestEnableBluetoothLauncher.launch(enableBtIntent)
        } else {
            log("블루투스가 이미 활성화되어 있습니다.")
            checkPermissionsAndRequestIfNeeded()
        }
    }

    private fun checkPermissionsAndRequestIfNeeded() {
        val requiredPermissions = mutableListOf<String>()

        // Android 12 (S, API 31) 이상 권한
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!hasPermission(Manifest.permission.BLUETOOTH_SCAN)) 
                requiredPermissions.add(Manifest.permission.BLUETOOTH_SCAN)
            if (!hasPermission(Manifest.permission.BLUETOOTH_ADVERTISE)) 
                requiredPermissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) 
                requiredPermissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            // API 30 이하에서는 위치 권한이 필요
            if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
                requiredPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            if (!hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)) {
                requiredPermissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
        }

        if (requiredPermissions.isNotEmpty()) {
            log("필수 권한 요청: ${requiredPermissions.joinToString()}")
            requestPermissionsLauncher.launch(requiredPermissions.toTypedArray())
        } else {
            log("모든 필수 권한이 이미 허용되었습니다.")
            
            // 블루투스 어댑터가 존재하는지 다시 확인
            if (bluetoothAdapter == null) {
                log("이 기기는 블루투스를 지원하지 않습니다.")
                Toast.makeText(this, "이 기기는 블루투스를 지원하지 않습니다.", Toast.LENGTH_LONG).show()
                return
            }
            
            // 모든 권한 확인 완료
            log("블루투스 및 권한 확인 완료")
        }
    }

    // 특정 권한 확인
    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    // 블루투스 관련 필수 권한 확인
    private fun hasBluetoothPermissions(): Boolean {
        // 블루투스 어댑터가 없으면 권한이 있어도 사용 불가
        if (bluetoothAdapter == null) {
            log("블루투스 어댑터가 없어 권한 체크가 의미 없음")
            return false
        }
        
        // API 레벨에 따라 필요한 권한 확인
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            hasPermission(Manifest.permission.BLUETOOTH_SCAN) &&
            hasPermission(Manifest.permission.BLUETOOTH_ADVERTISE) &&
            hasPermission(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            // API 30 이하에서는 위치 권한이 필요
            hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    // 로그 출력
    private fun log(message: String) {
        Log.d(TAG, message)
        // ViewModel에도 로그 전달
        lifecycleScope.launch {
            viewModel.addLog(message)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        if (viewModel.isMeshRunning()) {
            viewModel.stopMesh()
        }
    }
}