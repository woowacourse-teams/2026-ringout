package com.joon.ringout.presentation.termsreagreement

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import com.joon.ringout.domain.terms.RequiredTermType
import com.joon.ringout.presentation.mypage.PolicyId
import com.joon.ringout.presentation.mypage.findPolicyUrl

/** 기존 백스택을 보존한 채 모달에서 재동의를 완료한다. */
@Composable
internal fun TermsReagreementRoute(viewModel: TermsReagreementViewModel, modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    TermsReagreementDialog(
        state = viewModel.uiState,
        onContinue = viewModel::openConsent,
        onAgreementChange = viewModel::setAgreed,
        onAllChange = viewModel::setAllAgreed,
        onDetail = { type ->
            val url = findPolicyUrl(PolicyId(if (type == RequiredTermType.SERVICE) "terms" else "privacy"))
            if (url == null || runCatching { uriHandler.openUri(url) }.isFailure) viewModel.showDetailError()
        },
        onSubmit = viewModel::submit,
        onRetry = viewModel::retry,
        onLogout = viewModel::logout,
        modifier = modifier,
    )
}
