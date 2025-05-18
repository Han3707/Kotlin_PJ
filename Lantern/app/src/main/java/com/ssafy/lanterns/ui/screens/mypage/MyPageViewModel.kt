package com.ssafy.lanterns.ui.screens.mypage

import androidx.annotation.DrawableRes // Drawable 리소스 ID 사용
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.lanterns.R // 리소스 ID 접근
import com.ssafy.lanterns.data.model.User
import com.ssafy.lanterns.data.repository.AuthRepository // AuthRepository 임포트
import com.ssafy.lanterns.data.repository.AuthResult // AuthResult 임포트 추가
import com.ssafy.lanterns.data.repository.UserRepository
import com.ssafy.lanterns.data.source.token.TokenManager // TokenManager 추가
// import com.ssafy.lanterns.ui.util.ImageUtils // 전체 임포트 제거
import com.ssafy.lanterns.ui.util.getAllProfileImageResources // 개별 함수 임포트
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.util.Log

private const val TAG = "MyPageViewModel"

data class MyPageUiState(
    val isLoading: Boolean = false,
    val isEditing: Boolean = false,
    val user: User? = null,
    val errorMessage: String? = null,
    // 입력 상태
    val nicknameInput: String = "",
    val statusMessageInput: String = "", // 상태 메시지 입력 필드 추가
    // 사용자 이메일
    val email: String = "",
    // 선택된 프로필 이미지의 "번호" (1-15), 기본값 1
    val selectedProfileImageNumber: Int = 1, // User 객체의 값을 따라가거나, 기본값 1
    // 프로필 이미지 선택 다이얼로그에 표시할 이미지 정보 Map<Int, Int> : 번호 to 리소스ID
    val availableProfileImageResources: Map<Int, Int> = getAllProfileImageResources(), // 직접 함수 호출
    // 기본 테마값 (랜턴 노랑) - UI에서 직접 사용하지는 않지만, 다른 로직에 필요할 수 있어 유지
    val currentTheme: Int = 1, 
    // val isDarkMode: Boolean = true, // 다크 모드 관련 필드 제거
    val profileImageSelectionVisible: Boolean = false,
    val nicknameEditing: Boolean = false
)

@HiltViewModel
class MyPageViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val authRepository: AuthRepository,
    private val tokenManager: TokenManager // TokenManager 주입
) : ViewModel() {

    private val _uiState = MutableStateFlow(MyPageUiState()) // 기본값으로 UiState 생성
    val uiState: StateFlow<MyPageUiState> = _uiState.asStateFlow()

    private val _logoutEvent = MutableSharedFlow<Unit>()
    val logoutEvent: SharedFlow<Unit> = _logoutEvent.asSharedFlow()

    init {
        loadUserProfile()
    }

    // 사용자 프로필 불러오기
    private fun loadUserProfile() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            // 사용자 프로필 불러오기
            try {
                Log.d(TAG, "사용자 프로필 불러오기 시작")
                
                // 먼저 로컬 DB에서 유저 정보 가져오기 시도
                val localUser = userRepository.getUser()
                
                if (localUser != null) {
                    Log.d(TAG, "로컬 DB에서 사용자 정보 찾음: ${localUser.nickname}, 이메일: ${localUser.email}")
                    
                    // TokenManager에서 이메일 정보 직접 가져오기
                    val emailFromToken = tokenManager.getEmail()
                    Log.d(TAG, "TokenManager에서 가져온 이메일: $emailFromToken")
                    
                    // 유저 정보가 로컬에 있으면 바로 UI에 반영 (TokenManager의 이메일 사용)
                    _uiState.update { 
                        it.copy(
                            user = localUser,
                            nicknameInput = localUser.nickname,
                            statusMessageInput = localUser.statusMessage ?: "",
                            email = emailFromToken ?: localUser.email ?: "",
                            selectedProfileImageNumber = localUser.selectedProfileImageNumber
                        ) 
                    }
                    
                    // 백그라운드에서 현재 유저 정보 업데이트 (최신 정보 확인)
                    fetchUserProfile() 
                } else {
                    Log.d(TAG, "로컬 DB에 사용자 정보 없음, fetchUserProfile 호출")
                    fetchUserProfile()
                }
                
                // 친구 목록 가져오기 (필요시)
                fetchFriends()
            } catch (e: Exception) {
                Log.e(TAG, "사용자 프로필 불러오기 실패", e)
                _uiState.update { it.copy(
                    isLoading = false,
                    errorMessage = "사용자 정보를 불러오는데 실패했습니다: ${e.message}"
                ) }
            } finally {
                // 로딩 상태는 프로필 정보 로드 후 최종적으로 false로 설정
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    // 사용자 프로필 정보를 가져오는 함수
    private suspend fun fetchUserProfile() {
        try {
            Log.d(TAG, "fetchUserProfile 호출됨")
            
            val currentUser = userRepository.getCurrentUser()
            currentUser?.let { user ->
                // 유저 정보가 있으면 로컬 DB에 저장 (캐싱)
                userRepository.saveUser(user)
                
                Log.d(TAG, "사용자 정보 로컬 DB에 저장: ${user.nickname}, 이메일: ${user.email}, userId: ${user.userId}")
                
                _uiState.update { 
                    it.copy(
                        user = user,
                        nicknameInput = user.nickname,
                        statusMessageInput = user.statusMessage ?: "",
                        email = user.email ?: "",
                        selectedProfileImageNumber = user.selectedProfileImageNumber
                    ) 
                }
                Log.d(TAG, "사용자 프로필 로드 성공: ${user.nickname}")
            } ?: run {
                Log.e(TAG, "fetchUserProfile 실패: 사용자 정보 없음")
                
                // 로컬 DB에 저장된 유저 정보가 있는지 마지막으로 확인
                val lastKnownUser = userRepository.getUser()
                if (lastKnownUser != null) {
                    Log.d(TAG, "마지막 저장된 사용자 정보 사용: ${lastKnownUser.nickname}")
                    _uiState.update { 
                        it.copy(
                            user = lastKnownUser,
                            nicknameInput = lastKnownUser.nickname,
                            statusMessageInput = lastKnownUser.statusMessage ?: "",
                            email = lastKnownUser.email ?: "",
                            selectedProfileImageNumber = lastKnownUser.selectedProfileImageNumber
                        ) 
                    }
                } else {
                    _uiState.update { 
                        it.copy(errorMessage = "사용자 정보가 없습니다. 다시 로그인 해주세요.")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "사용자 프로필 로드 실패", e)
            
            // 예외 발생 시 로컬에 저장된, 마지막 알려진 유저 정보 사용 시도
            try {
                val localUser = userRepository.getUser()
                if (localUser != null) {
                    Log.d(TAG, "예외 발생 후 로컬 DB에서 사용자 정보 복구: ${localUser.nickname}")
                    _uiState.update { 
                        it.copy(
                            user = localUser,
                            nicknameInput = localUser.nickname,
                            statusMessageInput = localUser.statusMessage ?: "",
                            email = localUser.email ?: "",
                            selectedProfileImageNumber = localUser.selectedProfileImageNumber,
                            errorMessage = "최신 정보를 가져오지 못했습니다. 이전 저장된 정보를 표시합니다."
                        ) 
                    }
                } else {
                    _uiState.update { 
                        it.copy(errorMessage = "사용자 정보를 가져오는데 실패했습니다: ${e.message}")
                    }
                }
            } catch (localError: Exception) {
                _uiState.update { 
                    it.copy(errorMessage = "사용자 정보를 가져오는데 실패했습니다: ${e.message}")
                }
            }
        }
    }

    // 친구 목록을 가져오는 함수 (필요한 경우 구현)
    private suspend fun fetchFriends() {
        // 이 애플리케이션에서 친구 목록 관련 기능이 필요한 경우 구현
        // 현재는 빈 구현으로 두기
        Log.d(TAG, "친구 목록 로드 함수 호출됨 (아직 미구현)")
    }

    fun clearErrorMessage() { // 추가된 함수
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun toggleEditMode() {
        val currentState = _uiState.value
        if (currentState.isEditing) {
             // 편집 모드 종료 시, User 객체의 실제 값으로 복원
             _uiState.update {
                 it.copy(
                     isEditing = false,
                     nicknameInput = it.user?.nickname ?: "",
                     statusMessageInput = it.user?.statusMessage ?: "", // 상태 메시지 복원
                     selectedProfileImageNumber = it.user?.selectedProfileImageNumber ?: 1
                 )
             }
        } else {
            _uiState.update { it.copy(isEditing = true) }
        }
    }

    fun updateNickname(newNickname: String) {
        _uiState.update { it.copy(nicknameInput = newNickname) }
    }

    fun updateStatusMessage(newStatusMessage: String) { // 추가된 함수
        _uiState.update { it.copy(statusMessageInput = newStatusMessage) }
    }

    // 선택된 프로필 이미지의 "번호"를 업데이트합니다.
    fun updateSelectedProfileImageNumber(newImageNumber: Int) {
        if (_uiState.value.isEditing) {
            // 유효한 이미지 번호인지 확인 (1-15)
            if (newImageNumber in 1..15) { // getAllProfileImageResources().size 로 동적으로 변경 가능
                _uiState.update { it.copy(selectedProfileImageNumber = newImageNumber) }
            }
        }
    }

    fun saveProfileChanges() {
        val currentState = _uiState.value
        val userToUpdate = currentState.user ?: run {
             _uiState.update { it.copy(errorMessage = "사용자 정보가 없어 저장할 수 없습니다.") }
             return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            try {
                // 1. 닉네임 변경 사항이 있다면 업데이트
                if (userToUpdate.nickname != currentState.nicknameInput) {
                    userRepository.updateNickname(userToUpdate.userId, currentState.nicknameInput)
                    Log.d(TAG, "닉네임 업데이트: ${currentState.nicknameInput}")
                }

                // 2. 상태 메시지 변경 사항이 있다면 업데이트 (User 모델에 statusMessage 필드 및 userRepository에 관련 함수 필요)
                if (userToUpdate.statusMessage != currentState.statusMessageInput) {
                    // userRepository.updateStatusMessage(userToUpdate.userId, currentState.statusMessageInput)
                    // Log.d(TAG, "상태 메시지 업데이트: ${currentState.statusMessageInput}")
                    // 임시로 User 객체 직접 수정 (실제로는 Repository 통해 DB 업데이트 필요)
                    val tempUpdatedUser = userToUpdate.copy(statusMessage = currentState.statusMessageInput)
                    userRepository.saveUser(tempUpdatedUser) // saveUser가 UPSERT 역할을 한다면 가능
                }

                // 3. 프로필 이미지 번호 변경 사항이 있다면 업데이트
                if (userToUpdate.selectedProfileImageNumber != currentState.selectedProfileImageNumber) {
                    userRepository.updateProfileImageNumber(userToUpdate.userId, currentState.selectedProfileImageNumber)
                    Log.d(TAG, "프로필 이미지 번호 업데이트: ${currentState.selectedProfileImageNumber}")
                }
                
                // 업데이트된 사용자 정보 다시 로드하여 UI 반영
                val updatedUser = userRepository.getUserById(userToUpdate.userId)

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isEditing = false,
                        user = updatedUser, // DB에서 최신 정보 가져와서 반영
                        nicknameInput = updatedUser?.nickname ?: "",
                        statusMessageInput = updatedUser?.statusMessage ?: "",
                        selectedProfileImageNumber = updatedUser?.selectedProfileImageNumber ?: 1,
                        errorMessage = null
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "프로필 저장 오류", e)
                val errorMessage = "저장 실패: ${e.message}"
                _uiState.update { it.copy(isLoading = false, errorMessage = errorMessage) }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            // 로컬 DB 데이터 초기화
            userRepository.clearAllLocalData()
            val result = authRepository.signOut()
            when (result) {
                is AuthResult.Success -> {
                    Log.d(TAG, "로그아웃 성공")
                    _logoutEvent.emit(Unit)
                }
                is AuthResult.Error -> {
                    Log.e(TAG, "로그아웃 실패: ${result.message}")
                    _uiState.update { it.copy(isLoading = false, errorMessage = "로그아웃 실패: ${result.message}") }
                }
                AuthResult.Loading -> {
                    // Loading 상태 처리
                }
            }
        }
    }

    // 다크/라이트 모드 설정 함수 제거
    /*
    fun setDarkMode(isDarkMode: Boolean) {
        _uiState.update { it.copy(isDarkMode = isDarkMode) }
        
        // 실제 앱에서는 설정을 저장하는 로직이 추가되어야 합니다.
        viewModelScope.launch {
            try {
                // 사용자 설정에 다크모드 여부 저장
                userRepository.saveDisplayMode(isDarkMode)
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "디스플레이 모드 저장 실패: ${e.message}") }
            }
        }
    }
    */
} 