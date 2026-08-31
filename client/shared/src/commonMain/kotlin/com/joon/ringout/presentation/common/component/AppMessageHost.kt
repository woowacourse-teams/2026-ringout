package com.joon.ringout.presentation.common.component

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode

data class AppMessageHostState(
    val title: String,
    val message: String,
)

@Composable
fun AppMessageHost(
    state: AppMessageHostState?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val messageState = state ?: return

    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        title = { Text(messageState.title) },
        text = { Text(messageState.message) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("확인")
            }
        },
    )
}

@Preview(name = "App message")
@Composable
private fun AppMessageHostPreview() {
    RingoutTheme(themeMode = ThemeMode.Light) {
        AppMessageHost(
            state = AppMessageHostState(
                title = "알람을 처리할 수 없습니다",
                message = "알람을 저장하지 못했어요. 다시 시도해 주세요.",
            ),
            onDismiss = {},
        )
    }
}
