package com.joon.ringout.presentation.nickname

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joon.ringout.domain.member.MemberRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Immutable
internal data class NicknameChangeUiState(
    val nickname: String,
    val validation: NicknameValidation,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val completedNickname: String? = null,
)

internal class NicknameChangeViewModel(
    initialNickname: String,
    private val memberRepository: MemberRepository,
    coroutineScope: CoroutineScope? = null,
) : ViewModel() {
    var uiState by mutableStateOf(initialNickname.toNicknameChangeUiState())
        private set

    private val scope = coroutineScope ?: viewModelScope

    fun onNicknameChange(nickname: String) {
        if (uiState.isSaving) return
        uiState = nickname.toNicknameChangeUiState()
    }

    fun confirm() {
        if (uiState.isSaving || !uiState.validation.isValid) return
        val requestedNickname = uiState.nickname
        uiState = uiState.copy(isSaving = true, errorMessage = null)
        scope.launch {
            try {
                val updatedNickname = memberRepository.updateNickname(requestedNickname)
                uiState = uiState.copy(
                    nickname = updatedNickname,
                    validation = validateNickname(updatedNickname),
                    isSaving = false,
                    completedNickname = updatedNickname,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                uiState = uiState.copy(
                    isSaving = false,
                    errorMessage = error.message ?: "닉네임을 변경하지 못했어요.",
                )
            }
        }
    }

    fun consumeCompletedNickname() {
        uiState = uiState.copy(completedNickname = null)
    }

    private fun String.toNicknameChangeUiState(): NicknameChangeUiState =
        NicknameChangeUiState(
            nickname = this,
            validation = validateNickname(this),
        )
}
