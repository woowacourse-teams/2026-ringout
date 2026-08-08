package com.joon.ringout.presentation.termsagreement

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

data class TermsAgreementUiState(
    val terms: List<TermAgreementItem> = defaultTerms,
) {
    val isAllAgreed: Boolean
        get() = terms.isNotEmpty() && terms.all(TermAgreementItem::isAgreed)

    val canStart: Boolean
        get() = terms
            .filter(TermAgreementItem::isRequired)
            .all(TermAgreementItem::isAgreed)
}

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
            terms = uiState.terms.map { term -> term.copy(isAgreed = agreed) },
        )
    }

    fun setTermAgreed(termId: TermId, agreed: Boolean) {
        uiState = uiState.copy(
            terms = uiState.terms.map { term ->
                if (term.id == termId) term.copy(isAgreed = agreed) else term
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
