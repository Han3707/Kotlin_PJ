package com.ssafy.lantern.ui.screens.mypage

import androidx.annotation.DrawableRes // Drawable 리소스 ID 사용
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.lantern.R // 리소스 ID 접근
import com.ssafy.lantern.data.model.User
import com.ssafy.lantern.data.repository.AuthRepository // AuthRepository 임포트
import com.ssafy.lantern.data.repository.AuthResult // AuthResult 임포트 추가
import com.ssafy.lantern.data.repository.UserRepository
// import com.ssafy.lantern.ui.screens.login.LoginViewModel // LoginViewModel 임포트 제거
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MyPageUiState(
    val isLoading: Boolean = false,
    val isEditing: Boolean = false,
    val user: User? = null,
    val errorMessage: String? = null,
    // 입력 상태
    val nicknameInput: String = "",
    // 임시로 lantern_image 사용
    @DrawableRes val profileImageResId: Int = R.drawable.lantern_image,
    val availableProfileImages: List<Int> = defaultProfileImages
)

// 사용할 프로필 이미지 리소스 ID 목록 정의
val defaultProfileImages: List<Int> = listOf(
    R.drawable.profile_1, R.drawable.profile_2, R.drawable.profile_3,
    R.drawable.profile_4, R.drawable.profile_5, R.drawable.profile_6,
    R.drawable.profile_7, R.drawable.profile_8, R.drawable.profile_9,
    R.drawable.profile_10, R.drawable.profile_11, R.drawable.profile_12,
    R.drawable.profile_13, R.drawable.profile_14, R.drawable.profile_15
)

@HiltViewModel
class MyPageViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val authRepository: AuthRepository // LoginViewModel 대신 AuthRepository 주입
) : ViewModel() {

    private val _uiState = MutableStateFlow(MyPageUiState(profileImageResId = R.drawable.lantern_image, availableProfileImages = defaultProfileImages))
    val uiState: StateFlow<MyPageUiState> = _uiState.asStateFlow()

    private val _logoutEvent = MutableSharedFlow<Unit>()
    val logoutEvent: SharedFlow<Unit> = _logoutEvent.asSharedFlow()

    init {
        loadUserProfile()
    }

    private fun loadUserProfile() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                // FIXME: UserRepository에 getCurrentUser() 함수 구현 및 반환 타입 확인 후 아래 주석 해제 필요.
                // val currentUser = userRepository.getCurrentUser()
                val currentUser: User? = null // 임시 null 처리 (컴파일 오류 방지)
                if (currentUser != null) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            user = currentUser,
                            nicknameInput = currentUser.nickname,
                            // FIXME: User 모델에 profileImageResId 필드(Int? 타입) 추가 후 아래 주석 해제 필요.
                            // profileImageResId = currentUser.profileImageResId ?: defaultProfileImages.first(),
                            profileImageResId = defaultProfileImages.first(), // 임시 기본값
                            errorMessage = null
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            user = null,
                            nicknameInput = "",
                            profileImageResId = defaultProfileImages.first(),
                            errorMessage = "사용자 정보를 불러올 수 없습니다."
                        )
                    }
                }
            } catch (e: Exception) {
                 _uiState.update {
                    it.copy(
                        isLoading = false,
                        user = null,
                        nicknameInput = "",
                        profileImageResId = defaultProfileImages.first(),
                        errorMessage = "오류 발생: ${e.message}"
                    )
                 }
            }
        }
    }

    fun toggleEditMode() {
        val currentState = _uiState.value
        if (currentState.isEditing) {
             _uiState.update {
                 it.copy(
                     isEditing = false,
                     nicknameInput = it.user?.nickname ?: "",
                     // FIXME: User 모델에 profileImageResId 필드 추가 후 아래 주석 해제 필요.
                     // profileImageResId = it.user?.profileImageResId ?: defaultProfileImages.first()
                     profileImageResId = defaultProfileImages.first() // 임시
                 )
             }
        } else {
            _uiState.update { it.copy(isEditing = true) }
        }
    }

    fun updateNickname(newNickname: String) {
        _uiState.update { it.copy(nicknameInput = newNickname) }
    }

    fun updateProfileImage(@DrawableRes newResId: Int) {
        if (_uiState.value.isEditing) {
            // 실제로는 R.drawable.lantern_image만 있으므로 변경 의미 없음
            _uiState.update { it.copy(profileImageResId = newResId) }
        }
    }

    fun saveProfileChanges() {
        val currentState = _uiState.value
        val userToUpdate = currentState.user ?: run {
             _uiState.update { it.copy(errorMessage = "사용자 정보가 없어 저장할 수 없습니다.") }
             return
        }

        // FIXME: User 모델에 profileImageResId 필드가 Int? 타입으로 정의되어 있는지 확인 후 아래 copy 수정.
        val updatedUser = userToUpdate.copy(
            nickname = currentState.nicknameInput
            // , profileImageResId = currentState.profileImageResId // User 모델 확인 후 주석 해제
        )

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                // FIXME: UserRepository에 updateUser(User) 함수 구현 후 아래 주석 해제 필요.
                // userRepository.updateUser(updatedUser)

                // --- 임시 로직 (성공한 것처럼 UI 업데이트) --- 
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isEditing = false,
                        user = updatedUser,
                        errorMessage = null
                    )
                }
                 // --- 임시 로직 끝 --- 

            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "저장 실패: ${e.message}") }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) } // 로딩 상태 표시 (선택 사항)
            val result = authRepository.signOut() // AuthRepository의 signOut 호출
            when (result) {
                is AuthResult.Success -> {
                    _logoutEvent.emit(Unit) // 성공 시 이벤트 발생
                }
                is AuthResult.Error -> {
                    // 실패 시 에러 메시지 표시
                    _uiState.update { it.copy(isLoading = false, errorMessage = "로그아웃 실패: ${result.message}") }
                }
                AuthResult.Loading -> {
                    // Loading 상태 처리 (필요한 경우)
                }
            }
        }
    }
} 