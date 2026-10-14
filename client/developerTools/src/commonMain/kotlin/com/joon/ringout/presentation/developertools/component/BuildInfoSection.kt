package com.joon.ringout.presentation.developertools.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import com.joon.ringout.presentation.developertools.DeveloperToolsPreviewState
import com.joon.ringout.presentation.developertools.DeveloperToolsUiState

@Composable
internal fun BuildInfoSection(
    uiState: DeveloperToolsUiState,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf(
            "앱 버전" to uiState.versionName,
            "빌드 번호" to uiState.versionCode,
            "빌드 타입" to uiState.buildType,
            "서버 환경" to uiState.serverEnvironment,
            "서버 주소" to uiState.apiBaseUrl,
        ).forEach { (label, value) ->
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SelectionContainer {
                Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            }
            HorizontalDivider()
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun BuildInfoSectionPreview() {
    RingoutTheme(ThemeMode.Light) {
        BuildInfoSection(uiState = DeveloperToolsPreviewState)
    }
}
