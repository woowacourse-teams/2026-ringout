package com.joon.ringout.domain.terms

data class TermConsentRecord(
    val id: TermId,
    val termName: String,
    val termVersion: String,
    val termType: TermType,
    val isAgreed: Boolean,
    val agreedAtEpochMillis: Long?,
)
