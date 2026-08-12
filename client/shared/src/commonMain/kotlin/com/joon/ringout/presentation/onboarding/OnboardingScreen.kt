package com.joon.ringout.presentation.onboarding

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import com.joon.ringout.presentation.onboarding.component.OnboardingPage
import com.joon.ringout.presentation.onboarding.component.OnboardingPageIndicator
import com.joon.ringout.presentation.onboarding.component.OnboardingPrimaryButton

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
    completionEnabled: Boolean = true,
    completionRetryToken: Int = 0,
    pages: List<OnboardingPageContent> = defaultOnboardingPages,
    viewModel: OnboardingViewModel = viewModel { OnboardingViewModel() },
) {
    OnboardingScreenContent(
        pages = pages,
        currentPageIndex = viewModel.uiState.currentPageIndex,
        onNext = {
            when (viewModel.requestNext(pages.size, completionRetryToken)) {
                OnboardingAdvance.Complete -> onComplete()
                OnboardingAdvance.IgnoredAlreadyComplete,
                is OnboardingAdvance.MoveTo,
                -> Unit
            }
        },
        completionEnabled = completionEnabled,
        modifier = modifier,
    )
}

@Composable
internal fun OnboardingScreenContent(
    pages: List<OnboardingPageContent>,
    currentPageIndex: Int,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    completionEnabled: Boolean = true,
) {
    require(pages.isNotEmpty()) { "Onboarding requires at least one page." }
    require(currentPageIndex in pages.indices) {
        "Current onboarding page must exist in the supplied pages."
    }
    val isLastPage = currentPageIndex == pages.lastIndex

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
            contentAlignment = Alignment.Center,
        ) {
            val horizontalPadding = when {
                maxWidth <= ExtraCompactScreenWidth -> ExtraCompactHorizontalPadding
                maxWidth <= CompactScreenWidth -> CompactHorizontalPadding
                else -> DefaultHorizontalPadding
            }

            Column(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .padding(
                        start = horizontalPadding,
                        top = 12.dp,
                        end = horizontalPadding,
                        bottom = 16.dp,
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                OnboardingPage(
                    content = pages[currentPageIndex],
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(22.dp))
                OnboardingPageIndicator(
                    pageCount = pages.size,
                    selectedPage = currentPageIndex,
                )
                Spacer(modifier = Modifier.height(24.dp))
                OnboardingPrimaryButton(
                    label = if (isLastPage) "시작하기" else "다음으로",
                    onClick = onNext,
                    enabled = !isLastPage || completionEnabled,
                )
            }
        }
    }
}

private val ExtraCompactScreenWidth = 320.dp
private val CompactScreenWidth = 360.dp
private val ExtraCompactHorizontalPadding = 12.dp
private val CompactHorizontalPadding = 16.dp
private val DefaultHorizontalPadding = 24.dp

@Preview(name = "Dark onboarding first", widthDp = 402, heightDp = 941)
@Composable
private fun DarkOnboardingFirstPagePreview() {
    OnboardingScreenPreview(themeMode = ThemeMode.Dark, pageIndex = 0)
}

@Preview(name = "Light onboarding first", widthDp = 402, heightDp = 941)
@Composable
private fun LightOnboardingFirstPagePreview() {
    OnboardingScreenPreview(themeMode = ThemeMode.Light, pageIndex = 0)
}

@Preview(name = "Dark onboarding second", widthDp = 402, heightDp = 941)
@Composable
private fun DarkOnboardingSecondPagePreview() {
    OnboardingScreenPreview(themeMode = ThemeMode.Dark, pageIndex = 1)
}

@Preview(name = "Light onboarding second", widthDp = 402, heightDp = 941)
@Composable
private fun LightOnboardingSecondPagePreview() {
    OnboardingScreenPreview(themeMode = ThemeMode.Light, pageIndex = 1)
}

@Preview(name = "Dark onboarding third", widthDp = 402, heightDp = 941)
@Composable
private fun DarkOnboardingThirdPagePreview() {
    OnboardingScreenPreview(themeMode = ThemeMode.Dark, pageIndex = 2)
}

@Preview(name = "Light onboarding third", widthDp = 402, heightDp = 941)
@Composable
private fun LightOnboardingThirdPagePreview() {
    OnboardingScreenPreview(themeMode = ThemeMode.Light, pageIndex = 2)
}

@Preview(name = "Dark onboarding last", widthDp = 402, heightDp = 941)
@Composable
private fun DarkOnboardingLastPagePreview() {
    OnboardingScreenPreview(
        themeMode = ThemeMode.Dark,
        pageIndex = defaultOnboardingPages.lastIndex,
    )
}

@Preview(name = "Light onboarding last", widthDp = 402, heightDp = 941)
@Composable
private fun LightOnboardingLastPagePreview() {
    OnboardingScreenPreview(
        themeMode = ThemeMode.Light,
        pageIndex = defaultOnboardingPages.lastIndex,
    )
}

@Preview(name = "Small onboarding", widthDp = 360, heightDp = 800)
@Composable
private fun SmallOnboardingScreenPreview() {
    OnboardingScreenPreview(themeMode = ThemeMode.Dark, pageIndex = 0)
}

@Preview(name = "Extra compact onboarding", widthDp = 320, heightDp = 700)
@Composable
private fun ExtraCompactOnboardingScreenPreview() {
    OnboardingScreenPreview(
        themeMode = ThemeMode.Dark,
        pageIndex = defaultOnboardingPages.lastIndex,
    )
}

@Preview(
    name = "Onboarding accessibility text",
    widthDp = 360,
    heightDp = 800,
    fontScale = 1.3f,
)
@Composable
private fun OnboardingAccessibilityTextPreview() {
    OnboardingScreenPreview(
        themeMode = ThemeMode.Light,
        pageIndex = defaultOnboardingPages.lastIndex,
    )
}

@Preview(name = "Large onboarding", widthDp = 430, heightDp = 932)
@Composable
private fun LargeOnboardingScreenPreview() {
    OnboardingScreenPreview(
        themeMode = ThemeMode.Light,
        pageIndex = defaultOnboardingPages.lastIndex,
    )
}

@Composable
private fun OnboardingScreenPreview(
    themeMode: ThemeMode,
    pageIndex: Int,
) {
    RingoutTheme(themeMode = themeMode) {
        OnboardingScreenContent(
            pages = defaultOnboardingPages,
            currentPageIndex = pageIndex,
            onNext = {},
        )
    }
}
