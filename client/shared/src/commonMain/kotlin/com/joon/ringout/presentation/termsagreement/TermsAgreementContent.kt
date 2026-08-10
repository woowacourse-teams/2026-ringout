package com.joon.ringout.presentation.termsagreement

import com.joon.ringout.domain.terms.TermType
import com.joon.ringout.domain.terms.currentTerms

typealias TermId = com.joon.ringout.domain.terms.TermId

data class TermAgreementItem(
    val id: TermId,
    val title: String,
    val isRequired: Boolean,
    val isAgreed: Boolean = false,
)

val defaultTerms = currentTerms.map { definition ->
    TermAgreementItem(
        id = definition.id,
        title = "${definition.name} 동의",
        isRequired = definition.type == TermType.REQUIRED,
    )
}
