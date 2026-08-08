package com.joon.ringout.presentation.termsagreement

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TermsAgreementStateTest {
    @Test
    fun startsWithEveryAgreementUnchecked() {
        val viewModel = TermsAgreementViewModel()

        assertTrue(viewModel.uiState.terms.none(TermAgreementItem::isAgreed))
        assertFalse(viewModel.uiState.isAllAgreed)
        assertFalse(viewModel.uiState.canStart)
    }

    @Test
    fun agreeingToAllChecksEveryTermAndAllowsStart() {
        val viewModel = TermsAgreementViewModel()

        viewModel.setAllAgreed(true)

        assertTrue(viewModel.uiState.terms.all(TermAgreementItem::isAgreed))
        assertTrue(viewModel.uiState.isAllAgreed)
        assertTrue(viewModel.uiState.canStart)
    }

    @Test
    fun withdrawingAllUnchecksEveryTerm() {
        val viewModel = TermsAgreementViewModel()
        viewModel.setAllAgreed(true)

        viewModel.setAllAgreed(false)

        assertTrue(viewModel.uiState.terms.none(TermAgreementItem::isAgreed))
        assertFalse(viewModel.uiState.isAllAgreed)
    }

    @Test
    fun agreeingToEveryIndividualTermChecksAllAgreement() {
        val viewModel = TermsAgreementViewModel()

        defaultTerms.forEach { term -> viewModel.setTermAgreed(term.id, true) }

        assertTrue(viewModel.uiState.isAllAgreed)
    }

    @Test
    fun withdrawingOneTermUnchecksAllAgreement() {
        val viewModel = TermsAgreementViewModel()
        viewModel.setAllAgreed(true)

        viewModel.setTermAgreed(TermId.Service, false)

        assertFalse(viewModel.uiState.isAllAgreed)
        assertTrue(viewModel.uiState.terms.first { it.id == TermId.Privacy }.isAgreed)
    }

    @Test
    fun blocksStartWhileARequiredTermIsMissing() {
        val viewModel = TermsAgreementViewModel()
        viewModel.setTermAgreed(TermId.Service, true)

        assertEquals(
            TermsAgreementAdvance.BlockedMissingRequiredAgreement,
            viewModel.requestStart(),
        )
    }

    @Test
    fun optionalTermDoesNotBlockStart() {
        val terms = listOf(
            TermAgreementItem(TermId.Service, "필수 약관", isRequired = true),
            TermAgreementItem(TermId.Privacy, "선택 약관", isRequired = false),
        )
        val viewModel = TermsAgreementViewModel(initialTerms = terms)

        viewModel.setTermAgreed(TermId.Service, true)

        assertTrue(viewModel.uiState.canStart)
        assertFalse(viewModel.uiState.isAllAgreed)
        assertEquals(TermsAgreementAdvance.Complete, viewModel.requestStart())
    }

    @Test
    fun allAgreementIncludesOptionalTerms() {
        val terms = listOf(
            TermAgreementItem(TermId.Service, "필수 약관", isRequired = true),
            TermAgreementItem(TermId.Privacy, "선택 약관", isRequired = false),
        )
        val viewModel = TermsAgreementViewModel(initialTerms = terms)

        viewModel.setAllAgreed(true)

        assertTrue(viewModel.uiState.terms.all(TermAgreementItem::isAgreed))
    }

    @Test
    fun updatingAnIdThatIsNotInTheCurrentListDoesNotChangeState() {
        val terms = listOf(
            TermAgreementItem(TermId.Service, "필수 약관", isRequired = true),
        )
        val viewModel = TermsAgreementViewModel(initialTerms = terms)

        viewModel.setTermAgreed(TermId.Privacy, true)

        assertEquals(terms, viewModel.uiState.terms)
    }
}
