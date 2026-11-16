package com.joon.ringout.presentation.activemission.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import com.joon.ringout.presentation.common.component.ConfirmationDialog
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import ringout.shared.generated.resources.Res
import ringout.shared.generated.resources.force_end_mission_continue
import ringout.shared.generated.resources.force_end_mission_dialog_body_1
import ringout.shared.generated.resources.force_end_mission_dialog_body_2
import ringout.shared.generated.resources.force_end_mission_dialog_body_3
import ringout.shared.generated.resources.force_end_mission_dialog_body_4
import ringout.shared.generated.resources.force_end_mission_dialog_body_5
import ringout.shared.generated.resources.force_end_mission_dialog_title_1
import ringout.shared.generated.resources.force_end_mission_dialog_title_2
import ringout.shared.generated.resources.force_end_mission_dialog_title_3
import ringout.shared.generated.resources.force_end_mission_dialog_title_4
import ringout.shared.generated.resources.force_end_mission_dialog_title_5
import ringout.shared.generated.resources.force_end_mission_end
import ringout.shared.generated.resources.force_end_mission_wait

internal const val ForceEndMissionConfirmationStepCount = 5

private const val CountdownDurationNanos = 3_000_000_000L

private data class ForceEndMissionDialogCopy(
    val title: StringResource,
    val body: StringResource,
)

private val ConfirmationCopy = listOf(
    ForceEndMissionDialogCopy(
        title = Res.string.force_end_mission_dialog_title_1,
        body = Res.string.force_end_mission_dialog_body_1,
    ),
    ForceEndMissionDialogCopy(
        title = Res.string.force_end_mission_dialog_title_2,
        body = Res.string.force_end_mission_dialog_body_2,
    ),
    ForceEndMissionDialogCopy(
        title = Res.string.force_end_mission_dialog_title_3,
        body = Res.string.force_end_mission_dialog_body_3,
    ),
    ForceEndMissionDialogCopy(
        title = Res.string.force_end_mission_dialog_title_4,
        body = Res.string.force_end_mission_dialog_body_4,
    ),
    ForceEndMissionDialogCopy(
        title = Res.string.force_end_mission_dialog_title_5,
        body = Res.string.force_end_mission_dialog_body_5,
    ),
)

@Composable
internal fun ForceEndMissionConfirmationDialog(
    step: Int,
    isForceEndEnabled: Boolean,
    onDismiss: () -> Unit,
    onContinueMission: () -> Unit,
    onConfirmForceEnd: () -> Unit,
) {
    require(step in 0 until ForceEndMissionConfirmationStepCount)
    val copy = ConfirmationCopy[step]
    var remainingProgress by remember(step) { mutableFloatStateOf(1f) }
    val isCountingDown by remember(step) { derivedStateOf { remainingProgress > 0f } }
    LaunchedEffect(step) {
        val startNanos = withFrameNanos { it }
        while (remainingProgress > 0f) {
            withFrameNanos { frameNanos ->
                val remainingNanos = CountdownDurationNanos - (frameNanos - startNanos)
                remainingProgress = remainingNanos.coerceAtLeast(0L).toFloat() / CountdownDurationNanos
            }
        }
    }

    val countdown: (@Composable () -> Unit)? = if (isCountingDown) {
        {
            val waitDescription = stringResource(Res.string.force_end_mission_wait)
            CircularProgressIndicator(
                progress = { remainingProgress },
                modifier = Modifier.size(24.dp).semantics {
                    contentDescription = waitDescription
                },
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 3.dp,
            )
        }
    } else {
        null
    }

    ConfirmationDialog(
        title = stringResource(copy.title),
        description = stringResource(copy.body),
        confirmLabel = stringResource(Res.string.force_end_mission_continue),
        confirmColor = MaterialTheme.colorScheme.primary,
        cancelLabel = stringResource(Res.string.force_end_mission_end),
        onDismiss = onDismiss,
        dismissOnClickOutside = true,
        onConfirm = onContinueMission,
        cancelOnClick = onConfirmForceEnd,
        cancelEnabled = isForceEndEnabled,
        cancelReplacement = countdown,
    )
}

@Preview(name = "Force end confirmation · large text", widthDp = 402, heightDp = 941, fontScale = 1.35f)
@Composable
private fun ForceEndMissionConfirmationDialogPreview() {
    RingoutTheme(themeMode = ThemeMode.Dark) {
        ForceEndMissionConfirmationDialog(
            step = 0,
            isForceEndEnabled = true,
            onDismiss = {},
            onContinueMission = {},
            onConfirmForceEnd = {},
        )
    }
}
