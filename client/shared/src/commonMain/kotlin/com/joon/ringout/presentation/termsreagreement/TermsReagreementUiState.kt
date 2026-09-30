package com.joon.ringout.presentation.termsreagreement

import com.joon.ringout.domain.terms.RequiredTermType
import com.joon.ringout.domain.terms.RequiredTermsStatus
import com.joon.ringout.presentation.termsagreement.TermAgreementItem
import com.joon.ringout.presentation.termsagreement.TermsAgreementContentState
import com.joon.ringout.presentation.termsagreement.TermsAgreementUiState

enum class TermsGatePhase { Checking, Allowed, Offline, Notice, Consent, Failure }

data class TermsReagreementUiState(
    val phase: TermsGatePhase = TermsGatePhase.Checking,
    val status: RequiredTermsStatus? = null,
    val selected: Set<RequiredTermType> = emptySet(),
    val isSubmitting: Boolean = false,
    val isLoggingOut: Boolean = false,
    val errorMessage: String? = null,
) {
    val canSubmit: Boolean get() = phase == TermsGatePhase.Consent &&
        !isSubmitting && !isLoggingOut && status?.pending?.let { pending ->
            pending.isNotEmpty() && pending.all { it.type in selected }
        } == true
    val blocksService: Boolean get() = phase != TermsGatePhase.Allowed && phase != TermsGatePhase.Offline
}

internal fun TermsReagreementUiState.toTermsAgreementUiState() = TermsAgreementUiState(
    terms = status?.agreements.orEmpty().map { term ->
        TermAgreementItem(
            id = term.type.termId,
            title = term.type.title,
            isRequired = true,
            isAgreed = term.isCurrent || term.type in selected,
            isReadOnly = term.isCurrent,
            version = term.latestVersion,
        )
    },
    title = "최신 약관에 동의해 주세요",
    actionLabel = when {
        phase == TermsGatePhase.Failure -> "다시 확인하기"
        isSubmitting -> "동의 확인 중…"
        else -> "동의하고 계속하기"
    },
    contentState = when (phase) {
        TermsGatePhase.Consent -> TermsAgreementContentState.Agreement
        TermsGatePhase.Failure -> TermsAgreementContentState.Error
        else -> TermsAgreementContentState.Loading
    },
)
