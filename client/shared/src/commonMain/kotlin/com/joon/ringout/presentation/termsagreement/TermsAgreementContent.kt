package com.joon.ringout.presentation.termsagreement

import kotlin.jvm.JvmInline

@JvmInline
value class TermId(val value: String) {
    companion object {
        val Service = TermId("service")
        val Privacy = TermId("privacy")
    }
}

data class TermAgreementItem(
    val id: TermId,
    val title: String,
    val isRequired: Boolean,
    val isAgreed: Boolean = false,
)

val defaultTerms = listOf(
    TermAgreementItem(
        id = TermId.Service,
        title = "서비스 이용약관 동의",
        isRequired = true,
    ),
    TermAgreementItem(
        id = TermId.Privacy,
        title = "개인정보처리방침 동의",
        isRequired = true,
    ),
)
