package com.ssafy.lanterns.ui.view.main

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.ssafy.lanterns.service.WakeWordService
import com.ssafy.lanterns.ui.screens.App           // App.kt의 루트 Composable
import com.ssafy.lanterns.ui.screens.main.MainViewModel
import com.ssafy.lanterns.ui.screens.mypage.MyPageViewModel
import com.ssafy.lanterns.ui.theme.LanternsTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /* -------------------------------------------------- *
     * ViewModels
     * -------------------------------------------------- */
    private val mainViewModel: MainViewModel by viewModels()

    /* -------------------------------------------------- *
     * 권한 요청 런처
     * -------------------------------------------------- */
    // 다른 앱 위에 그리기 권한
    private lateinit var requestOverlayPermissionLauncher: ActivityResultLauncher<Intent>

    // 일반 권한(RECORD_AUDIO, POST_NOTIFICATIONS)
    private val requestMultiplePermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            var allRequiredGranted = true

            // RECORD_AUDIO
            permissions[Manifest.permission.RECORD_AUDIO]?.let { granted ->
                if (granted) {
                    Log.d("MainActivity", "RECORD_AUDIO 권한 허용")
                } else {
                    Log.w("MainActivity", "RECORD_AUDIO 권한 거부")
                    Toast.makeText(this,
                        "마이크 권한이 거부되어 웨이크워드 기능을 사용할 수 없습니다.",
                        Toast.LENGTH_LONG
                    ).show()
                    allRequiredGranted = false
                }
            }

            // POST_NOTIFICATIONS (Android 13+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissions[Manifest.permission.POST_NOTIFICATIONS]?.let { granted ->
                    if (!granted) {
                        Log.w("MainActivity", "POST_NOTIFICATIONS 권한 거부")
                    }
                }
            }

            if (allRequiredGranted) checkAndRequestOverlayPermission()
        }

    /* -------------------------------------------------- *
     * 웨이크워드 브로드캐스트 수신
     * -------------------------------------------------- */
    private val wakeWordReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == WakeWordService.ACTION_ACTIVATE_AI) {
                Log.d("MainActivity", "ACTION_ACTIVATE_AI 수신 → activateAI()")
                mainViewModel.activateAI()
            }
        }
    }

    /* ==================================================
     *  Activity Lifecycle
     * ================================================== */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        /* --- Overlay 권한 런처 초기화 --- */
        requestOverlayPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                if (Settings.canDrawOverlays(this)) {
                    Log.d("MainActivity", "Overlay 권한 허용(설정 후)")
                    startWakeWordService()
                } else {
                    Log.w("MainActivity", "Overlay 권한 거부(설정 후)")
                    Toast.makeText(
                        this,
                        "AI 화면 표시 권한이 거부되었습니다.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

        /* --- Compose UI --- */
        setContent {
            // (예시) MyPageViewModel에서 사용자 테마 취향 조회
            val userViewModel: MyPageViewModel = hiltViewModel()
            val userState by userViewModel.uiState.collectAsState()

            LanternsTheme(darkTheme = true) {      // 다크 테마 강제 적용
                Surface(modifier = Modifier.fillMaxSize()) {
                    App()       // App.kt의 루트 Composable
                }
            }
        }

        /* --- 권한 체크 --- */
        checkAndRequestPermissions()

        /* --- 웨이크워드 브로드캐스트 등록 --- */
        LocalBroadcastManager.getInstance(this).registerReceiver(
            wakeWordReceiver,
            IntentFilter(WakeWordService.ACTION_ACTIVATE_AI)
        )
        Log.d("MainActivity", "wakeWordReceiver 등록")
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(wakeWordReceiver)
        Log.d("MainActivity", "wakeWordReceiver 해제")
    }

    /* ==================================================
     *  권한 / Overlay 처리
     * ================================================== */
    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissions.add(Manifest.permission.RECORD_AUDIO)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (permissions.isNotEmpty()) {
            Log.d("MainActivity", "요청할 권한: $permissions")
            requestMultiplePermissionsLauncher.launch(permissions.toTypedArray())
        } else {
            Log.d("MainActivity", "일반 권한 이미 허용됨")
            checkAndRequestOverlayPermission()
        }
    }

    private fun checkAndRequestOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            Log.d("MainActivity", "Overlay 권한 없음 → 설정 화면 이동")
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            Toast.makeText(
                this,
                "AI 화면을 표시하려면 '다른 앱 위에 표시' 권한이 필요합니다.",
                Toast.LENGTH_LONG
            ).show()
            requestOverlayPermissionLauncher.launch(intent)
        } else {
            Log.d("MainActivity", "Overlay 권한 허용됨")
            startWakeWordService()
        }
    }

    private fun startWakeWordService() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED &&
            Settings.canDrawOverlays(this)
        ) {
            Log.d("MainActivity", "필수 권한 OK → WakeWordService 시작")
            val intent = Intent(this, WakeWordService::class.java)
            ContextCompat.startForegroundService(this, intent)
        } else {
            Log.w("MainActivity", "WakeWordService 시작 불가: 권한 부족")
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED
            ) {
                Toast.makeText(this, "마이크 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
            }
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "'다른 앱 위에 표시' 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
