package com.joon.ringout.presentation.onboarding.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode

@Composable
fun OnboardingPageIndicator(
    pageCount: Int,
    selectedPage: Int,
    modifier: Modifier = Modifier,
) {
    require(pageCount > 0) { "Page indicator requires at least one page." }
    require(selectedPage in 0 until pageCount) { "Selected page must be within page count." }

    Row(
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = "현재 페이지 ${selectedPage + 1}, 전체 $pageCount"
        },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(pageCount) { pageIndex ->
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        color = if (pageIndex == selectedPage) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                        shape = CircleShape,
                    ),
            )
        }
    }
}

@Preview(name = "Dark page indicator")
@Composable
private fun DarkOnboardingPageIndicatorPreview() {
    RingoutTheme(themeMode = ThemeMode.Dark) {
        OnboardingPageIndicator(pageCount = 4, selectedPage = 0)
    }
}

@Preview(name = "Light page indicator")
@Composable
private fun LightOnboardingPageIndicatorPreview() {
    RingoutTheme(themeMode = ThemeMode.Light) {
        OnboardingPageIndicator(pageCount = 4, selectedPage = 3)
    }
}
