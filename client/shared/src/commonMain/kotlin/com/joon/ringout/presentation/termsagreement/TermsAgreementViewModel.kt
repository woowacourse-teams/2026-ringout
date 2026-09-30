package com.joon.ringout.presentation.termsagreement

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

enum class TermsAgreementAdvance {
    Complete,
    BlockedMissingRequiredAgreement,
}

class TermsAgreementViewModel(
    initialTerms: List<TermAgreementItem> = defaultTerms,
) : ViewModel() {
    var uiState by mutableStateOf(TermsAgreementUiState(terms = initialTerms))
        private set

    fun setAllAgreed(agreed: Boolean) {
        uiState = uiState.copy(
            terms = uiState.terms.map { term ->
                if (term.isReadOnly) term else term.copy(isAgreed = agreed)
            },
        )
    }

    fun setTermAgreed(termId: TermId, agreed: Boolean) {
        uiState = uiState.copy(
            terms = uiState.terms.map { term ->
                if (term.id == termId && !term.isReadOnly) term.copy(isAgreed = agreed) else term
            },
        )
    }

    fun requestStart(): TermsAgreementAdvance =
        if (uiState.canStart) {
            TermsAgreementAdvance.Complete
        } else {
            TermsAgreementAdvance.BlockedMissingRequiredAgreement
        }
}
