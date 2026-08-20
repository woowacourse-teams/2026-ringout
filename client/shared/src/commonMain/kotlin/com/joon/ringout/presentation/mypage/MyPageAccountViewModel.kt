package com.joon.ringout.presentation.mypage

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joon.ringout.domain.member.MemberRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MyPageAccountViewModel(
    private val memberRepository: MemberRepository,
    coroutineScope: CoroutineScope? = null,
) : ViewModel() {
    var uiState by mutableStateOf<MyPageAccountUiState>(MyPageAccountUiState.Loading)
        private set

    private val scope = coroutineScope ?: viewModelScope
    private var loadJob: Job? = null
    private var requestId = 0L

    fun onSessionRestoring() {
        cancelLoad()
        uiState = MyPageAccountUiState.Loading
    }

    fun onAuthenticated() {
        loadProfile()
    }

    fun onLoggedOut() {
        cancelLoad()
        uiState = MyPageAccountUiState.LoggedOut
    }

    fun retry() {
        if (uiState != MyPageAccountUiState.Error) return
        loadProfile()
    }

    fun onNicknameUpdated(nickname: String) {
        val profile = uiState as? MyPageAccountUiState.LoggedIn ?: return
        uiState = profile.copy(nickname = nickname)
    }

    private fun loadProfile() {
        loadJob?.cancel()
        val currentRequestId = ++requestId
        uiState = MyPageAccountUiState.Loading
        loadJob = scope.launch {
            try {
                val profile = memberRepository.getProfile()
                if (currentRequestId != requestId) return@launch
                uiState = MyPageAccountUiState.LoggedIn(
                    nickname = profile.nickname,
                    email = profile.email?.takeIf { it.isNotBlank() } ?: MissingEmailMessage,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                if (currentRequestId == requestId) {
                    uiState = MyPageAccountUiState.Error
                }
            }
        }
    }

    private fun cancelLoad() {
        requestId++
        loadJob?.cancel()
        loadJob = null
    }
}

private const val MissingEmailMessage = "이메일 정보 없음"
