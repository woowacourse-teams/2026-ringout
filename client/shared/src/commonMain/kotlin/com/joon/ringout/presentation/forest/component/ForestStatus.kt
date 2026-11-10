package com.joon.ringout.presentation.forest.component

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.presentation.forest.ForestUiState

@Composable
internal fun ForestStatus(state: ForestUiState, onRetry: () -> Unit, onBackClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            if (state.errorMessage == null) {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
                Text("숲을 불러오고 있어요.")
            } else {
                Text(state.errorMessage)
                Spacer(Modifier.height(16.dp))
                Button(onClick = onRetry) { Text("다시 시도") }
            }
            TextButton(onClick = onBackClick) { Text("홈으로") }
        }
    }
}

@Preview
@Composable
private fun ForestStatusPreview() { RingoutTheme { ForestStatus(ForestUiState(), {}, {}) } }
