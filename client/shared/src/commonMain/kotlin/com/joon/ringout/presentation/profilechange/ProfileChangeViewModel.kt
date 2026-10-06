package com.joon.ringout.presentation.profilechange

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joon.ringout.domain.member.MemberRepository
import com.joon.ringout.domain.member.ProfileImageUpload
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Immutable
internal data class ProfileChangeUiState(
    val nickname: String,
    val validation: NicknameValidation,
    val profileImageUrl: String? = null,
    val hasImageChanges: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val completedNickname: String? = null,
)

internal class ProfileChangeViewModel(
    initialNickname: String,
    private val memberRepository: MemberRepository,
    coroutineScope: CoroutineScope? = null,
) : ViewModel() {
    var uiState by mutableStateOf(
        ProfileChangeUiState(
            nickname = initialNickname,
            validation = validateNickname(initialNickname),
            profileImageUrl = memberRepository.getCachedProfileImage()?.url,
        ),
    )
        private set

    private val scope = coroutineScope ?: viewModelScope
    private var savedNickname = initialNickname
    private var pendingImage: ProfileImageUpload? = null
    private var imageSaveVersion = 0L

    init {
        if (memberRepository.getCachedProfileImage() == null) {
            val version = imageSaveVersion
            scope.launch {
                try {
                    val image = memberRepository.getProfileImage()
                    if (version == imageSaveVersion) uiState = uiState.copy(profileImageUrl = image.url)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    // 기존 사진 조회 실패가 새 사진 선택이나 저장을 막지 않는다.
                }
            }
        }
    }

    fun onNicknameChange(nickname: String) {
        if (uiState.isSaving) return
        uiState = uiState.copy(nickname = nickname, validation = validateNickname(nickname), errorMessage = null)
    }

    fun onProfileImageSelected(image: ProfileImageUpload): Boolean {
        if (uiState.isSaving) return false
        pendingImage = image
        uiState = uiState.copy(hasImageChanges = true, errorMessage = null)
        return true
    }

    fun confirm() {
        if (uiState.isSaving || !uiState.validation.isValid || uiState.completedNickname != null) return
        val requestedNickname = uiState.nickname
        val requestedImage = pendingImage
        uiState = uiState.copy(isSaving = true, errorMessage = null)
        scope.launch {
            var nicknameSaved = false
            try {
                if (requestedNickname != savedNickname) {
                    savedNickname = memberRepository.updateNickname(requestedNickname)
                    nicknameSaved = true
                    uiState = uiState.copy(nickname = savedNickname, validation = validateNickname(savedNickname))
                }
                if (requestedImage != null) {
                    val image = memberRepository.uploadProfileImage(requestedImage)
                    imageSaveVersion++
                    pendingImage = null
                    uiState = uiState.copy(profileImageUrl = image.url, hasImageChanges = false)
                }
                uiState = uiState.copy(isSaving = false, completedNickname = savedNickname)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val message = error.message ?: "프로필을 저장하지 못했어요. 다시 시도해 주세요."
                uiState = uiState.copy(
                    isSaving = false,
                    errorMessage = if (nicknameSaved) "닉네임은 저장됐어요. 사진 저장을 다시 시도해 주세요.\n$message" else message,
                )
            }
        }
    }

    fun consumeCompletedNickname() {
        uiState = uiState.copy(completedNickname = null)
    }
}
