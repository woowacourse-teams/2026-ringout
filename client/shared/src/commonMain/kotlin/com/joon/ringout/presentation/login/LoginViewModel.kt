package com.joon.ringout.presentation.login

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joon.ringout.analytics.AnalyticsAuthProvider
import com.joon.ringout.analytics.ProductAnalyticsRecorder
import com.joon.ringout.diagnostics.AuthDiagnosticLogger
import com.joon.ringout.domain.auth.AuthRepository
import com.joon.ringout.domain.auth.SocialLoginOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
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
        val provider: AnalyticsAuthProvider,
    ) : LoginCompletion
}

class LoginViewModel(
    private val authRepository: AuthRepository,
    private val productAnalyticsRecorder: ProductAnalyticsRecorder,
    coroutineScope: CoroutineScope? = null,
) : ViewModel() {
    var uiState by mutableStateOf(LoginUiState())
        private set

    private var nextEventId = 0L
    private var activeProvider: AnalyticsAuthProvider? = null
    private var loginJob: Job? = null
    private var isCleared = false
    private val scope = coroutineScope ?: viewModelScope

    fun beginAppleSignIn(): Boolean {
        return beginSocialSignIn(AnalyticsAuthProvider.Apple)
    }

    fun beginGoogleSignIn(): Boolean {
        return beginSocialSignIn(AnalyticsAuthProvider.Google)
    }

    fun beginKakaoSignIn(): Boolean {
        return beginSocialSignIn(AnalyticsAuthProvider.Kakao)
    }

    private fun beginSocialSignIn(provider: AnalyticsAuthProvider): Boolean {
        if (isCleared) return false
        if (uiState.isLoading) {
            AuthDiagnosticLogger.debug(
                "login_start_ignored provider=${provider.logValue} reason=already_in_progress",
            )
            return false
        }
        activeProvider = provider
        uiState = uiState.copy(isLoading = true, errorMessage = null)
        AuthDiagnosticLogger.debug("login_started provider=${provider.logValue}")
        runCatching {
            productAnalyticsRecorder.recordLoginStarted(provider)
        }.onFailure { error ->
            AuthDiagnosticLogger.error(
                "analytics_login_started_failed provider=${provider.logValue}",
                error,
            )
        }
        return true
    }

    fun handleGoogleAccessTokenResult(result: GoogleAccessTokenResult) {
        if (isCleared || activeProvider != AnalyticsAuthProvider.Google) {
            AuthDiagnosticLogger.debug(
                "sdk_result_ignored provider=google reason=no_matching_login",
            )
            return
        }
        when (result) {
            GoogleAccessTokenResult.Cancelled -> {
                AuthDiagnosticLogger.debug("sdk_cancelled provider=google")
                activeProvider = null
                uiState = uiState.copy(isLoading = false)
            }

            is GoogleAccessTokenResult.Failure -> {
                AuthDiagnosticLogger.error(
                    "sdk_failed provider=google",
                    IllegalStateException(result.message),
                )
                activeProvider = null
                uiState = uiState.copy(
                    isLoading = false,
                    errorMessage = result.message,
                )
            }

            is GoogleAccessTokenResult.Success -> {
                AuthDiagnosticLogger.debug("sdk_succeeded provider=google token_received=true")
                activeProvider = null
                loginWithGoogle(result.accessToken)
            }
        }
    }

    fun handleAppleIdTokenResult(result: AppleIdTokenResult) {
        if (isCleared || activeProvider != AnalyticsAuthProvider.Apple) {
            AuthDiagnosticLogger.debug(
                "sdk_result_ignored provider=apple reason=no_matching_login",
            )
            return
        }
        when (result) {
            AppleIdTokenResult.Cancelled -> {
                AuthDiagnosticLogger.debug("sdk_cancelled provider=apple")
                activeProvider = null
                uiState = uiState.copy(isLoading = false)
            }

            is AppleIdTokenResult.Failure -> {
                AuthDiagnosticLogger.error(
                    "sdk_failed provider=apple",
                    IllegalStateException(result.message),
                )
                activeProvider = null
                uiState = uiState.copy(
                    isLoading = false,
                    errorMessage = result.message,
                )
            }

            is AppleIdTokenResult.Success -> {
                AuthDiagnosticLogger.debug("sdk_succeeded provider=apple token_received=true")
                activeProvider = null
                loginWithApple(result.idToken)
            }
        }
    }

    fun handleKakaoAccessTokenResult(result: KakaoAccessTokenResult) {
        if (isCleared || activeProvider != AnalyticsAuthProvider.Kakao) {
            AuthDiagnosticLogger.debug(
                "sdk_result_ignored provider=kakao reason=no_matching_login",
            )
            return
        }
        when (result) {
            KakaoAccessTokenResult.Cancelled -> {
                AuthDiagnosticLogger.debug("sdk_cancelled provider=kakao")
                activeProvider = null
                uiState = uiState.copy(isLoading = false)
            }

            is KakaoAccessTokenResult.Failure -> {
                AuthDiagnosticLogger.error(
                    "sdk_failed provider=kakao",
                    IllegalStateException(result.message),
                )
                activeProvider = null
                uiState = uiState.copy(
                    isLoading = false,
                    errorMessage = result.message,
                )
            }

            is KakaoAccessTokenResult.Success -> {
                AuthDiagnosticLogger.debug("sdk_succeeded provider=kakao token_received=true")
                activeProvider = null
                loginWithKakao(result.accessToken)
            }
        }
    }

    fun showUnavailableProvider(provider: SocialLoginProvider) {
        if (isCleared || uiState.isLoading) return
        uiState = uiState.copy(
            errorMessage = "${provider.displayName} 로그인은 아직 준비 중이에요.",
        )
    }

    fun consumeCompletion(eventId: Long) {
        if (uiState.completion?.eventId == eventId) {
            uiState = uiState.copy(completion = null)
        }
    }

    override fun onCleared() {
        isCleared = true
        activeProvider = null
        loginJob?.cancel()
        loginJob = null
        uiState = LoginUiState()
        super.onCleared()
    }

    private fun loginWithGoogle(accessToken: String) {
        loginWithSocialProvider(AnalyticsAuthProvider.Google) {
            authRepository.loginWithGoogle(accessToken)
        }
    }

    private fun loginWithApple(idToken: String) {
        loginWithSocialProvider(AnalyticsAuthProvider.Apple) {
            authRepository.loginWithApple(idToken)
        }
    }

    private fun loginWithKakao(accessToken: String) {
        loginWithSocialProvider(AnalyticsAuthProvider.Kakao) {
            authRepository.loginWithKakao(accessToken)
        }
    }

    private fun loginWithSocialProvider(
        provider: AnalyticsAuthProvider,
        login: suspend () -> SocialLoginOutcome,
    ) {
        if (isCleared) return
        loginJob = scope.launch {
            if (isCleared || !isActive) return@launch
            AuthDiagnosticLogger.debug("backend_login_started provider=${provider.logValue}")
            try {
                val outcome = login()
                if (isCleared || !isActive) return@launch
                val isNewUser = outcome is SocialLoginOutcome.SignupRequired
                AuthDiagnosticLogger.debug(
                    "backend_login_succeeded provider=${provider.logValue} " +
                        "is_new_user=$isNewUser",
                )
                runCatching {
                    productAnalyticsRecorder.recordLoginCompleted(
                        provider = provider,
                        isNewUser = isNewUser,
                    )
                }.onFailure { error ->
                    AuthDiagnosticLogger.error(
                        "analytics_login_completed_failed provider=${provider.logValue}",
                        error,
                    )
                }
                if (isCleared || !isActive) return@launch
                val completion = when (outcome) {
                    SocialLoginOutcome.Authenticated ->
                        LoginCompletion.Authenticated(++nextEventId)

                    is SocialLoginOutcome.SignupRequired ->
                        LoginCompletion.SignupRequired(
                            eventId = ++nextEventId,
                            signupToken = outcome.signupToken,
                            provider = provider,
                        )
                }
                uiState = uiState.copy(
                    isLoading = false,
                    completion = completion,
                )
            } catch (error: CancellationException) {
                AuthDiagnosticLogger.debug("backend_login_cancelled provider=${provider.logValue}")
                throw error
            } catch (error: Throwable) {
                if (isCleared || !isActive) return@launch
                AuthDiagnosticLogger.error(
                    "backend_login_failed provider=${provider.logValue}",
                    error,
                )
                uiState = uiState.copy(
                    isLoading = false,
                    errorMessage = error.message ?: "로그인하지 못했어요. 다시 시도해 주세요.",
                )
            }
        }
    }
}

private val AnalyticsAuthProvider.logValue: String
    get() = when (this) {
        AnalyticsAuthProvider.Google -> "google"
        AnalyticsAuthProvider.Kakao -> "kakao"
        AnalyticsAuthProvider.Apple -> "apple"
    }

private val SocialLoginProvider.displayName: String
    get() = when (this) {
        SocialLoginProvider.Apple -> "Apple"
        SocialLoginProvider.Google -> "Google"
        SocialLoginProvider.Kakao -> "카카오"
    }
