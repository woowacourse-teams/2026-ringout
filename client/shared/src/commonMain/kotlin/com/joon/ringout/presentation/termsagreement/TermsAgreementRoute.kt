package com.joon.ringout.presentation.termsagreement

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    var lastPendingSignup by remember(signupViewModel) {
        mutableStateOf(signupUiState.takeIf { it.hasPendingSignup })
    }
    if (isActive && signupUiState.hasPendingSignup) lastPendingSignup = signupUiState
    val completedEventId = signupUiState.completedEventId
    LaunchedEffect(signupViewModel, signupUiState.hasPendingSignup, completedEventId, isActive) {
        if (!isActive) return@LaunchedEffect
        if (completedEventId != null) {
            if (onSignupCompleted()) {
                signupViewModel.consumeCompletedEvent(completedEventId)
            }
        } else if (!signupUiState.hasPendingSignup) {
            onMissingSignup()
        }
    }
    // 회원가입 세션은 즉시 정리하되 퇴장 애니메이션이 끝날 때까지 표시 내용만 유지한다.
    val displayedSignupState = if (isActive) {
        signupUiState.takeIf { it.hasPendingSignup }
    } else {
        lastPendingSignup
    } ?: return

    val termsViewModel: TermsAgreementViewModel = viewModel { TermsAgreementViewModel() }
    val uriHandler = LocalUriHandler.current

    TermsAgreementScreen(
        uiState = termsViewModel.uiState,
        onAllAgreementChange = { agreed ->
            if (isActive) termsViewModel.setAllAgreed(agreed)
        },
        onTermAgreementChange = { termId, agreed ->
            if (isActive) termsViewModel.setTermAgreed(termId, agreed)
        },
        onTermDetailClick = { termId ->
            val policyId = when (termId) {
                TermId.Service -> PolicyId("terms")
                TermId.Privacy -> PolicyId("privacy")
                else -> null
            }
            if (isActive) {
                policyId?.let(::findPolicyUrl)?.let { url ->
                    runCatching { uriHandler.openUri(url) }
                }
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
        startEnabled = !displayedSignupState.isSaving,
        errorMessage = displayedSignupState.errorMessage,
    )
}
