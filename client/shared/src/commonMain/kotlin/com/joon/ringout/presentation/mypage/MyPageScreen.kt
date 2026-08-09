package com.joon.ringout.presentation.mypage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import com.joon.ringout.domain.missionhistory.GetMissionSuccessDates
import com.joon.ringout.domain.missionhistory.MissionDate
import com.joon.ringout.domain.missionhistory.MissionYearMonth
import com.joon.ringout.presentation.destination.PlatformBackHandler
import com.joon.ringout.presentation.mypage.component.MissionCalendarCard
import com.joon.ringout.presentation.mypage.component.MyPageAppVersionRow
import com.joon.ringout.presentation.mypage.component.MyPageHeader
import com.joon.ringout.presentation.mypage.component.MyPagePolicySection
import com.joon.ringout.presentation.mypage.component.MyPageThemeCard

@Composable
fun MyPageScreen(
    getMissionSuccessDates: GetMissionSuccessDates,
    themeMode: ThemeMode,
    appVersion: String,
    policies: List<PolicyInfo>,
    onThemeModeChange: (ThemeMode) -> Unit,
    onBackClick: () -> Unit,
    onPolicyClick: (PolicyId) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MyPageViewModel = viewModel {
        MyPageViewModel(getMissionSuccessDates, currentMissionYearMonth())
    },
) {
    PlatformBackHandler(onBack = onBackClick)
    MyPageScreenContent(
        uiState = viewModel.uiState,
        themeMode = themeMode,
        appVersion = appVersion,
        policies = policies,
        onThemeModeChange = onThemeModeChange,
        onPreviousMonthClick = viewModel::onPreviousMonthClick,
        onNextMonthClick = viewModel::onNextMonthClick,
        onBackClick = onBackClick,
        onPolicyClick = onPolicyClick,
        modifier = modifier,
    )
}

@Composable
fun MyPageScreenContent(
    uiState: MyPageUiState,
    themeMode: ThemeMode,
    appVersion: String,
    policies: List<PolicyInfo>,
    onThemeModeChange: (ThemeMode) -> Unit,
    onPreviousMonthClick: () -> Unit,
    onNextMonthClick: () -> Unit,
    onBackClick: () -> Unit,
    onPolicyClick: (PolicyId) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item { MyPageHeader(onBackClick = onBackClick) }
        item {
            MissionCalendarCard(
                month = uiState.selectedMonth,
                successDates = uiState.successDates,
                onPreviousMonthClick = onPreviousMonthClick,
                onNextMonthClick = onNextMonthClick,
            )
        }
        if (uiState.isLoading) {
            item {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
        uiState.errorMessage?.let { message ->
            item {
                Text(
                    text = message,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        item {
            MyPageThemeCard(
                themeMode = themeMode,
                onThemeModeChange = onThemeModeChange,
            )
        }
        if (policies.isNotEmpty()) {
            item {
                MyPagePolicySection(
                    policies = policies,
                    onPolicyClick = onPolicyClick,
                )
            }
        }
        item { MyPageAppVersionRow(appVersion = appVersion) }
    }
}

private val PreviewPolicies = listOf(
    PolicyInfo(PolicyId("privacy"), "개인정보처리방침", PolicyIcon.PRIVACY),
    PolicyInfo(PolicyId("terms"), "이용약관", PolicyIcon.DOCUMENT),
)

private val PreviewState = MyPageUiState(
    isLoading = false,
    selectedMonth = MyPageCalendarMonth(MissionYearMonth(2026, 8)),
    successDates = setOf(1, 3, 4, 5, 7, 11, 12, 13, 17, 18, 21, 22, 23, 25, 26, 28)
        .mapTo(mutableSetOf()) { MissionDate.of(2026, 8, it) },
)

@Preview(name = "Dark My Page", widthDp = 402, heightDp = 941)
@Composable
private fun MyPageDarkPreview() = MyPagePreview(ThemeMode.Dark, PreviewState)

@Preview(name = "Light My Page", widthDp = 402, heightDp = 941)
@Composable
private fun MyPageLightPreview() = MyPagePreview(ThemeMode.Light, PreviewState)

@Preview(name = "Small empty My Page", widthDp = 360, heightDp = 800)
@Composable
private fun MyPageSmallEmptyPreview() = MyPagePreview(
    ThemeMode.Dark,
    PreviewState.copy(successDates = emptySet()),
)

@Preview(name = "Error My Page", widthDp = 402, heightDp = 941)
@Composable
private fun MyPageErrorPreview() = MyPagePreview(
    ThemeMode.Light,
    PreviewState.copy(errorMessage = "미션 기록을 불러오지 못했어요."),
)

@Composable
private fun MyPagePreview(themeMode: ThemeMode, state: MyPageUiState) {
    RingoutTheme(themeMode) {
        MyPageScreenContent(
            uiState = state,
            themeMode = themeMode,
            appVersion = "1.0.0",
            policies = PreviewPolicies,
            onThemeModeChange = {},
            onPreviousMonthClick = {},
            onNextMonthClick = {},
            onBackClick = {},
            onPolicyClick = {},
        )
    }
}
