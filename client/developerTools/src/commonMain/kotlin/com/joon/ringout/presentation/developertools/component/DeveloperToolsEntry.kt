package com.joon.ringout.presentation.developertools.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode

@Composable
internal fun DeveloperToolsEntry(onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Text("개발자 도구")
    }
}

@Preview(showBackground = true)
@Composable
private fun DeveloperToolsEntryPreview() {
    RingoutTheme(ThemeMode.Dark) { DeveloperToolsEntry(onClick = {}) }
}
