package com.ssafy.lanterns.ui.screens.ondevice

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.lanterns.service.audio.wakeword.WakeWordService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

enum class AiState {
    IDLE, LISTENING, COMMAND_RECOGNIZED, PROCESSING, SPEAKING, ERROR
}

data class OnDeviceAIState(
    val currentAiState: AiState = AiState.IDLE,
    val listeningMessage: String = "듣는 중",
    val commandRecognizedMessage: String = "명령 인식 완료",
    val processingMessage: String = "처리 중",
    val responseMessage: String = "",
    val errorMessage: String = "오류 발생"
)

private const val TAG = "OnDeviceAIViewModel"

private const val WAKE_WORD_AUTO_CLOSE_DELAY_MILLIS = 7000L
private const val INTERACTION_AUTO_CLOSE_DELAY_MILLIS = 10000L
private const val SPEAKING_STATE_AUTO_CLOSE_DELAY_MILLIS = 5000L
private const val ERROR_STATE_AUTO_CLOSE_DELAY_MILLIS = 4000L

// PorcupineController 인터페이스 (현재 코드에서는 직접 사용되지 않음)
/*
interface PorcupineController {
    fun pausePorcupine()
    fun resumePorcupine()
}
*/

@HiltViewModel
class OnDeviceAIViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : AndroidViewModel(context as Application) {

    private val _uiState = MutableStateFlow(OnDeviceAIState())
    val uiState: StateFlow<OnDeviceAIState> = _uiState.asStateFlow()

    private var autoCloseJob: Job? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var speechRecognitionRetryCount = 0
    private val MAX_SPEECH_RECOGNITION_RETRIES = 3

    // var porcupineController: PorcupineController? = null // 현재 코드에서는 직접 사용되지 않음

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            Log.d(TAG, "onReadyForSpeech - 음성 입력 준비 완료")
            _uiState.value = _uiState.value.copy(
                listeningMessage = "말씀해주세요",
                currentAiState = AiState.LISTENING // 상태 확실히 LISTENING으로 유지
            )
            resetAutoCloseTimer(INTERACTION_AUTO_CLOSE_DELAY_MILLIS)
        }

        override fun onBeginningOfSpeech() {
            Log.d(TAG, "onBeginningOfSpeech")
            _uiState.value = _uiState.value.copy(listeningMessage = "듣고 있어요...")
            resetAutoCloseTimer(INTERACTION_AUTO_CLOSE_DELAY_MILLIS)
        }

        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {
            Log.d(TAG, "onEndOfSpeech")
        }

        override fun onError(error: Int) {
            val errorMessageText = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "오디오 에러가 발생했습니다"
                SpeechRecognizer.ERROR_CLIENT -> "클라이언트 에러가 발생했습니다"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "권한이 없습니다"
                SpeechRecognizer.ERROR_NETWORK -> "네트워크 에러가 발생했습니다"
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "네트워크 타임아웃이 발생했습니다"
                SpeechRecognizer.ERROR_NO_MATCH -> "음성을 인식하지 못했습니다. 다시 말씀해주세요"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "음성 인식기가 사용 중입니다"
                SpeechRecognizer.ERROR_SERVER -> "서버 에러가 발생했습니다"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "입력 시간이 초과되었습니다"
                else -> "알 수 없는 에러가 발생했습니다"
            }
            Log.e(TAG, "onError: $errorMessageText (code: $error)")

            val shouldRetry = (error == SpeechRecognizer.ERROR_NO_MATCH ||
                    error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ||
                    error == SpeechRecognizer.ERROR_AUDIO ||
                    error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) && // RECOGNIZER_BUSY도 재시도 고려
                    speechRecognitionRetryCount < MAX_SPEECH_RECOGNITION_RETRIES

            if (shouldRetry) {
                speechRecognitionRetryCount++
                _uiState.value = _uiState.value.copy(
                    currentAiState = AiState.ERROR, // UI에는 오류 상태 표시
                    errorMessage = "$errorMessageText (재시도 ${speechRecognitionRetryCount}/${MAX_SPEECH_RECOGNITION_RETRIES})"
                )
                Log.d(TAG, "음성 인식 재시도 ($speechRecognitionRetryCount/$MAX_SPEECH_RECOGNITION_RETRIES)")
                viewModelScope.launch {
                    delay(1500) // 잠시 대기
                    if (_uiState.value.currentAiState != AiState.IDLE) { // IDLE로 전환되지 않았다면 재시도
                        startSpeechRecognition()
                    }
                }
                resetAutoCloseTimer(INTERACTION_AUTO_CLOSE_DELAY_MILLIS) // 재시도 시에도 타이머 리셋
            } else {
                // 재시도 불가 또는 횟수 초과 시 화면 닫기
                Log.w(TAG, "음성 인식 재시도 중단 또는 불가. 오류 코드: $error, 재시도 횟수: $speechRecognitionRetryCount")
                showErrorAndPrepareToClose(errorMessageText) // 이 함수가 IDLE로 전환하고 화면 닫음
            }
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            Log.d(TAG, "onResults: $matches")
            speechRecognitionRetryCount = 0 // 성공 시 재시도 횟수 초기화
            if (!matches.isNullOrEmpty()) {
                val recognizedText = matches[0]
                if (recognizedText.isNotEmpty()) {
                    processVoiceInput(recognizedText)
                } else {
                    showErrorAndPrepareToClose("인식된 내용이 없어요")
                }
            } else {
                showErrorAndPrepareToClose("결과를 받지 못했어요")
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun initSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            if (speechRecognizer == null) {
                try {
                    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
                    speechRecognizer?.setRecognitionListener(recognitionListener)
                } catch (e: Exception) {
                    Log.e(TAG, "SpeechRecognizer 생성 실패: ${e.message}", e)
                    showErrorAndPrepareToClose("음성 인식기 초기화 실패")
                }
            }
        } else {
            Log.e(TAG, "음성 인식 기능을 지원하지 않는 기기입니다")
            showErrorAndPrepareToClose("음성 인식 기능을 지원하지 않아요")
        }
    }

    private fun startSpeechRecognition() {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            showErrorAndPrepareToClose("마이크 권한이 필요합니다")
            return
        }

        if (speechRecognizer == null) {
            initSpeechRecognizer()
            if (speechRecognizer == null) return // 초기화 실패 시 종료
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1000)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
        }

        try {
            speechRecognizer?.startListening(intent)
            Log.d(TAG, "SpeechRecognizer.startListening() 호출됨")
            // activateAI에서 이미 LISTENING 상태 및 타이머 설정됨.
            // 여기서 resetAutoCloseTimer를 호출하면 onReadyForSpeech의 타이머와 중복/충돌 가능.
            // _uiState.value = _uiState.value.copy(currentAiState = AiState.LISTENING) // 이미 activateAI에서 설정
            // resetAutoCloseTimer(INTERACTION_AUTO_CLOSE_DELAY_MILLIS) // onReadyForSpeech에서 처리
        } catch (e: Exception) {
            Log.e(TAG, "음성 인식 시작 실패: ${e.message}", e)
            showErrorAndPrepareToClose("음성 인식을 시작할 수 없습니다")
        }
    }

    private fun stopSpeechRecognition() {
        try {
            speechRecognizer?.stopListening()
            Log.d(TAG, "SpeechRecognizer.stopListening() 호출됨")
        } catch (e: Exception) {
            Log.e(TAG, "음성 인식 중지 실패: ${e.message}", e)
        }
    }

    fun activateAI() {
        speechRecognitionRetryCount = 0
        Log.d(TAG, "activateAI() 시작. Porcupine 일시 중지 요청.")
        context.startService(Intent(context, WakeWordService::class.java).apply {
            action = WakeWordService.ACTION_PAUSE_PORCUPINE
        })

        _uiState.value = _uiState.value.copy(
            currentAiState = AiState.LISTENING,
            listeningMessage = "듣는 중..." // 초기 메시지 설정
        )
        startAutoCloseTimer(WAKE_WORD_AUTO_CLOSE_DELAY_MILLIS) // 웨이크워드로 켜졌을 때의 기본 타이머
        startSpeechRecognition()
        Log.d(TAG, "activateAI() 완료. 현재 상태: ${uiState.value.currentAiState}")
    }

    private fun startAutoCloseTimer(delayMillis: Long, forceIdle: Boolean = false) {
        Log.d(TAG, "startAutoCloseTimer 호출됨. 지연 시간: ${delayMillis}ms, 강제IDLE: $forceIdle, 현재 AI 상태: ${_uiState.value.currentAiState}")
        autoCloseJob?.cancel()
        autoCloseJob = viewModelScope.launch {
            Log.d(TAG, "AutoCloseJob: ${delayMillis}ms 타이머 시작.")
            delay(delayMillis)
            Log.d(TAG, "AutoCloseJob: ${delayMillis}ms 타이머 만료. 현재 AI 상태: ${_uiState.value.currentAiState}.")
            if (_uiState.value.currentAiState != AiState.IDLE || forceIdle) {
                Log.d(TAG, "AutoCloseJob: AI 상태를 IDLE로 변경합니다.")
                deactivateAI(fromTimer = true)
            } else {
                Log.d(TAG, "AutoCloseJob: AI 상태가 이미 IDLE이거나 강제 IDLE 조건이 아님. 추가 작업 없음.")
            }
        }
    }

    fun resetAutoCloseTimer(delayMillis: Long = INTERACTION_AUTO_CLOSE_DELAY_MILLIS) {
        if (_uiState.value.currentAiState != AiState.IDLE) {
            Log.d(TAG, "resetAutoCloseTimer 호출됨. 지연 시간: ${delayMillis}ms")
            startAutoCloseTimer(delayMillis, false) // forceIdle을 명시적으로 false로 전달
        }
    }

    fun deactivateAI(fromTimer: Boolean = false) {
        Log.d(TAG, "deactivateAI() 호출됨. fromTimer: $fromTimer, Porcupine 재개 요청.")
        context.startService(Intent(context, WakeWordService::class.java).apply {
            action = WakeWordService.ACTION_RESUME_PORCUPINE
        })

        autoCloseJob?.cancel()
        autoCloseJob = null
        stopSpeechRecognition()
        speechRecognizer?.destroy() // 명시적으로 destroy 호출
        speechRecognizer = null     // 참조 제거하여 다음번에 새로 생성하도록 함
        Log.d(TAG, "SpeechRecognizer destroy 및 null 처리 완료.")

        _uiState.value = OnDeviceAIState()
    }

    fun processVoiceInput(text: String) {
        Log.d(TAG, "processVoiceInput: \"$text\"")
        resetAutoCloseTimer(INTERACTION_AUTO_CLOSE_DELAY_MILLIS)
        stopSpeechRecognition()

        _uiState.value = _uiState.value.copy(
            currentAiState = AiState.COMMAND_RECOGNIZED,
            commandRecognizedMessage = "\"$text\""
        )

        viewModelScope.launch {
            delay(1500)
            _uiState.value = _uiState.value.copy(currentAiState = AiState.PROCESSING, processingMessage = "생각 중...")
            delay(2000)
            val response = when {
                text.contains("안녕") -> "안녕하세요! 무엇을 도와드릴까요?"
                text.contains("이름") -> "저는 랜턴 AI입니다. 반갑습니다."
                text.contains("날씨") -> "오늘은 맑은 하늘이 예상됩니다."
                text.contains("시간") -> "현재 시간은 ${java.text.SimpleDateFormat("HH:mm", Locale.getDefault()).format(java.util.Date())}입니다."
                text.contains("도움") -> "음성 기반 AI 서비스입니다. 간단한 질문에 답변해드릴 수 있어요."
                else -> "\"$text\"라고 말씀하셨군요. 어떻게 도와드릴까요?"
            }
            showResponse(response)
        }
    }

    private fun showResponse(response: String) {
        Log.d(TAG, "showResponse: \"$response\"")
        _uiState.value = _uiState.value.copy(
            currentAiState = AiState.SPEAKING,
            responseMessage = response
        )
        startAutoCloseTimer(SPEAKING_STATE_AUTO_CLOSE_DELAY_MILLIS, true)
    }

    fun showErrorAndPrepareToClose(message: String) { // public으로 변경됨
        Log.e(TAG, "showErrorAndPrepareToClose: $message")
        _uiState.value = _uiState.value.copy(
            currentAiState = AiState.ERROR,
            errorMessage = message
        )
        startAutoCloseTimer(ERROR_STATE_AUTO_CLOSE_DELAY_MILLIS, true)
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "onCleared() 호출됨. Porcupine 재개 및 리소스 해제.")
        context.startService(Intent(context, WakeWordService::class.java).apply {
            action = WakeWordService.ACTION_RESUME_PORCUPINE
        })
        autoCloseJob?.cancel()
        autoCloseJob = null
        speechRecognizer?.destroy()
        speechRecognizer = null
    }
}