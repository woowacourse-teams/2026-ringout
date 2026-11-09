package com.joon.ringout.presentation.arrival

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import com.joon.ringout.domain.arrival.ArrivalResult
import com.joon.ringout.domain.missionhistory.MissionDate
import com.joon.ringout.presentation.arrival.component.ArrivalActions
import com.joon.ringout.presentation.arrival.component.ArrivalCelebration
import com.joon.ringout.ringoutColors

@Composable
internal fun ArrivalScreen(
    uiState: ArrivalUiState,
    onStartDay: () -> Unit,
    onShare: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.ringoutColors.mainScreenBackground)
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center,
    ) {
        when {
            uiState.isLoading -> CircularProgressIndicator(
                Modifier.testTag("arrival-loading").semantics { contentDescription = "도착 기록을 불러오는 중" },
            )
            uiState.result != null -> Column(
                modifier = Modifier.widthIn(max = 480.dp).fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ArrivalCelebration(
                    growth = uiState.result.growth,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
                ArrivalActions(onStartDay = onStartDay, onShare = onShare)
            }
            else -> Column(
                modifier = Modifier.widthIn(max = 480.dp).verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = uiState.errorMessage ?: "도착 기록을 찾을 수 없어요.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                TextButton(onClick = onRetry) { Text("다시 시도") }
            }
        }
    }
}

private fun previewArrivalState(successfulDays: Int) = ArrivalUiState(
    result = ArrivalResult(
        occurrenceId = "preview-arrival",
        completedLocalDate = MissionDate.parse("2026-11-09"),
        completedAtEpochMillis = 1_794_182_400_000,
        alarmDismissedAtEpochMillis = 1_794_180_600_000,
        successfulDayCountAtArrival = successfulDays,
        grewOnThisArrival = true,
        acknowledgedAtEpochMillis = null,
    ),
    isLoading = false,
)

@Preview(name = "씨앗 성장 중", widthDp = 402, heightDp = 941)
@Composable
private fun ArrivalScreenPreview() {
    RingoutTheme { ArrivalScreen(previewArrivalState(4), onStartDay = {}, onShare = {}, onRetry = {}) }
}

@Preview(name = "나무 완성", widthDp = 402, heightDp = 941)
@Composable
private fun ArrivalTreeScreenPreview() {
    RingoutTheme { ArrivalScreen(previewArrivalState(5), onStartDay = {}, onShare = {}, onRetry = {}) }
}

@Preview(name = "여섯 번째 도착 · 새 씨앗", widthDp = 402, heightDp = 941)
@Composable
private fun ArrivalNewSeedScreenPreview() {
    RingoutTheme { ArrivalScreen(previewArrivalState(6), onStartDay = {}, onShare = {}, onRetry = {}) }
}

@Preview(name = "라이트", widthDp = 402, heightDp = 941)
@Composable
private fun ArrivalLightScreenPreview() {
    RingoutTheme(ThemeMode.Light) { ArrivalScreen(previewArrivalState(4), onStartDay = {}, onShare = {}, onRetry = {}) }
}

@Preview(name = "작은 화면 · 큰 글씨", widthDp = 320, heightDp = 568, fontScale = 2f)
@Composable
private fun ArrivalLargeTextScreenPreview() {
    RingoutTheme { ArrivalScreen(previewArrivalState(4), onStartDay = {}, onShare = {}, onRetry = {}) }
}

@Preview(name = "로딩", widthDp = 402, heightDp = 941)
@Composable
private fun ArrivalLoadingScreenPreview() {
    RingoutTheme { ArrivalScreen(ArrivalUiState(), onStartDay = {}, onShare = {}, onRetry = {}) }
}

@Preview(name = "오류", widthDp = 402, heightDp = 941)
@Composable
private fun ArrivalErrorScreenPreview() {
    RingoutTheme {
        ArrivalScreen(
            ArrivalUiState(isLoading = false, errorMessage = "도착 기록을 불러오지 못했어요. 다시 시도해 주세요."),
            onStartDay = {}, onShare = {}, onRetry = {},
        )
    }
}
