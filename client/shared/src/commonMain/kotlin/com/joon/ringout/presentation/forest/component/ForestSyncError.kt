package com.joon.ringout.presentation.forest.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joon.ringout.RingoutTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ForestSyncError(
    message: String,
    isRetrying: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CompositionLocalProvider(LocalRippleConfiguration provides null) {
        Surface(modifier.semantics { liveRegion = LiveRegionMode.Polite }, tonalElevation = 3.dp) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(message, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = onRetry, enabled = !isRetrying) {
                    Text(if (isRetrying) "재시도 중…" else "다시 시도")
                }
            }
        }
    }
}

@Preview
@Composable
private fun ForestSyncErrorPreview() {
    RingoutTheme { ForestSyncError("나무 확인 기록을 서버에 저장하지 못했어요.", false, {}) }
}
