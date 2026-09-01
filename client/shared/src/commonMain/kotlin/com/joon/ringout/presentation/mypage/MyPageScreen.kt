package com.joon.ringout.presentation.mypage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import com.joon.ringout.analytics.AnalyticsLoginState
import com.joon.ringout.domain.missionhistory.MissionDate
import com.joon.ringout.domain.missionhistory.MissionYearMonth
import com.joon.ringout.presentation.mypage.component.MissionCalendarCard
import com.joon.ringout.presentation.mypage.component.MyPageAppVersionRow
import com.joon.ringout.presentation.mypage.component.MyPageHeader
import com.joon.ringout.presentation.mypage.component.MyPagePolicySection
import com.joon.ringout.presentation.mypage.component.MyPageThemeCard
import com.joon.ringout.presentation.mypage.component.myPageColors
import com.joon.ringout.presentation.mypage.model.MyPageUiState

@Composable
internal fun MyPageScreen(
    uiState: MyPageUiState,
    themeMode: ThemeMode,
    appVersion: String,
    policies: List<PolicyInfo>,
    onScreenEntered: (MyPageEntryToken, AnalyticsLoginState) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onPreviousMonthClick: (AnalyticsLoginState) -> Unit,
    onNextMonthClick: (AnalyticsLoginState) -> Unit,
    onCalendarRetry: () -> Unit,
    onBackClick: () -> Unit,
    onPolicyClick: (PolicyId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val entryToken = remember { MyPageEntryToken() }
    val analyticsLoginState = AnalyticsLoginState.LoggedOut
    LaunchedEffect(entryToken) {
        onScreenEntered(entryToken, analyticsLoginState)
    }
    MyPageScreenContent(
        uiState = uiState,
        themeMode = themeMode,
        appVersion = appVersion,
        policies = policies,
        onThemeModeChange = onThemeModeChange,
        onPreviousMonthClick = { onPreviousMonthClick(analyticsLoginState) },
        onNextMonthClick = { onNextMonthClick(analyticsLoginState) },
        onCalendarRetry = onCalendarRetry,
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
    onCalendarRetry: () -> Unit,
    onBackClick: () -> Unit,
    onPolicyClick: (PolicyId) -> Unit,
    isMonthNavigationEnabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val colors = myPageColors()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item { MyPageHeader(onBackClick = onBackClick) }
        // TODO(RINGOUT_ACCOUNT): 로그인 재도입 시 헤더 아래에 계정 상태 컴포넌트를 복구한다.
        item { Spacer(Modifier.height(16.dp)) }
        item {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                MissionCalendarCard(
                    month = uiState.selectedMonth,
                    successDates = uiState.successDates,
                    onPreviousMonthClick = onPreviousMonthClick,
                    onNextMonthClick = onNextMonthClick,
                    navigationEnabled = isMonthNavigationEnabled,
                )
            }
        }
        uiState.errorMessage?.let { message ->
            item { Spacer(Modifier.height(8.dp)) }
            item {
                Text(
                    text = message,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            item {
                TextButton(onClick = onCalendarRetry) {
                    Text("다시 시도")
                }
            }
        }
        item { Spacer(Modifier.height(34.dp)) }
        item {
            MyPageThemeCard(
                themeMode = themeMode,
                onThemeModeChange = onThemeModeChange,
            )
        }
        if (policies.isNotEmpty()) {
            item { Spacer(Modifier.height(10.dp)) }
            item {
                MyPagePolicySection(
                    policies = policies,
                    onPolicyClick = onPolicyClick,
                )
            }
        }
        item { Spacer(Modifier.height(10.dp)) }
        item { MyPageAppVersionRow(appVersion = appVersion) }
        // TODO(RINGOUT_ACCOUNT): 로그인 재도입 시 계정 관리 섹션과 확인 다이얼로그를 복구한다.
    }
}

private val PreviewState = MyPageUiState(
    isLoading = false,
    selectedMonth = MyPageCalendarMonth(MissionYearMonth(2026, 8)),
    successDates = listOf(1, 3, 5, 7, 11, 13, 17, 21, 23, 25, 28)
        .mapTo(mutableSetOf()) { day -> MissionDate.of(2026, 8, day) },
)

@Preview(name = "Dark My Page - success stamps", widthDp = 402, heightDp = 985)
@Composable
private fun MyPageDarkPreview() = MyPagePreview(ThemeMode.Dark, PreviewState)

@Preview(name = "Light My Page - success stamps", widthDp = 402, heightDp = 985)
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
private fun MyPagePreview(
    themeMode: ThemeMode,
    state: MyPageUiState,
) {
    RingoutTheme(themeMode) {
        MyPageScreenContent(
            uiState = state,
            themeMode = themeMode,
            appVersion = "1.0.0",
            policies = DefaultMyPagePolicies,
            onThemeModeChange = {},
            onPreviousMonthClick = {},
            onNextMonthClick = {},
            onCalendarRetry = {},
            onBackClick = {},
            onPolicyClick = {},
        )
    }
}
