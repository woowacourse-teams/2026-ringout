package com.joon.ringout.presentation.termsagreement.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import com.joon.ringout.ringoutColors

@Composable
fun TermsStartButton(
    enabled: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(58.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.ringoutColors.primaryActionContent,
            disabledContainerColor = MaterialTheme.ringoutColors.elevatedSurface,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Preview(name = "Start button disabled", widthDp = 360)
@Composable
private fun TermsStartButtonDisabledPreview() {
    RingoutTheme(themeMode = ThemeMode.Light) {
        TermsStartButton(enabled = false, onClick = {}, label = "시작하기")
    }
}

@Preview(name = "Start button enabled", widthDp = 360)
@Composable
private fun TermsStartButtonEnabledPreview() {
    RingoutTheme(themeMode = ThemeMode.Dark) {
        TermsStartButton(enabled = true, onClick = {}, label = "시작하기")
    }
}
