package com.joon.ringout.domain.terms

import kotlin.jvm.JvmInline

@JvmInline
value class TermId(val value: String) {
    companion object {
        val Service = TermId("service")
        val Privacy = TermId("privacy")
    }
}

enum class TermType(val persistedValue: String) {
    REQUIRED("required"),
    OPTIONAL("optional");

    companion object {
        fun fromPersistedValue(value: String): TermType? =
            entries.firstOrNull { type -> type.persistedValue == value }
    }
}

data class TermDefinition(
    val id: TermId,
    val name: String,
    val version: String,
    val type: TermType,
)

val currentTerms = listOf(
    TermDefinition(
        id = TermId.Service,
        name = "서비스 이용약관",
        version = "1",
        type = TermType.REQUIRED,
    ),
    TermDefinition(
        id = TermId.Privacy,
        name = "개인정보처리방침",
        version = "1",
        type = TermType.REQUIRED,
    ),
)
