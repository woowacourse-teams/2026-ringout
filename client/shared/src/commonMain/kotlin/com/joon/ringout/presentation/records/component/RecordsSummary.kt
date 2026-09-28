package com.joon.ringout.presentation.records.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.domain.alarmactivity.AlarmActivitySummary
import com.joon.ringout.presentation.records.recordsColors

@Composable
internal fun RecordsSummary(
    summary: AlarmActivitySummary,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    onRetry: () -> Unit = {},
) {
    val unavailable = when {
        isLoading -> "불러오는 중"
        errorMessage != null -> "—"
        else -> "집계 정보 없음"
    }
    val ringing = if (isLoading || errorMessage != null) unavailable else summary.ringingCount?.let {
        if (summary.observedRingingOnly) "확인된 ${it}번" else "${it}번"
    } ?: unavailable
    Column(modifier = modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "알람이 울린 횟수",
                modifier = Modifier.weight(1f),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = recordsColors().text,
            )
            Text(ringing, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
        if (errorMessage != null) {
            Text(errorMessage, style = MaterialTheme.typography.bodySmall, color = recordsColors().text)
            TextButton(onClick = onRetry) { Text("다시 시도") }
        } else if (!isLoading && summary.observedRingingOnly && summary.ringingCount != null) {
            Text(
                "울림은 앱에서 확인한 날짜 기준이에요. 앱이 실행되지 않는 동안의 울림은 누락될 수 있어요.",
                style = MaterialTheme.typography.bodySmall,
                color = recordsColors().secondaryText,
            )
        }
    }
}

@Preview
@Composable
private fun RecordsSummaryPreview() {
    RingoutTheme { RecordsSummary(AlarmActivitySummary(ringingCount = 5)) }
}

@Preview
@Composable
private fun RecordsSummaryUnavailablePreview() {
    RingoutTheme { RecordsSummary(AlarmActivitySummary()) }
}

@Preview
@Composable
private fun RecordsSummaryObservedPreview() {
    RingoutTheme { RecordsSummary(AlarmActivitySummary(ringingCount = 2, observedRingingOnly = true)) }
}
