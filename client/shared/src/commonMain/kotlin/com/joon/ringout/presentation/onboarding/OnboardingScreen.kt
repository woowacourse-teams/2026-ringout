package com.joon.ringout.presentation.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import com.joon.ringout.presentation.onboarding.component.OnboardingPage
import com.joon.ringout.presentation.onboarding.component.OnboardingPageIndicator
import com.joon.ringout.presentation.onboarding.component.OnboardingPrimaryButton
import com.joon.ringout.ringoutColors
import kotlinx.coroutines.flow.drop

@Composable
internal fun OnboardingScreen(
    uiState: OnboardingUiState,
    onPageChanged: (Int) -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    completionEnabled: Boolean = true,
    completionFailed: Boolean = false,
) {
    val pagerState = rememberPagerState(initialPage = uiState.currentStepIndex) { uiState.steps.size }
    val currentOnPageChanged by rememberUpdatedState(onPageChanged)
    val currentPageIndex by rememberUpdatedState(uiState.currentStepIndex)
    var isSynchronizingPage by remember { mutableStateOf(false) }
    val busy = !completionEnabled || (uiState.isCompletionRequested && !completionFailed)

    fun isRequestedPageSettled(): Boolean =
        !pagerState.isScrollInProgress && pagerState.settledPage == uiState.currentStepIndex

    LaunchedEffect(uiState.currentStepIndex) {
        if (pagerState.settledPage != uiState.currentStepIndex || pagerState.isScrollInProgress) {
            isSynchronizingPage = true
            pagerState.animateScrollToPage(uiState.currentStepIndex)
        }
        // Keep the guard across cancellation when another external page request arrives.
        isSynchronizingPage = false
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .drop(1)
            .collect { page ->
                if (!isSynchronizingPage && page != currentPageIndex) currentOnPageChanged(page)
            }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.ringoutColors.mainScreenBackground)
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 560.dp).fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f).fillMaxWidth().testTag("onboarding-pager"),
                userScrollEnabled = !busy && !uiState.isCompletionRequested && !isSynchronizingPage,
                key = { uiState.steps[it].name },
            ) { page ->
                OnboardingPage(step = uiState.steps[page])
            }
            OnboardingPageIndicator(
                pageCount = uiState.steps.size,
                selectedPage = uiState.currentStepIndex,
                modifier = Modifier.padding(vertical = 10.dp),
            )
            if (completionFailed) {
                Text(
                    text = "시작하지 못했어요. 다시 시도해주세요.",
                    modifier = Modifier
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }
            Box(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                OnboardingPrimaryButton(
                    label = when {
                        busy -> "시작하는 중…"
                        completionFailed -> "다시 시도"
                        uiState.isLastStep && isRequestedPageSettled() -> "시작하기"
                        else -> "다음으로"
                    },
                    onClick = {
                        // Read the live pager state so a tap during the final transition cannot submit.
                        if (!uiState.isLastStep || isRequestedPageSettled()) onNext()
                    },
                    enabled = !busy,
                    modifier = Modifier.widthIn(max = 343.dp),
                )
            }
        }
    }
}

@Preview(name = "Introduction dark", widthDp = 402, heightDp = 941)
@Preview(name = "Introduction compact", widthDp = 320, heightDp = 568, fontScale = 2f)
@Composable
private fun OnboardingScreenPreview() = OnboardingPreview(OnboardingStep.Introduction)

@Preview(name = "Introduction light", widthDp = 402, heightDp = 941)
@Composable
private fun OnboardingScreenLightPreview() = OnboardingPreview(OnboardingStep.Introduction, ThemeMode.Light)

@Preview(name = "Destination dark", widthDp = 402, heightDp = 941)
@Composable
private fun OnboardingDestinationPreview() = OnboardingPreview(OnboardingStep.Destination)

@Preview(name = "Destination light", widthDp = 402, heightDp = 941)
@Composable
private fun OnboardingDestinationLightPreview() = OnboardingPreview(OnboardingStep.Destination, ThemeMode.Light)

@Preview(name = "Interval dark", widthDp = 402, heightDp = 941)
@Composable
private fun OnboardingIntervalPreview() = OnboardingPreview(OnboardingStep.Interval)

@Preview(name = "Interval light", widthDp = 402, heightDp = 941)
@Composable
private fun OnboardingIntervalLightPreview() = OnboardingPreview(OnboardingStep.Interval, ThemeMode.Light)

@Preview(name = "Departure dark", widthDp = 402, heightDp = 941)
@Composable
private fun OnboardingDeparturePreview() = OnboardingPreview(OnboardingStep.Departure)

@Preview(name = "Departure light", widthDp = 402, heightDp = 941)
@Composable
private fun OnboardingDepartureLightPreview() = OnboardingPreview(OnboardingStep.Departure, ThemeMode.Light)

@Preview(name = "Completing", widthDp = 402, heightDp = 941)
@Composable
private fun OnboardingCompletingPreview() = OnboardingPreview(OnboardingStep.Departure, completionEnabled = false)

@Preview(name = "Completion failed", widthDp = 402, heightDp = 941)
@Composable
private fun OnboardingCompletionFailedPreview() = OnboardingPreview(OnboardingStep.Departure, completionFailed = true)

@Composable
private fun OnboardingPreview(
    step: OnboardingStep,
    theme: ThemeMode = ThemeMode.Dark,
    completionEnabled: Boolean = true,
    completionFailed: Boolean = false,
) {
    RingoutTheme(themeMode = theme) {
        OnboardingScreen(
            uiState = OnboardingUiState(step = step),
            onPageChanged = {},
            onNext = {},
            completionEnabled = completionEnabled,
            completionFailed = completionFailed,
        )
    }
}
