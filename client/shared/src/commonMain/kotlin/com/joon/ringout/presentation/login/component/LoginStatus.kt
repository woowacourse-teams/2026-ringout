package com.joon.ringout.presentation.login.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode

@Composable
internal fun LoginStatus(
    isLoading: Boolean,
    errorMessage: String?,
    modifier: Modifier = Modifier,
) {
    val message = when {
        isLoading -> "Google 계정을 확인하고 있어요."
        errorMessage != null -> errorMessage
        else -> return
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.padding(2.dp),
                strokeWidth = 2.dp,
            )
        }
        Text(
            text = message,
            color = if (errorMessage == null) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.error
            },
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun LoginStatusLoadingPreview() {
    RingoutTheme(ThemeMode.Light) {
        LoginStatus(isLoading = true, errorMessage = null)
    }
}

@Preview(showBackground = true)
@Composable
private fun LoginStatusErrorPreview() {
    RingoutTheme(ThemeMode.Dark) {
        LoginStatus(isLoading = false, errorMessage = "로그인하지 못했어요.")
    }
}
