package com.joon.ringout.presentation.activemission.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
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

internal const val ForceEndMissionConfirmationStepCount = 5

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
