package com.joon.ringout.presentation.onboarding.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import com.joon.ringout.ringoutColors

@Composable
fun OnboardingPrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.ringoutColors.primaryActionContent,
        ),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Preview(name = "Dark primary button", widthDp = 354)
@Composable
private fun DarkOnboardingPrimaryButtonPreview() {
    RingoutTheme(themeMode = ThemeMode.Dark) {
        OnboardingPrimaryButton(label = "다음으로", onClick = {})
    }
}

@Preview(name = "Light primary button", widthDp = 354)
@Composable
private fun LightOnboardingPrimaryButtonPreview() {
    RingoutTheme(themeMode = ThemeMode.Light) {
        OnboardingPrimaryButton(label = "다음으로", onClick = {})
    }
}
