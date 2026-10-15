package com.joon.ringout.presentation.records.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.presentation.records.recordsColors

@Composable
internal fun RecordsMonthStatus(
    isLoading: Boolean,
    errorMessage: String?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val textStyle = MaterialTheme.typography.bodyMedium
    val messageHeight = with(LocalDensity.current) { textStyle.lineHeight.toDp() * 2 }
    val actionHeight = with(LocalDensity.current) {
        (MaterialTheme.typography.labelLarge.lineHeight.toDp() + 16.dp).coerceAtLeast(48.dp)
    }
    // Keep both slots even after loading completes so the centered dialog never changes height.
    Column(
        modifier = modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(messageHeight), contentAlignment = Alignment.Center) {
            val message = if (isLoading) "기록을 불러오는 중이에요" else errorMessage
            if (message != null) {
                Text(
                    text = message,
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    color = recordsColors().secondaryText,
                    style = textStyle,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(actionHeight), contentAlignment = Alignment.Center) {
            when {
                isLoading -> CircularProgressIndicator(Modifier.size(28.dp))
                errorMessage != null -> TextButton(onClick = onRetry) { Text("다시 시도") }
            }
        }
    }
}

@Preview
@Composable
private fun RecordsMonthStatusPreview() {
    RingoutTheme { RecordsMonthStatus(false, null, {}) }
}

@Preview
@Composable
private fun RecordsMonthStatusLoadingPreview() {
    RingoutTheme { RecordsMonthStatus(true, null, {}) }
}

@Preview
@Composable
private fun RecordsMonthStatusErrorPreview() {
    RingoutTheme { RecordsMonthStatus(false, "기록을 불러오지 못했어요", {}) }
}
