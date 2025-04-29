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
import com.example.bletest.service.BleService
import com.example.bletest.service.BleServiceConnection
import com.example.bletest.ui.theme.BleMeshTheme
import com.example.bletest.ui.view.BleMeshNavigation
import com.example.bletest.ui.viewmodel.BleViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import androidx.lifecycle.Lifecycle

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
                // 권한이 모두 허용된 경우 BLE 서비스 시작
                startBleService()
            } else {
                // 권한이 거부된 경우 - 더 이상 권한 요청 루프가 발생하지 않도록 처리
                log("필수 권한 중 일부 또는 전체가 거부되었습니다.")
                
                // 거부된 권한 목록 로깅
                permissions.forEach { (permission, granted) ->
                    if (!granted) {
                        log("거부된 권한: $permission")
                    }
                }
                
                // 사용자에게 필요한 권한이 왜 필요한지 설명하는 메시지 표시
                Toast.makeText(
                    this, 
                    "블루투스 통신을 위해 필요한 권한이 거부되었습니다. 앱 설정에서 권한을 허용해주세요.", 
                    Toast.LENGTH_LONG
                ).show()
                
                // 권한 다시 요청하지 않고 UI는 계속 표시
                // 이렇게 함으로써 사용자는 앱을 계속 사용할 수 있음
            }
        }

    private val requestEnableBluetoothLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                log("블루투스가 활성화되었습니다.")
                // 블루투스가 활성화된 후 권한 확인
                if (hasBluetoothPermissions()) {
                    // 모든 권한이 있으면 서비스 시작
                    startBleService()
                } else {
                    // 권한이 없으면 권한 요청
                    checkPermissionsAndRequestIfNeeded()
                }
            } else {
                log("블루투스 활성화가 거부되었습니다.")
                Toast.makeText(this, "메시 서비스를 사용하려면 블루투스를 켜야 합니다.", Toast.LENGTH_LONG).show()
                // 블루투스 비활성화 상태에서도 앱은 계속 실행
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 블루투스 매니저 및 어댑터 초기화
        initializeBluetoothAdapter()
        
        // 권한 확인 및 요청 - 이 부분을 onCreate에서 즉시 실행
        if (!checkAndRequestPermissions()) {
            // 권한이 없는 경우에는 여기서 권한 요청이 시작됩니다.
            // requestPermissionsLauncher에서 결과를 처리합니다.
            log("필수 권한 요청이 필요합니다.")
        } else {
            // 이미 모든 권한이 있을 경우
            log("블루투스 및 권한 확인 완료")
            startBleService()
        }
        
        // Compose UI 설정
        setContent {
            BleMeshTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    BleMeshNavigation()
                }
            }
        }
        
        // 서비스 상태 관찰
        observeServiceState()
    }
    
    private fun initializeBluetoothAdapter() {
        try {
            log("블루투스 매니저 및 어댑터 초기화 시작")
            bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager
            bluetoothAdapter = bluetoothManager.adapter
            
            if (bluetoothAdapter == null) {
                log("블루투스 어댑터를 가져올 수 없음 (null)")
                Toast.makeText(this, "블루투스를 지원하지 않는 기기입니다. 일부 기능이 제한됩니다.", Toast.LENGTH_LONG).show()
            } else {
                log("블루투스 매니저 및 어댑터 초기화 성공")
                // 블루투스 상태 및 권한 확인 - 초기화 성공했을 때만 호출
                checkBluetoothAvailability()
            }
        } catch (e: Exception) {
            log("블루투스 매니저 및 어댑터 초기화 실패: ${e.message}")
            Toast.makeText(this, "블루투스 초기화에 실패했습니다. 일부 기능이 제한됩니다.", Toast.LENGTH_LONG).show()
        }
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
                Toast.makeText(this, "블루투스를 지원하지 않는 기기입니다. 일부 기능이 제한됩니다.", Toast.LENGTH_LONG).show()
                return
            }
        }
        
        // 블루투스 활성화 확인
        val adapter = bluetoothAdapter ?: return
        log("블루투스 어댑터 확인됨. 활성화 상태: ${adapter.isEnabled}")
        
        if (!adapter.isEnabled) {
            log("블루투스 활성화 요청...")
            // 블루투스 활성화 요청 - 사용자가 거부할 수 있음
            try {
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                requestEnableBluetoothLauncher.launch(enableBtIntent)
            } catch (e: Exception) {
                log("블루투스 활성화 요청 실패: ${e.message}")
                Toast.makeText(this, "블루투스 활성화 요청에 실패했습니다.", Toast.LENGTH_SHORT).show()
            }
        } else {
            log("블루투스가 이미 활성화되어 있습니다.")
            // 권한 확인 및 요청
            if (hasBluetoothPermissions()) {
                log("블루투스 및 권한 확인 완료")
                startBleService() // 모든 권한이 있으면 서비스 시작
            } else {
                checkPermissionsAndRequestIfNeeded()
            }
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
            try {
                requestPermissionsLauncher.launch(requiredPermissions.toTypedArray())
            } catch (e: Exception) {
                log("권한 요청 실패: ${e.message}")
                Toast.makeText(this, "권한 요청에 실패했습니다. 일부 기능이 제한됩니다.", Toast.LENGTH_SHORT).show()
            }
        } else {
            log("모든 필수 권한이 이미 허용되었습니다.")
            log("블루투스 및 권한 확인 완료")
            
            // 모든 권한이 있을 때 서비스 시작
            startBleService()
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
    
    /**
     * BLE 서비스 시작
     * 권한 확인이 완료되고 블루투스가 활성화된 후 호출됩니다.
     */
    private fun startBleService() {
        log("BLE 서비스 시작")
        
        // 서비스 연결이 이미 시작되었는지 확인
        val service = BleServiceConnection.getService()
        if (service != null) {
            log("BLE 서비스가 이미 연결되어 있습니다.")
            viewModel.onServiceConnected()
            return
        }
        
        // 서비스 시작 (포그라운드 서비스)
        try {
            val serviceIntent = Intent(this, BleService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            
            // 서비스에 바인딩
            val bound = BleServiceConnection.bindService(this)
            if (bound) {
                log("서비스 바인딩 시도")
            } else {
                log("서비스 바인딩 실패")
                Toast.makeText(this, "BLE 서비스를 시작할 수 없습니다.", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            log("BLE 서비스 시작 실패: ${e.message}")
            Toast.makeText(this, "BLE 서비스 시작에 실패했습니다. 앱을 재시작해보세요.", Toast.LENGTH_LONG).show()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // 메시 중지
        if (viewModel.isMeshRunning()) {
            viewModel.stopPublicChatRoom()
        }
        
        // 서비스 바인딩 해제
        try {
            BleServiceConnection.unbindService(this)
        } catch (e: Exception) {
            log("서비스 바인딩 해제 중 오류: ${e.message}")
        }
    }

    /**
     * 서비스 상태 관찰 함수
     * 서비스의 연결 상태를 관찰하고 뷰모델에 전달합니다.
     */
    private fun observeServiceState() {
        lifecycleScope.launch {
            // 서비스 연결 상태 관찰
            BleServiceConnection.serviceFlow.collect { service ->
                if (service != null) {
                    log("BLE 서비스에 연결되었습니다.")
                    // 뷰모델에 서비스 연결 상태 알림
                    viewModel.onServiceConnected()
                } else {
                    log("BLE 서비스와의 연결이 끊겼습니다.")
                    // 뷰모델에 서비스 연결 해제 상태 알림
                    viewModel.onServiceDisconnected()
                }
            }
        }
    }

    // checkAndRequestPermissions 함수 추가
    private fun checkAndRequestPermissions(): Boolean {
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

        // 필요한 권한이 있는지 확인
        return if (requiredPermissions.isEmpty()) {
            // 모든 권한이 이미 있는 경우
            true
        } else {
            log("권한 요청 필요: ${requiredPermissions.joinToString()}")
            // 권한 요청
            requestPermissionsLauncher.launch(requiredPermissions.toTypedArray())
            false
        }
    }
}