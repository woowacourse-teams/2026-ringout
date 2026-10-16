package com.joon.ringout.presentation.activemission.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.joon.ringout.RingoutTheme
import com.joon.ringout.presentation.activemission.ForceEndMissionUiState
import com.joon.ringout.presentation.activemission.ForceEndPhase
import com.joon.ringout.presentation.activemission.components.ForceEndCompletedDialog
import com.joon.ringout.presentation.common.component.DialogWithoutRipple

@Composable
internal fun ForceEndMissionStatus(
    uiState: ForceEndMissionUiState,
    onConfirm: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (uiState.phase) {
        ForceEndPhase.Idle -> Unit
        ForceEndPhase.Completed -> ForceEndCompletedDialog(onConfirm = onConfirm, modifier = modifier)
        ForceEndPhase.Processing, ForceEndPhase.Failed -> DialogWithoutRipple {
            val processing = uiState.phase == ForceEndPhase.Processing
            AlertDialog(
                modifier = modifier,
                onDismissRequest = { if (!processing) onDismiss() },
                properties = DialogProperties(dismissOnBackPress = !processing, dismissOnClickOutside = false),
                title = { Text(if (processing) "미션을 종료하고 있어요" else "미션을 종료하지 못했어요") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (processing) CircularProgressIndicator(Modifier.size(28.dp))
                        Text(if (processing) "잠시만 기다려 주세요." else "다시 시도해 주세요.")
                    }
                },
                confirmButton = {
                    if (!processing) TextButton(onClick = onRetry) { Text("다시 시도") }
                },
                dismissButton = {
                    if (!processing) TextButton(onClick = onDismiss) { Text("닫기") }
                },
            )
        }
    }
}

@Preview
@Composable
private fun ForceEndMissionStatusPreview() {
    RingoutTheme {
        ForceEndMissionStatus(ForceEndMissionUiState("preview", ForceEndPhase.Completed), {}, {}, {})
    }
}

@Preview
@Composable
private fun ForceEndMissionStatusProcessingPreview() {
    RingoutTheme {
        ForceEndMissionStatus(ForceEndMissionUiState("preview", ForceEndPhase.Processing), {}, {}, {})
    }
}

@Preview
@Composable
private fun ForceEndMissionStatusFailedPreview() {
    RingoutTheme {
        ForceEndMissionStatus(ForceEndMissionUiState("preview", ForceEndPhase.Failed), {}, {}, {})
    }
}
