package com.joon.ringout.presentation.forest.component

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
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
import com.joon.ringout.presentation.forest.ForestUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ForestRefreshStatus(state: ForestUiState, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    CompositionLocalProvider(LocalRippleConfiguration provides null) {
        Surface(modifier.semantics { liveRegion = LiveRegionMode.Polite }, tonalElevation = 3.dp) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (state.isRefreshing) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("숲을 새로 불러오고 있어요.", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text(state.refreshErrorMessage.orEmpty(), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = onRetry) { Text("다시 시도") }
                }
            }
        }
    }
}

@Preview
@Composable
private fun ForestRefreshStatusPreview() {
    RingoutTheme { ForestRefreshStatus(ForestUiState(isLoading = false, isRefreshing = true), {}) }
}

@Preview
@Composable
private fun ForestRefreshStatusErrorPreview() {
    RingoutTheme { ForestRefreshStatus(ForestUiState(isLoading = false, refreshErrorMessage = "숲을 새로 불러오지 못했어요."), {}) }
}
