package com.joon.ringout.presentation.home.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.joon.ringout.RingoutTheme
import com.joon.ringout.presentation.common.component.ConfirmationDialog

@Composable
internal fun AlarmDeleteDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ConfirmationDialog(
        title = "알람 삭제",
        description = "정말 해당 알람을 삭제하시겠습니까?",
        confirmLabel = "삭제",
        confirmColor = MaterialTheme.colorScheme.primary,
        onDismiss = onDismiss,
        onConfirm = onConfirm,
        modifier = modifier,
    )
}

@Preview
@Composable
private fun AlarmDeleteDialogPreview() {
    RingoutTheme {
        AlarmDeleteDialog(onDismiss = {}, onConfirm = {})
    }
}
