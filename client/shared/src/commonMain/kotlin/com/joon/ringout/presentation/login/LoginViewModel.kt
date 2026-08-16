package com.joon.ringout.presentation.login

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joon.ringout.domain.auth.AuthRepository
import com.joon.ringout.domain.auth.SocialLoginOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

data class LoginUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val completion: LoginCompletion? = null,
)

sealed interface LoginCompletion {
    val eventId: Long

    data class Authenticated(
        override val eventId: Long,
    ) : LoginCompletion

    data class SignupRequired(
        override val eventId: Long,
        val signupToken: String,
    ) : LoginCompletion
}

class LoginViewModel(
    private val authRepository: AuthRepository,
) : ViewModel() {
    var uiState by mutableStateOf(LoginUiState())
        private set

    private var nextEventId = 0L

    fun beginGoogleSignIn(): Boolean {
        if (uiState.isLoading) return false
        uiState = uiState.copy(isLoading = true, errorMessage = null)
        return true
    }

    fun handleGoogleAccessTokenResult(result: GoogleAccessTokenResult) {
        when (result) {
            GoogleAccessTokenResult.Cancelled -> {
                uiState = uiState.copy(isLoading = false)
            }

            is GoogleAccessTokenResult.Failure -> {
                uiState = uiState.copy(
                    isLoading = false,
                    errorMessage = result.message,
                )
            }

            is GoogleAccessTokenResult.Success -> loginWithGoogle(result.accessToken)
        }
    }

    fun showUnavailableProvider(provider: SocialLoginProvider) {
        if (uiState.isLoading) return
        uiState = uiState.copy(
            errorMessage = "${provider.displayName} 로그인은 아직 준비 중이에요.",
        )
    }

    fun consumeCompletion(eventId: Long) {
        if (uiState.completion?.eventId == eventId) {
            uiState = uiState.copy(completion = null)
        }
    }

    private fun loginWithGoogle(accessToken: String) {
        viewModelScope.launch {
            try {
                val completion = when (val outcome = authRepository.loginWithGoogle(accessToken)) {
                    SocialLoginOutcome.Authenticated ->
                        LoginCompletion.Authenticated(++nextEventId)

                    is SocialLoginOutcome.SignupRequired ->
                        LoginCompletion.SignupRequired(
                            eventId = ++nextEventId,
                            signupToken = outcome.signupToken,
                        )
                }
                uiState = uiState.copy(
                    isLoading = false,
                    completion = completion,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                uiState = uiState.copy(
                    isLoading = false,
                    errorMessage = error.message ?: "로그인하지 못했어요. 다시 시도해 주세요.",
                )
            }
        }
    }
}

private val SocialLoginProvider.displayName: String
    get() = when (this) {
        SocialLoginProvider.Google -> "Google"
        SocialLoginProvider.Kakao -> "카카오"
        SocialLoginProvider.Naver -> "네이버"
    }
