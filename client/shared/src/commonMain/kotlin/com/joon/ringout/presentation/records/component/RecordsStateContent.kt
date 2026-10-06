package com.joon.ringout.presentation.records.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.presentation.records.recordsColors

@Composable
internal fun RecordsStateContent(
    isLoading: Boolean,
    errorMessage: String?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 32.dp).semantics { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (isLoading) CircularProgressIndicator(modifier = Modifier.size(28.dp))
        Text(
            when {
                isLoading -> "기록을 불러오는 중이에요"
                errorMessage != null -> errorMessage
                else -> "이 날짜에는 알람 사용 기록이 없어요"
            },
            color = recordsColors().secondaryText,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        if (!isLoading && errorMessage != null) TextButton(onClick = onRetry) { Text("다시 시도") }
    }
}

@Preview
@Composable
private fun RecordsStateContentPreview() {
    RingoutTheme { RecordsStateContent(false, null, {}) }
}

@Preview
@Composable
private fun RecordsStateContentErrorPreview() {
    RingoutTheme { RecordsStateContent(false, "기록을 불러오지 못했어요", {}) }
}
