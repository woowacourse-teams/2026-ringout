package com.joon.ringout.presentation.developertools.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
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

/** Step 1 iOS entry verification; platform build information is connected in step 2. */
@Composable
internal fun DeveloperToolsPendingContent(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.safeDrawingPadding().padding(20.dp)) {
            TextButton(onClick = onBack) { Text("뒤로") }
            Text("개발자 도구", style = MaterialTheme.typography.headlineSmall)
            Text("빌드 정보는 다음 단계에서 연결합니다.", modifier = Modifier.padding(top = 20.dp))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DeveloperToolsPendingContentPreview() {
    RingoutTheme(ThemeMode.Light) { DeveloperToolsPendingContent(onBack = {}) }
}
