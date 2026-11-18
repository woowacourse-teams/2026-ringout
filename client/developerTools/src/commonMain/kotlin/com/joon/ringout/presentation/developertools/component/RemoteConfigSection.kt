package com.joon.ringout.presentation.developertools.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.joon.ringout.presentation.developertools.RemoteConfigUiState

@Composable
internal fun RemoteConfigSection(
    state: RemoteConfigUiState,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("원격 설정", style = MaterialTheme.typography.titleMedium)
        listOf(
            "최소 지원 버전 코드" to state.minVersionCode,
            "값의 출처" to state.valueSource,
            "마지막 가져오기 성공 시각" to state.lastFetchTime,
            "가져오기 상태" to state.fetchStatus,
            "실시간 리스너 오류" to state.listenerStatus,
        ).forEach { (label, value) ->
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        }
        TextButton(onClick = onRefresh, enabled = !state.isRefreshing) {
            Text(if (state.isRefreshing) "가져오는 중" else "설정 다시 가져오기")
        }
        HorizontalDivider()
    }
}

@Preview(showBackground = true)
@Composable
private fun RemoteConfigSectionPreview() {
    RingoutTheme(ThemeMode.Light) {
        RemoteConfigSection(
            state = RemoteConfigUiState(
                minVersionCode = "1",
                valueSource = "앱 기본값",
                lastFetchTime = "가져온 적 없음",
                fetchStatus = "가져오기 전",
                listenerStatus = "없음",
                isRefreshing = false,
            ),
            onRefresh = {},
        )
    }
}
