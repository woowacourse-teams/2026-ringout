package com.joon.ringout.presentation.records.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
    showLoadingIndicator: Boolean = false,
    loadingDescription: String = "알람 울림 횟수 불러오는 중",
    errorMessage: String? = null,
    onRetry: () -> Unit = {},
) {
    val ringing = when {
        errorMessage != null -> "—"
        summary.ringingCount != null -> if (summary.observedRingingOnly) "확인된 ${summary.ringingCount}번" else "${summary.ringingCount}번"
        isLoading -> null
        else -> "집계 정보 없음"
    }
    Column(modifier = modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "알람이 울린 횟수",
                modifier = Modifier.weight(1f),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = recordsColors().text,
            )
            if (isLoading && showLoadingIndicator && errorMessage == null) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp).semantics { contentDescription = loadingDescription },
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (ringing != null) {
                Text(ringing, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
        }
        if (errorMessage != null) {
            Text(errorMessage, style = MaterialTheme.typography.bodySmall, color = recordsColors().text)
            TextButton(onClick = onRetry) { Text("다시 시도") }
        } else if (summary.observedRingingOnly && summary.ringingCount != null) {
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

@Preview
@Composable
private fun RecordsSummaryShortLoadingPreview() {
    RingoutTheme { RecordsSummary(AlarmActivitySummary(), isLoading = true) }
}

@Preview
@Composable
private fun RecordsSummaryLoadingPreview() {
    RingoutTheme { RecordsSummary(AlarmActivitySummary(), isLoading = true, showLoadingIndicator = true) }
}

@Preview
@Composable
private fun RecordsSummaryRefreshingPreview() {
    RingoutTheme { RecordsSummary(AlarmActivitySummary(ringingCount = 5), isLoading = true, showLoadingIndicator = true) }
}
