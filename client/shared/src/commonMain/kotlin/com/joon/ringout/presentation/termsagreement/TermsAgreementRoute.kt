package com.joon.ringout.presentation.termsagreement

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.viewmodel.compose.viewModel
import com.joon.ringout.presentation.mypage.PolicyId
import com.joon.ringout.presentation.mypage.findPolicyUrl
import com.joon.ringout.presentation.signup.SignupViewModel

@Composable
internal fun TermsAgreementRoute(
    signupViewModel: SignupViewModel,
    isActive: Boolean,
    onMissingSignup: () -> Unit,
    onSignupCompleted: () -> Boolean,
    modifier: Modifier = Modifier,
) {
    val signupUiState = signupViewModel.uiState
    val completedEventId = signupUiState.completedEventId
    LaunchedEffect(signupUiState.hasPendingSignup, completedEventId, isActive) {
        if (!isActive) return@LaunchedEffect
        if (completedEventId != null) {
            if (onSignupCompleted()) {
                signupViewModel.consumeCompletedEvent(completedEventId)
            }
        } else if (!signupUiState.hasPendingSignup) {
            onMissingSignup()
        }
    }
    if (!signupUiState.hasPendingSignup) return

    val termsViewModel: TermsAgreementViewModel = viewModel { TermsAgreementViewModel() }
    val uriHandler = LocalUriHandler.current

    TermsAgreementScreen(
        uiState = termsViewModel.uiState,
        onAllAgreementChange = termsViewModel::setAllAgreed,
        onTermAgreementChange = termsViewModel::setTermAgreed,
        onTermDetailClick = { termId ->
            val policyId = when (termId) {
                TermId.Service -> PolicyId("terms")
                TermId.Privacy -> PolicyId("privacy")
                else -> null
            }
            policyId?.let(::findPolicyUrl)?.let { url ->
                runCatching { uriHandler.openUri(url) }
            }
        },
        onStartClick = {
            if (
                isActive &&
                termsViewModel.requestStart() == TermsAgreementAdvance.Complete
            ) {
                signupViewModel.signup(
                    termsViewModel.uiState.terms
                        .filter(TermAgreementItem::isAgreed)
                        .mapTo(mutableSetOf(), TermAgreementItem::id),
                )
            }
        },
        modifier = modifier,
        startEnabled = !signupUiState.isSaving,
        errorMessage = signupUiState.errorMessage,
    )
}
