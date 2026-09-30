package com.joon.ringout.presentation.termsreagreement

import com.joon.ringout.domain.terms.RequiredTermStatus
import com.joon.ringout.domain.terms.RequiredTermType
import com.joon.ringout.domain.terms.RequiredTermsStatus
import com.joon.ringout.presentation.termsagreement.TermId
import com.joon.ringout.presentation.termsagreement.TermsAgreementViewModel
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TermsReagreementScreenStateTest {
    @Test
    fun `기존에 동의한 약관은 선택 없이 완료로 표시하고 재동의할 약관만 제출을 막는다`() {
        val state = state()
        val screen = state.toTermsAgreementUiState()

        assertFalse(screen.terms.first { it.id == TermId.Service }.isReadOnly)
        assertTrue(screen.terms.first { it.id == TermId.Privacy }.isReadOnly)
        assertTrue(screen.terms.first { it.id == TermId.Privacy }.isAgreed)
        assertFalse(screen.canStart)

        val selected = state.copy(selected = setOf(RequiredTermType.SERVICE)).toTermsAgreementUiState()
        assertTrue(selected.canStart)
        assertTrue(selected.isAllAgreed)
    }

    @Test
    fun `공통 동의 상태에서 전체 선택을 해제해도 이미 완료된 동의는 유지한다`() {
        val terms = state().toTermsAgreementUiState().terms
        val viewModel = TermsAgreementViewModel(terms)
        viewModel.setAllAgreed(true)
        viewModel.setAllAgreed(false)
        viewModel.setTermAgreed(TermId.Privacy, false)

        assertFalse(viewModel.uiState.terms.first { it.id == TermId.Service }.isAgreed)
        assertTrue(viewModel.uiState.terms.first { it.id == TermId.Privacy }.isAgreed)
        assertFalse(viewModel.uiState.canStart)
    }

    private fun state() = TermsReagreementUiState(
        phase = TermsGatePhase.Consent,
        status = RequiredTermsStatus(listOf(
            RequiredTermStatus(RequiredTermType.SERVICE, 3, "2026-09-30", "2026-08-13", true),
            RequiredTermStatus(RequiredTermType.PRIVACY, 2, "2026-08-13", "2026-08-13", false),
        )),
    )
}
