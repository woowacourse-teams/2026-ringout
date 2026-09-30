package com.joon.ringout.presentation.termsreagreement

import com.joon.ringout.domain.terms.RequiredTermType
import com.joon.ringout.domain.terms.RequiredTermsStatus
import com.joon.ringout.presentation.termsagreement.TermAgreementItem
import com.joon.ringout.presentation.termsagreement.TermsAgreementContentState
import com.joon.ringout.presentation.termsagreement.TermsAgreementUiState

enum class TermsGatePhase { Checking, CheckFailed, Allowed, Offline, Notice, Consent, Failure }

data class TermsReagreementUiState(
    val phase: TermsGatePhase = TermsGatePhase.Checking,
    val status: RequiredTermsStatus? = null,
    val selected: Set<RequiredTermType> = emptySet(),
    val isSubmitting: Boolean = false,
    val isChecking: Boolean = false,
    val isLoggingOut: Boolean = false,
    val errorMessage: String? = null,
) {
    val canSubmit: Boolean get() = phase == TermsGatePhase.Consent &&
        !isSubmitting && !isChecking && !isLoggingOut && status?.pending?.let { pending ->
            pending.isNotEmpty() && pending.all { it.type in selected }
        } == true
    // 서버가 재동의 필요를 확인한 뒤에만 모달을 표시한다.
    val blocksService: Boolean get() = phase == TermsGatePhase.Notice ||
        phase == TermsGatePhase.Consent || phase == TermsGatePhase.Failure
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
        isSubmitting || isChecking -> "동의 확인 중…"
        phase == TermsGatePhase.Failure -> "다시 확인하기"
        else -> "동의하고 계속하기"
    },
    contentState = if (phase == TermsGatePhase.Failure) {
        TermsAgreementContentState.Error
    } else {
        TermsAgreementContentState.Agreement
    },
)
