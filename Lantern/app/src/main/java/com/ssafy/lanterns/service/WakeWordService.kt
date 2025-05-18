package com.ssafy.lanterns.service

import ai.picovoice.porcupine.PorcupineException
import ai.picovoice.porcupine.PorcupineManager
import ai.picovoice.porcupine.PorcupineManagerCallback
import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.ssafy.lanterns.R
// import com.ssafy.lanterns.ui.view.main.MainActivity // 서비스에서 직접 Activity 참조는 좋지 않음
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
// import android.os.Binder // 현재 코드에서는 Binder를 사용하지 않으므로 제거해도 무방

class WakeWordService : Service() {

    private var porcupineManager: PorcupineManager? = null

    private val ACCESS_KEY = "yDavJ4DBaKni68lXNor2vRSSwSwPgAjh8wvgMTJ62e7SkLdoOT2eOA==" // 실제 키는 안전하게 관리하세요.

    private val KEYWORD_ASSET_FILENAME = "hey-lantern_en_android_v3_0_0.ppn"
    private val MODEL_ASSET_FILENAME = "porcupine_params.pv"

    private val INTERNAL_KEYWORD_FILENAME = "wakeword_keyword.ppn"
    private val INTERNAL_MODEL_FILENAME = "wakeword_model.pv"

    private var isPorcupinePaused = false

    // wakeWordCallback은 Porcupine이 일시 중지 상태일 때도 호출될 수 있으므로,
    // 콜백 내부에서 isPorcupinePaused 상태를 확인해야 합니다.
    private val wakeWordCallback = PorcupineManagerCallback { keywordIndex ->
        if (isPorcupinePaused) {
            Log.d("WakeWordService_Detection", "Porcupine 일시 중지 중, 웨이크워드 감지 무시됨.")
            return@PorcupineManagerCallback // 일시 중지 상태이면 아무 작업도 하지 않음
        }
        if (keywordIndex == 0) { // "hey-lantern" 키워드가 첫 번째(인덱스 0)로 가정
            Log.i("WakeWordService_Detection", "★★★ '헤이 랜턴' 웨이크워드 감지됨! (keywordIndex: $keywordIndex) ★★★")

            val intent = Intent(ACTION_ACTIVATE_AI)
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
            Log.d("WakeWordService", "ACTION_ACTIVATE_AI 로컬 브로드캐스트 전송됨.")
        }
    }

    private fun copyAssetToInternalStorage(context: Context, assetFileName: String, internalFileName: String): String? {
        val assetManager = context.assets
        val outputFile = File(context.filesDir, internalFileName)

        try {
            BufferedInputStream(assetManager.open(assetFileName)).use { inputStream ->
                BufferedOutputStream(FileOutputStream(outputFile)).use { outputStream ->
                    val buffer = ByteArray(1024)
                    var read: Int
                    while (inputStream.read(buffer).also { read = it } != -1) {
                        outputStream.write(buffer, 0, read)
                    }
                }
            }
            Log.i("WakeWordService", "성공: '$assetFileName' -> '${outputFile.absolutePath}' 복사 완료")
            return outputFile.absolutePath
        } catch (e: IOException) {
            Log.e("WakeWordService", "'$assetFileName' 파일을 '$internalFileName'(으)로 복사 실패", e)
            // e.printStackTrace() // 프로덕션에서는 제거하거나 조건부 로깅
            return null
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("WakeWordService", "onCreate() 호출됨")
        // Log.d("WakeWordService", "Access Key (앞 5자리): ${ACCESS_KEY.take(5)}...") // 실제 키 로깅은 보안상 주의

        startForegroundServiceNotification() // 포그라운드 서비스 시작

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.e("WakeWordService", "RECORD_AUDIO 권한 없음. 서비스 중단.")
            stopSelf() // 권한 없으면 서비스 즉시 중단
            return
        }
        Log.d("WakeWordService", "RECORD_AUDIO 권한 확인됨.")

        initializePorcupine()
    }

    private fun initializePorcupine() {
        try {
            Log.d("WakeWordService", "모델 파일 복사 시도: $MODEL_ASSET_FILENAME -> $INTERNAL_MODEL_FILENAME")
            val modelPath = copyAssetToInternalStorage(applicationContext, MODEL_ASSET_FILENAME, INTERNAL_MODEL_FILENAME)

            Log.d("WakeWordService", "키워드 파일 복사 시도: $KEYWORD_ASSET_FILENAME -> $INTERNAL_KEYWORD_FILENAME")
            val keywordPath = copyAssetToInternalStorage(applicationContext, KEYWORD_ASSET_FILENAME, INTERNAL_KEYWORD_FILENAME)

            if (modelPath == null || keywordPath == null) {
                Log.e("WakeWordService", "모델 또는 키워드 파일 내부 저장소로 복사 실패. 서비스 중단.")
                stopSelf()
                return
            }

            Log.i("WakeWordService", "내부 저장소 모델 파일 경로: $modelPath")
            Log.i("WakeWordService", "내부 저장소 키워드 파일 경로: $keywordPath")

            porcupineManager = PorcupineManager.Builder()
                .setAccessKey(ACCESS_KEY)
                .setModelPath(modelPath) // 내부 저장소 경로 사용
                .setKeywordPath(keywordPath) // 내부 저장소 경로 사용
                .setSensitivity(0.7f)
                .build(applicationContext, wakeWordCallback)

            Log.i("WakeWordService", "PorcupineManager 초기화 성공 (내부 저장소 파일 사용).")
        } catch (e: PorcupineException) {
            Log.e("WakeWordService", "PorcupineManager 초기화 중 예외 발생: ${e.message}", e)
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("WakeWordService", "onStartCommand() 호출됨. Action: ${intent?.action}, Flags: $flags, StartId: $startId")

        // 외부에서 Porcupine 제어
        when (intent?.action) {
            ACTION_PAUSE_PORCUPINE -> {
                pausePorcupine()
                return START_NOT_STICKY // 일시 중지 요청 시에는 서비스를 계속 실행할 필요 없음
            }
            ACTION_RESUME_PORCUPINE -> {
                resumePorcupine()
                return START_STICKY // 재개 요청 시에는 서비스 계속 실행
            }
            else -> { // 일반적인 시작 또는 null action
                // 서비스가 이미 실행 중이고 Porcupine이 초기화되었다면, 여기서 다시 start()를 호출할 필요 없음
                // onCreate에서 이미 초기화 및 start 시도.
                // 만약 서비스가 어떤 이유로든 중지되었다가 재시작되는 경우(START_STICKY 반환 시),
                // porcupineManager가 null일 수 있으므로 재초기화 또는 시작 로직 필요할 수 있음.
                // 현재는 onCreate에서 초기화하고, 명시적인 시작은 여기서 한 번만 하도록 함.
                if (porcupineManager == null && ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    // onCreate에서 권한 문제로 초기화 실패 후, 권한이 부여되어 서비스가 재시작된 경우 등
                    initializePorcupine() // 다시 초기화 시도
                }
                startPorcupineListening() // Porcupine 감지 시작/재시작
            }
        }
        return START_STICKY // 서비스가 비정상 종료되어도 시스템이 재시작
    }

    private fun startPorcupineListening() {
        if (isPorcupinePaused) {
            Log.d("WakeWordService", "Porcupine이 일시 중지 상태이므로 시작하지 않음.")
            return
        }
        if (porcupineManager == null) {
            Log.e("WakeWordService", "startPorcupineListening: PorcupineManager가 null입니다. (초기화 실패 가능성)")
            // 필요하다면 여기서 stopSelf() 호출
            return
        }
        try {
            porcupineManager?.start()
            Log.i("WakeWordService", "Porcupine 음성 감지 시작됨.")
        } catch (e: PorcupineException) {
            Log.e("WakeWordService", "Porcupine 시작 중 예외 발생: ${e.message}", e)
            // 이 경우 서비스 자체를 중단할지, 아니면 재시도 로직을 둘지 결정 필요
        }
    }

    fun pausePorcupine() {
        if (!isPorcupinePaused && porcupineManager != null) {
            try {
                porcupineManager?.stop()
                isPorcupinePaused = true
                Log.i("WakeWordService", "Porcupine 음성 감지 일시 중지됨.")
            } catch (e: PorcupineException) {
                Log.e("WakeWordService", "Porcupine 중지 중 예외 발생 (일시 중지 시): ${e.message}", e)
            }
        }
    }

    fun resumePorcupine() {
        if (isPorcupinePaused) { // 일시 중지 상태일 때만 재개 로직 실행
            isPorcupinePaused = false
            Log.i("WakeWordService", "Porcupine 음성 감지 재개 시도.")
            if (porcupineManager == null) {
                Log.w("WakeWordService", "resumePorcupine: porcupineManager가 null입니다. 재초기화 시도.")
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    initializePorcupine() // Porcupine 재초기화
                } else {
                    Log.e("WakeWordService", "resumePorcupine: RECORD_AUDIO 권한 없어 초기화 불가.")
                    // 이 경우, 웨이크워드 감지가 다시 동작하지 않을 수 있음.
                    // 사용자에게 알림을 주거나, MainActivity에서 권한 상태를 다시 확인하도록 유도 필요.
                    return // 초기화 실패 시 더 이상 진행하지 않음
                }
            }
            startPorcupineListening() // 다시 감지 시작
        } else {
            Log.d("WakeWordService", "resumePorcupine: Porcupine이 이미 실행 중이거나 재개할 필요 없음 (isPorcupinePaused=false).")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            porcupineManager?.stop()
            porcupineManager?.delete()
        } catch (e: PorcupineException) {
            Log.e("WakeWordService", "Porcupine 리소스 해제 중 예외 발생: ${e.message}", e)
        }
        porcupineManager = null
        Log.d("WakeWordService", "onDestroy() 호출됨. Porcupine 리소스 해제 완료.")
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null // 바인드 서비스로 사용하지 않음
    }

    private fun startForegroundServiceNotification() {
        val CHANNEL_ID = "WakeWordServiceChannel"
        val NOTIFICATION_ID = 1

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "웨이크워드 감지 서비스",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "앱이 '헤이 랜턴' 웨이크워드를 감지하고 있습니다."
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }

        val notificationIcon = R.drawable.lantern_logo // 실제 아이콘 리소스 확인 필요

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("'헤이 랜턴' 웨이크워드 감지 중...")
            .setSmallIcon(notificationIcon)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        try {
            startForeground(NOTIFICATION_ID, notification)
            Log.d("WakeWordService", "Foreground 서비스 알림 표시됨.")
        } catch (e: Exception) {
            Log.e("WakeWordService", "startForeground 예외 발생: ${e.message}", e)
            // Android 12 (S) 이상에서는 포그라운드 서비스 시작 제한이 있을 수 있음 (앱이 백그라운드 상태일 때 등)
        }
    }

    // 하나의 companion object로 통합
    companion object {
        const val ACTION_ACTIVATE_AI = "com.ssafy.lanterns.service.ACTION_ACTIVATE_AI"
        const val ACTION_PAUSE_PORCUPINE = "com.ssafy.lanterns.service.ACTION_PAUSE_PORCUPINE"
        const val ACTION_RESUME_PORCUPINE = "com.ssafy.lanterns.service.ACTION_RESUME_PORCUPINE"
    }
}