package com.joon.ringout.presentation.activemission.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.presentation.destination.PlatformBackHandler

@Composable
internal fun AlarmCompletionWaiting(
    onBackground: () -> Unit,
    onReturnToRinging: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    PlatformBackHandler(onBack = onBackground)
    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            Text(
                text = "진행 중인 알람과 미션을 마친 뒤\n앱을 업데이트해 주세요.",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge,
            )
            if (onReturnToRinging != null) {
                TextButton(onClick = onReturnToRinging) { Text("울리는 알람으로 이동") }
            } else {
                CircularProgressIndicator()
            }
        }
    }
}

@Preview
@Composable
private fun AlarmCompletionWaitingPreview() {
    RingoutTheme { AlarmCompletionWaiting({}, {}) }
}

@Preview
@Composable
private fun AlarmCompletionWaitingRetryPreview() {
    RingoutTheme { AlarmCompletionWaiting({}, null) }
}
