package com.joon.ringout.presentation.termsagreement

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joon.ringout.domain.auth.AuthRepository
import com.joon.ringout.domain.auth.AuthTerm
import com.joon.ringout.domain.destination.DestinationRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

data class SignupUiState(
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val completedEventId: Long? = null,
)

class SignupViewModel(
    private val authRepository: AuthRepository,
    private val destinationRepository: DestinationRepository,
    private val currentDate: () -> String = ::currentAgreementDate,
) : ViewModel() {
    var uiState by mutableStateOf(SignupUiState())
        private set

    private var nextEventId = 0L
    private var completedSignupToken: String? = null

    fun signup(
        signupToken: String,
        agreedTermIds: Set<TermId>,
    ) {
        if (uiState.isSaving) return
        val terms = agreedTermIds.mapNotNullTo(mutableSetOf()) { id ->
            when (id) {
                TermId.Service -> AuthTerm.SERVICE
                TermId.Privacy -> AuthTerm.PRIVACY
                else -> null
            }
        }
        uiState = uiState.copy(isSaving = true, errorMessage = null)
        viewModelScope.launch {
            try {
                if (completedSignupToken != signupToken) {
                    authRepository.signup(
                        signupToken = signupToken,
                        agreedTerms = terms,
                        agreedAt = currentDate(),
                    )
                    completedSignupToken = signupToken
                }
                destinationRepository.sync()
                uiState = uiState.copy(
                    isSaving = false,
                    completedEventId = ++nextEventId,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                uiState = uiState.copy(
                    isSaving = false,
                    errorMessage = if (completedSignupToken == signupToken) {
                        error.message ?: "회원가입은 완료됐지만 목적지를 동기화하지 못했어요."
                    } else {
                        error.message ?: "회원가입을 완료하지 못했어요."
                    },
                )
            }
        }
    }

    fun consumeCompletedEvent(eventId: Long) {
        if (uiState.completedEventId == eventId) {
            uiState = uiState.copy(completedEventId = null)
            completedSignupToken = null
        }
    }
}

internal expect fun currentAgreementDate(): String
