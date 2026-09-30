package com.joon.ringout.presentation.termsreagreement

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import com.joon.ringout.domain.terms.RequiredTermType
import com.joon.ringout.domain.terms.RequiredTermStatus
import com.joon.ringout.domain.terms.RequiredTermsStatus
import com.joon.ringout.presentation.termsagreement.TermsAgreementScreen
import com.joon.ringout.presentation.termsreagreement.component.TermsUpdateNotice

@Composable
internal fun TermsReagreementDialog(
    state: TermsReagreementUiState,
    onContinue: () -> Unit,
    onAgreementChange: (RequiredTermType, Boolean) -> Unit,
    onAllChange: (Boolean) -> Unit,
    onDetail: (RequiredTermType) -> Unit,
    onSubmit: () -> Unit,
    onRetry: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!state.blocksService) return
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false, usePlatformDefaultWidth = false),
    ) {
        if (state.phase == TermsGatePhase.Notice) {
            Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                TermsUpdateNotice(onContinue)
            }
        } else {
            TermsAgreementScreen(
                uiState = state.toTermsAgreementUiState(),
                onAllAgreementChange = onAllChange,
                onTermAgreementChange = { id, agreed ->
                    RequiredTermType.entries.find { it.termId == id }?.let { onAgreementChange(it, agreed) }
                },
                onTermDetailClick = { id ->
                    RequiredTermType.entries.find { it.termId == id }?.let(onDetail)
                },
                onStartClick = if (state.phase == TermsGatePhase.Failure) onRetry else onSubmit,
                modifier = modifier,
                startEnabled = !state.isSubmitting && !state.isChecking && !state.isLoggingOut,
                errorMessage = state.errorMessage,
                secondaryAction = {
                    TextButton(
                        onClick = onLogout,
                        enabled = !state.isSubmitting && !state.isLoggingOut,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    ) {
                        Text(if (state.isLoggingOut) "로그아웃 중…" else "로그아웃")
                    }
                },
            )
        }
    }
}

private fun termsPreviewState() = TermsReagreementUiState(
    phase = TermsGatePhase.Consent,
    status = RequiredTermsStatus(listOf(
        RequiredTermStatus(RequiredTermType.SERVICE, 3, "2026-09-30", "2026-08-13", true),
        RequiredTermStatus(RequiredTermType.PRIVACY, 2, "2026-08-13", "2026-08-13", false),
    )),
)

@Preview(name = "약관 재동의 흐름", widthDp = 402, heightDp = 941)
@Composable
private fun TermsReagreementDialogPreview() {
    var state by remember { mutableStateOf(termsPreviewState().copy(phase = TermsGatePhase.Notice)) }
    RingoutTheme(ThemeMode.Dark) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            TermsReagreementDialog(
                state = state,
                onContinue = { state = state.copy(phase = TermsGatePhase.Consent) },
                onAgreementChange = { type, agreed -> state = state.copy(selected = if (agreed) state.selected + type else state.selected - type) },
                onAllChange = { agreed -> state = state.copy(selected = if (agreed) setOf(RequiredTermType.SERVICE) else emptySet()) },
                onDetail = {},
                onSubmit = { state = state.copy(phase = TermsGatePhase.Allowed) },
                onRetry = {},
                onLogout = { state = state.copy(phase = TermsGatePhase.Allowed) },
            )
        }
    }
}
