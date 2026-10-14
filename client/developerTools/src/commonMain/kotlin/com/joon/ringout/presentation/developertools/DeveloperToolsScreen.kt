package com.joon.ringout.presentation.developertools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import com.joon.ringout.presentation.developertools.component.BuildInfoSection

@Composable
internal fun DeveloperToolsScreen(
    uiState: DeveloperToolsUiState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            TextButton(onClick = onBack) { Text("뒤로") }
            Text("개발자 도구", style = MaterialTheme.typography.headlineSmall)
            Text(
                "현재 설치된 앱의 빌드 정보예요.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            BuildInfoSection(uiState = uiState)
            Text(
                "서버 환경은 앱에 설정된 주소를 기준으로 표시해요. 서버 연결 상태를 확인하는 기능은 아니에요.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun DeveloperToolsScreenPreview() {
    RingoutTheme(ThemeMode.Light) {
        DeveloperToolsScreen(uiState = DeveloperToolsPreviewState, onBack = {})
    }
}

@Preview(showBackground = true, widthDp = 320, heightDp = 640, fontScale = 1.5f)
@Composable
private fun DeveloperToolsScreenDarkPreview() {
    RingoutTheme(ThemeMode.Dark) {
        DeveloperToolsScreen(uiState = DeveloperToolsPreviewState, onBack = {})
    }
}
