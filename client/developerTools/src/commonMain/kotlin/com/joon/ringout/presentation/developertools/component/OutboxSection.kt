package com.joon.ringout.presentation.developertools.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import com.joon.ringout.domain.outbox.OutboxRecord

@Composable
internal fun OutboxSection(
    records: List<OutboxRecord>,
    isLoading: Boolean,
    error: String?,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Outbox", style = MaterialTheme.typography.titleMedium)
        when {
            isLoading -> Text("Outbox 데이터를 조회하고 있어요.", style = MaterialTheme.typography.bodyMedium)
            error != null -> Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            records.isEmpty() -> Text("남아 있는 Outbox 데이터가 없어요.", style = MaterialTheme.typography.bodyMedium)
            else -> {
                Text("남은 항목 ${records.size}개", style = MaterialTheme.typography.bodyMedium)
                records.forEach { record ->
                    Text("${record.source} · #${record.id}", style = MaterialTheme.typography.labelLarge)
                    Text("상태: ${record.state}", style = MaterialTheme.typography.bodyMedium)
                    SelectionContainer {
                        Text(record.details, style = MaterialTheme.typography.bodySmall)
                    }
                    HorizontalDivider()
                }
            }
        }
        TextButton(onClick = onRefresh, enabled = !isLoading) {
            Text(if (isLoading) "조회 중" else "Outbox 다시 조회")
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun OutboxSectionPreview() {
    RingoutTheme(ThemeMode.Light) {
        OutboxSection(
            records = listOf(
                OutboxRecord(
                    source = "알람 실행 이벤트",
                    id = "12",
                    state = "BLOCKED",
                    details = "이벤트: START\n시도 횟수: 3\n오류 코드: MISSING_TIME",
                ),
            ),
            isLoading = false,
            error = null,
            onRefresh = {},
        )
    }
}
