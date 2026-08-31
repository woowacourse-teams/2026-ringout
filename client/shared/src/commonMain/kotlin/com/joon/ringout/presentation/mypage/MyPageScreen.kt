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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.joon.ringout.presentation.mypage.component.MyPageAccountActionDialog
import com.joon.ringout.presentation.mypage.component.MyPageAccountActionErrorDialog
import com.joon.ringout.presentation.mypage.component.MyPageAccountLoadError
import com.joon.ringout.presentation.mypage.component.MyPageAccountManagementSection
import com.joon.ringout.presentation.mypage.component.MyPageAccountStatus
import com.joon.ringout.presentation.mypage.component.MyPageAppVersionRow
import com.joon.ringout.presentation.mypage.component.MyPageHeader
import com.joon.ringout.presentation.mypage.component.MyPageLoggedInAccountStatus
import com.joon.ringout.presentation.mypage.component.MyPagePolicySection
import com.joon.ringout.presentation.mypage.component.MyPageThemeCard
import com.joon.ringout.presentation.mypage.component.myPageColors
import com.joon.ringout.presentation.mypage.model.MyPageAccountAction
import com.joon.ringout.presentation.mypage.model.MyPageAccountActionState
import com.joon.ringout.presentation.mypage.model.MyPageAccountStatus
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
    onAccountStatusClick: () -> Unit,
    onAccountRetry: () -> Unit,
    onPolicyClick: (PolicyId) -> Unit,
    onEditProfileClick: () -> Unit,
    onLogoutConfirm: () -> Unit,
    onWithdrawConfirm: () -> Unit,
    onAccountActionErrorDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val entryToken = remember { MyPageEntryToken() }
    val analyticsLoginState = when (uiState.accountStatus) {
        MyPageAccountStatus.Loading -> null
        MyPageAccountStatus.LoggedOut -> AnalyticsLoginState.LoggedOut
        MyPageAccountStatus.Error -> AnalyticsLoginState.LoggedIn
        is MyPageAccountStatus.LoggedIn -> AnalyticsLoginState.LoggedIn
    }
    LaunchedEffect(entryToken, analyticsLoginState) {
        if (analyticsLoginState != null) {
            onScreenEntered(entryToken, analyticsLoginState)
        }
    }
    MyPageScreenContent(
        uiState = uiState,
        themeMode = themeMode,
        appVersion = appVersion,
        policies = policies,
        onThemeModeChange = onThemeModeChange,
        onPreviousMonthClick = {
            analyticsLoginState?.let(onPreviousMonthClick)
        },
        onNextMonthClick = {
            analyticsLoginState?.let(onNextMonthClick)
        },
        onCalendarRetry = onCalendarRetry,
        isMonthNavigationEnabled = analyticsLoginState != null,
        onBackClick = onBackClick,
        onAccountStatusClick = onAccountStatusClick,
        onAccountRetry = onAccountRetry,
        onPolicyClick = onPolicyClick,
        onEditProfileClick = onEditProfileClick,
        onLogoutConfirm = onLogoutConfirm,
        onWithdrawConfirm = onWithdrawConfirm,
        modifier = modifier,
    )

    val actionError = uiState.accountAction as? MyPageAccountActionState.Error
    if (actionError != null) {
        MyPageAccountActionErrorDialog(
            action = actionError.action,
            message = actionError.message,
            onDismiss = onAccountActionErrorDismiss,
        )
    }
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
    onAccountStatusClick: () -> Unit,
    onAccountRetry: () -> Unit,
    onPolicyClick: (PolicyId) -> Unit,
    isMonthNavigationEnabled: Boolean = true,
    onEditProfileClick: () -> Unit = {},
    onLogoutConfirm: () -> Unit = {},
    onWithdrawConfirm: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = myPageColors()
    val accountStatus = uiState.accountStatus
    val isAccountActionInProgress =
        uiState.accountAction is MyPageAccountActionState.InProgress
    var pendingAccountAction by remember { mutableStateOf<MyPageAccountAction?>(null) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item { MyPageHeader(onBackClick = onBackClick) }
        item { Spacer(Modifier.height(6.dp)) }
        item {
            when (accountStatus) {
                MyPageAccountStatus.Loading -> {
                    Text(
                        text = "회원 정보 불러오는 중…",
                        modifier = Modifier.semantics {
                            liveRegion = LiveRegionMode.Polite
                        },
                        color = colors.secondaryText,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                MyPageAccountStatus.LoggedOut -> {
                    MyPageAccountStatus(onClick = onAccountStatusClick)
                }

                MyPageAccountStatus.Error -> {
                    MyPageAccountLoadError(onRetry = onAccountRetry)
                }

                is MyPageAccountStatus.LoggedIn -> {
                    MyPageLoggedInAccountStatus(
                        nickname = accountStatus.nickname,
                        email = accountStatus.email,
                        onEditClick = onEditProfileClick,
                    )
                }
            }
        }
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
        if (
            accountStatus is MyPageAccountStatus.LoggedIn ||
            accountStatus == MyPageAccountStatus.Error
        ) {
            item { Spacer(Modifier.height(10.dp)) }
            item {
                MyPageAccountManagementSection(
                    onLogoutClick = { pendingAccountAction = MyPageAccountAction.Logout },
                    onWithdrawClick = { pendingAccountAction = MyPageAccountAction.Withdraw },
                    enabled = !isAccountActionInProgress,
                )
            }
        }
    }

    pendingAccountAction?.let { action ->
        MyPageAccountActionDialog(
            action = action,
            onDismiss = { pendingAccountAction = null },
            onConfirm = {
                pendingAccountAction = null
                when (action) {
                    MyPageAccountAction.Logout -> onLogoutConfirm()
                    MyPageAccountAction.Withdraw -> onWithdrawConfirm()
                }
            },
        )
    }
}

private val PreviewState = MyPageUiState(
    isLoading = false,
    selectedMonth = MyPageCalendarMonth(MissionYearMonth(2026, 8)),
    successDates = listOf(1, 3, 5, 7, 11, 13, 17, 21, 23, 25, 28)
        .mapTo(mutableSetOf()) { day -> MissionDate.of(2026, 8, day) },
    accountStatus = MyPageAccountStatus.LoggedOut,
)

@Preview(name = "Dark My Page - success stamps", widthDp = 402, heightDp = 985)
@Composable
private fun MyPageDarkPreview() = MyPagePreview(ThemeMode.Dark, PreviewState)

@Preview(name = "Light My Page - success stamps", widthDp = 402, heightDp = 985)
@Composable
private fun MyPageLightPreview() = MyPagePreview(ThemeMode.Light, PreviewState)

@Preview(name = "Logged in My Page - Dark", widthDp = 402, heightDp = 1082)
@Composable
private fun MyPageLoggedInDarkPreview() = MyPageLoggedInInteractivePreview(ThemeMode.Dark)

@Preview(name = "Logged in My Page - Light", widthDp = 402, heightDp = 1082)
@Composable
private fun MyPageLoggedInLightPreview() = MyPageLoggedInInteractivePreview(ThemeMode.Light)

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

@Preview(name = "Account Error My Page", widthDp = 402, heightDp = 941)
@Composable
private fun MyPageAccountErrorPreview() = MyPagePreview(
    themeMode = ThemeMode.Light,
    state = PreviewState.copy(accountStatus = MyPageAccountStatus.Error),
)

@Preview(name = "Account Loading My Page", widthDp = 402, heightDp = 941)
@Composable
private fun MyPageAccountLoadingPreview() = MyPagePreview(
    themeMode = ThemeMode.Dark,
    state = PreviewState.copy(accountStatus = MyPageAccountStatus.Loading),
)

private val PreviewLoggedInAccount = MyPageAccountStatus.LoggedIn(
    nickname = "닉네임닉네임12312313",
    email = "dsakdfsa@gmail.com",
)

@Composable
private fun MyPageLoggedInInteractivePreview(initialThemeMode: ThemeMode) {
    var previewThemeMode by remember { mutableStateOf(initialThemeMode) }

    RingoutTheme(previewThemeMode) {
        MyPageScreenContent(
            uiState = PreviewState.copy(accountStatus = PreviewLoggedInAccount),
            themeMode = previewThemeMode,
            appVersion = "1.0.0",
            policies = DefaultMyPagePolicies,
            onThemeModeChange = { previewThemeMode = it },
            onPreviousMonthClick = {},
            onNextMonthClick = {},
            onCalendarRetry = {},
            onBackClick = {},
            onAccountStatusClick = {},
            onAccountRetry = {},
            onPolicyClick = {},
        )
    }
}

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
            onAccountStatusClick = {},
            onAccountRetry = {},
            onPolicyClick = {},
        )
    }
}
