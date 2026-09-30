package com.joon.ringout.presentation.termsagreement

enum class TermsAgreementContentState { Agreement, Loading, Error }

data class TermsAgreementUiState(
    val terms: List<TermAgreementItem> = defaultTerms,
    val title: String = "서비스 이용을 위해 약관에 동의해주세요",
    val actionLabel: String = "시작하기",
    val contentState: TermsAgreementContentState = TermsAgreementContentState.Agreement,
) {
    val isAllAgreed: Boolean
        get() = terms.isNotEmpty() && terms.all(TermAgreementItem::isAgreed)

    val canStart: Boolean
        get() = terms
            .filter(TermAgreementItem::isRequired)
            .all(TermAgreementItem::isAgreed)
}
