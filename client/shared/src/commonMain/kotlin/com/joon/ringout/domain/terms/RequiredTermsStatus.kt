package com.joon.ringout.domain.terms

enum class RequiredTermType(val title: String, val termId: TermId) {
    SERVICE("서비스 이용약관", TermId.Service),
    PRIVACY("개인정보처리방침", TermId.Privacy),
}

data class RequiredTermStatus(
    val type: RequiredTermType,
    val termsId: Long,
    val latestVersion: String,
    val agreedVersion: String?,
    val needsReagreement: Boolean,
) {
    val isCurrent: Boolean
        get() = !needsReagreement && agreedVersion == latestVersion
}

data class RequiredTermsStatus(val agreements: List<RequiredTermStatus>) {
    init {
        require(agreements.map { it.type }.toSet() == RequiredTermType.entries.toSet())
        require(agreements.size == RequiredTermType.entries.size)
        require(agreements.all { it.latestVersion.isNotBlank() && it.termsId > 0 })
    }
    val pending: List<RequiredTermStatus> get() = agreements.filterNot { it.isCurrent }
    val allAgreed: Boolean get() = pending.isEmpty()

    fun hasSameVersions(other: RequiredTermsStatus): Boolean = agreements.all { term ->
        other.agreements.any { it.type == term.type && it.termsId == term.termsId && it.latestVersion == term.latestVersion }
    }
}

interface TermsRepository {
    suspend fun getStatus(): RequiredTermsStatus
    /** 서버 계약상 필수 약관 모두를 포함하며, 기존 최신 동의는 서버가 중복 저장하지 않는다. */
    suspend fun agreeRequiredTerms(agreedAt: String)
}
