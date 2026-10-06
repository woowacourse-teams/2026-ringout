package com.joon.ringout.domain.terms

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RequiredTermsStatusTest {
    @Test
    fun `두 필수 약관의 버전이 정확히 같아야 최신 동의로 판단한다`() {
        val status = status("2026-9-1", "2026-09-01")
        assertFalse(status.allAgreed)
        assertEquals(listOf(RequiredTermType.SERVICE), status.pending.map { it.type })
    }
    @Test
    fun `동의 기록이 없거나 서버에서 재동의가 필요하면 미동의로 판단한다`() {
        val current = status(null, "2026-09-01")
        assertFalse(current.allAgreed)
        assertFalse(status("2026-09-01", "2026-09-01", true).allAgreed)
    }
    @Test
    fun `필수 약관 누락과 중복 및 빈 최신 버전은 잘못된 응답으로 처리한다`() {
        val terms = status("2026-09-01", "2026-09-01").agreements
        assertFailsWith<IllegalArgumentException> { RequiredTermsStatus(terms.take(1)) }
        assertFailsWith<IllegalArgumentException> { RequiredTermsStatus(terms + terms.first()) }
        assertFailsWith<IllegalArgumentException> { RequiredTermsStatus(terms.map { it.copy(latestVersion = "") }) }
    }
    @Test
    fun `양쪽 최신 동의와 응답 순서에 관계없는 버전 비교를 지원한다`() {
        val current = status("2026-09-01", "2026-09-01")
        assertTrue(current.allAgreed)
        assertTrue(current.hasSameVersions(current.copy(agreements = current.agreements.reversed())))
        assertFalse(current.hasSameVersions(current.copy(agreements = current.agreements.map { it.copy(termsId = 9) })))
    }
    private fun status(agreed: String?, latest: String, needs: Boolean = false) = RequiredTermsStatus(listOf(
        RequiredTermStatus(RequiredTermType.SERVICE, 1, latest, agreed, needs),
        RequiredTermStatus(RequiredTermType.PRIVACY, 2, latest, latest, false),
    ))
}
