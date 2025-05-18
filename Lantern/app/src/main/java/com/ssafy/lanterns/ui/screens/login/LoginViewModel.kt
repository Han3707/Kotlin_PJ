package com.ssafy.lanterns.ui.screens.login // 패키지 경로는 실제 프로젝트 구조에 맞게 조정하세요

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import com.ssafy.lanterns.R
import com.ssafy.lanterns.data.model.User
import com.ssafy.lanterns.data.repository.AuthRepository
import com.ssafy.lanterns.data.repository.AuthResult
import com.ssafy.lanterns.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

private const val TAG = "LoginViewModel"

// 로그인 상태를 나타내는 Sealed Interface
sealed interface LoginUiState {
    object Idle : LoginUiState
    object Loading : LoginUiState
    data class Success(val user: User) : LoginUiState
    data class Error(val message: String) : LoginUiState
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    @ApplicationContext private val applicationContext: Context,
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    // Video Toggle State - START
    private val _isVideoASelected = MutableStateFlow(true) // true면 main_video3, false면 main_video4
    val isVideoASelected: StateFlow<Boolean> = _isVideoASelected.asStateFlow()

    val currentVideoUri: StateFlow<Uri> = isVideoASelected.map { isA ->
        val videoResId = if (isA) R.raw.main_video3 else R.raw.main_video4
        Uri.parse("android.resource://${applicationContext.packageName}/$videoResId")
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Lazily, // 필요할 때 스트림 시작
        initialValue = Uri.parse("android.resource://${applicationContext.packageName}/${R.raw.main_video3}") // 초기값
    )

    fun toggleVideo() {
        _isVideoASelected.value = !_isVideoASelected.value
    }
    // Video Toggle State - END

    // 초기화 시 로그인 상태 확인 (앱 시작 시 자동 로그인)
    init {
        checkLoginStatus()
    }

    // 로그인 상태 확인
    private fun checkLoginStatus() {
        viewModelScope.launch {
            if (authRepository.isLoggedIn()) {
                val userId = authRepository.getCurrentUserId() ?: return@launch
                // 로컬 DB에서 전체 User 객체를 가져와서 selectedProfileImageNumber도 포함하도록 수정
                val user = userRepository.getUserById(userId) ?: run {
                    // 만약 로컬에 없다면 (매우 드문 경우), 최소한의 정보로 생성하거나 로그아웃 처리
                    Log.w(TAG, "자동 로그인 시도 중 로컬 DB에서 userId: $userId 사용자 정보를 찾을 수 없습니다.")
                    // 필요하다면 여기서 로그아웃 처리 또는 기본 사용자 생성 로직 추가
                    // 우선은 AuthRepository에서 가져온 정보로 최소 User 객체 생성
                    User(userId = userId, nickname = authRepository.getCurrentUserNickname() ?: "Unknown", deviceId = "")
                }
                
                _uiState.update { LoginUiState.Success(user) }
                Log.d(TAG, "자동 로그인 성공: ${user.nickname}, 이미지: ${user.selectedProfileImageNumber}")
            }
        }
    }

    // 서버 클라이언트 ID
    private val serverClientId: String by lazy {
        applicationContext.getString(R.string.server_client_id)
    }

    // GoogleSignInClient 인스턴스 (지연 초기화)
    private val googleSignInClient: GoogleSignInClient by lazy {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(serverClientId)
            .requestEmail()
            .requestProfile()
            .build()
        
        GoogleSignIn.getClient(applicationContext, gso)
    }

    /**
     * Google 로그인 인텐트를 반환합니다.
     */
    fun getSignInIntent(): Intent {
        // 항상 새로운 구글 로그인 세션을 시작하도록 기존 세션 로그아웃
        googleSignInClient.signOut().addOnFailureListener { e ->
            Log.e(TAG, "Google 로그아웃 실패 (getSignInIntent 시)", e)
        }
        
        return googleSignInClient.signInIntent
    }

    /**
     * ActivityResult 로부터 받은 로그인 결과를 처리합니다.
     */
    fun handleSignInResult(data: Intent?) {
        _uiState.update { LoginUiState.Loading }
        
        if (data == null) {
            Log.e(TAG, "로그인 결과 Intent가 null입니다")
            _uiState.update { LoginUiState.Error("로그인 처리 중 오류가 발생했습니다.") }
            return
        }
        
        val task: Task<GoogleSignInAccount> = GoogleSignIn.getSignedInAccountFromIntent(data)
        
        try {
            val account = task.getResult(ApiException::class.java)
            Log.i(TAG, "구글 로그인 성공: ${account.email}, 이름=${account.displayName}")
            
            // ID 토큰 가져오기
            val idToken = account.idToken
            if (idToken != null) {
                // ID 토큰으로 백엔드에 인증 시도
                googleLoginWithIdToken(idToken, account)
            } else {
                Log.e(TAG, "ID 토큰이 null입니다")
                _uiState.update { LoginUiState.Error("구글 로그인 정보를 가져오지 못했습니다.") }
            }
        } catch (e: ApiException) {
            // 로그인 실패 처리
            val statusCode = e.statusCode
            val statusMessage = e.status?.statusMessage ?: "null"
            
            Log.e(TAG, "Google Sign-In failed: statusCode=$statusCode, statusMessage='$statusMessage'")
            Log.e(TAG, "Google SignIn API 예외 상세: code=$statusCode, message=${e.message}, status=${e.status}", e)
            
            val errorMessage = when (e.statusCode) {
                10 -> "앱이 Google에 등록되지 않았거나 설정 오류입니다 (DEVELOPER_ERROR). statusMessage='$statusMessage'"
                16 -> "앱에 대한 적절한 인증 설정이 없습니다 (INTERNAL_ERROR). statusMessage='$statusMessage'"
                7 -> "네트워크 오류가 발생했습니다 (NETWORK_ERROR)"
                12501 -> "로그인이 취소되었습니다 (SIGN_IN_CANCELLED)"
                12500 -> "Google Play 서비스 업데이트가 필요합니다 (SIGN_IN_FAILED)"
                else -> "구글 로그인 중 오류가 발생했습니다. 코드: $statusCode, 메시지: '$statusMessage'"
            }
            
            _uiState.update { LoginUiState.Error(errorMessage) }
        } catch (e: Exception) {
            Log.e(TAG, "로그인 처리 중 예외 발생", e)
            _uiState.update { LoginUiState.Error("로그인 처리 중 오류 발생: ${e.message}") }
        }
    }

    /**
     * ID 토큰으로 백엔드 로그인을 시도합니다.
     */
    private fun googleLoginWithIdToken(idToken: String, account: GoogleSignInAccount) {
        viewModelScope.launch {
            _uiState.update { LoginUiState.Loading } // 명시적 로딩 상태 업데이트
            try {
                // ID 토큰으로 백엔드 인증 시도
                val result = authRepository.googleLogin(idToken)
                
                when (result) {
                    is AuthResult.Success -> {
                        // 성공: 사용자 정보 저장
                        val backendUser = result.data // 백엔드로부터 받은 User 객체
                        var userToSave = backendUser

                        // 1. 로컬 DB에서 기존 사용자 정보 조회
                        val localUser = userRepository.getUserById(backendUser.userId)

                        if (localUser != null) {
                            // 2. 로컬 DB에 정보가 있다면, 로컬 값을 우선으로 User 객체 업데이트
                            Log.d(TAG, "로컬 DB 사용자 정보 발견: ${localUser.nickname}, 이미지: ${localUser.selectedProfileImageNumber}")
                            Log.d(TAG, "백엔드 수신 정보: ${backendUser.nickname}, 이미지 (기본값 가정): ${backendUser.selectedProfileImageNumber}")

                            userToSave = backendUser.copy(
                                nickname = localUser.nickname, // 로컬 DB의 닉네임 사용
                                selectedProfileImageNumber = localUser.selectedProfileImageNumber // 로컬 DB의 이미지 번호 사용
                            )
                            Log.d(TAG, "로컬 우선 적용된 정보: ${userToSave.nickname}, 이미지: ${userToSave.selectedProfileImageNumber}")
                        } else {
                            // 3. 로컬 DB에 정보가 없다면 (최초 로그인 등), 백엔드 정보를 그대로 사용하되,
                            // selectedProfileImageNumber는 User 모델의 기본값(1)으로 설정되도록 명시.
                            // (백엔드 응답에 이 필드가 없다면 User 생성 시 기본값이 사용될 것이나, 명확성을 위해)
                            userToSave = backendUser.copy(
                                // nickname은 백엔드에서 온 값을 그대로 사용 (최초이므로)
                                selectedProfileImageNumber = 1 // 최초 로그인이므로 기본 이미지 번호 1로 설정
                            )
                            Log.d(TAG, "로컬 DB 사용자 정보 없음. 백엔드 정보 기반으로 생성: ${userToSave.nickname}, 이미지: ${userToSave.selectedProfileImageNumber}")
                        }
                        
                        userRepository.saveUser(userToSave) // 최종 사용자 정보 저장
                        Log.i(TAG, "백엔드 인증 및 로컬 DB 저장 성공: ${userToSave.nickname}, 이미지: ${userToSave.selectedProfileImageNumber}")
                        _uiState.update { LoginUiState.Success(userToSave) }
                    }
                    is AuthResult.Error -> {
                        // 실패: 오류 메시지 처리
                        Log.w(TAG, "백엔드 인증 실패: ${result.message}")
                        
                        _uiState.update { LoginUiState.Error("백엔드 인증 실패: ${result.message}") }
                        // 백엔드 인증 실패 시 Google 로그아웃
                        googleSignInClient.signOut().addOnFailureListener { e ->
                            Log.e(TAG, "Google 로그아웃 실패 (백엔드 인증 실패 시)", e)
                        }
                    }
                    is AuthResult.Loading -> {
                        // 이미 로딩 상태이므로 추가 작업 불필요
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "백엔드 인증 중 예외 발생", e)
                _uiState.update { LoginUiState.Error("백엔드 인증 중 오류 발생: ${e.message}") }
                googleSignInClient.signOut().addOnFailureListener { ex ->
                    Log.e(TAG, "Google 로그아웃 실패 (백엔드 인증 예외 시)", ex)
                }
            }
        }
    }

    /**
     * 로그아웃 처리
     */
    fun signOut() {
        viewModelScope.launch {
            _uiState.update { LoginUiState.Loading }
            
            try {
                // 로컬 DB 데이터 초기화
                userRepository.clearAllLocalData()
                
                // Google 로그아웃
                googleSignInClient.signOut().addOnCompleteListener {
                    Log.d(TAG, "Google 로그아웃 완료")
                }
                
                // 백엔드 로그아웃 및 로컬 데이터 삭제
                val result = authRepository.signOut() // authRepository에서 로컬 user 정보도 삭제해야 함
                if (result is AuthResult.Success) {
                    _uiState.update { LoginUiState.Idle }
                    Log.i(TAG, "로그아웃 성공 (ViewModel)")
                } else if (result is AuthResult.Error) {
                    _uiState.update { LoginUiState.Error("로그아웃 중 오류 발생: ${result.message}") }
                     Log.w(TAG, "로그아웃 실패 (ViewModel): ${result.message}")
                }
            } catch (e: Exception) {
                _uiState.update { LoginUiState.Error("로그아웃 중 오류 발생: ${e.message}") }
                Log.e(TAG, "로그아웃 중 예외 발생 (ViewModel)", e)
            }
        }
    }

    /**
     * UI 상태를 Idle로 초기화하는 함수
     */
    fun resetStateToIdle() {
         _uiState.update { LoginUiState.Idle }
    }
} 