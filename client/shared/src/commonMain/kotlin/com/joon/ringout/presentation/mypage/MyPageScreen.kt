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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import com.joon.ringout.domain.missionhistory.GetMissionSuccessDates
import com.joon.ringout.domain.missionhistory.MissionDate
import com.joon.ringout.domain.missionhistory.MissionYearMonth
import com.joon.ringout.presentation.destination.PlatformBackHandler
import com.joon.ringout.presentation.mypage.component.MissionCalendarCard
import com.joon.ringout.presentation.mypage.component.MyPageAccountAction
import com.joon.ringout.presentation.mypage.component.MyPageAccountActionDialog
import com.joon.ringout.presentation.mypage.component.MyPageAccountManagementSection
import com.joon.ringout.presentation.mypage.component.MyPageAccountStatus
import com.joon.ringout.presentation.mypage.component.MyPageAppVersionRow
import com.joon.ringout.presentation.mypage.component.MyPageHeader
import com.joon.ringout.presentation.mypage.component.MyPageLoggedInAccountStatus
import com.joon.ringout.presentation.mypage.component.MyPagePolicySection
import com.joon.ringout.presentation.mypage.component.MyPageThemeCard
import com.joon.ringout.presentation.mypage.component.myPageColors

@Composable
fun MyPageScreen(
    themeMode: ThemeMode,
    appVersion: String,
    policies: List<PolicyInfo>,
    accountUiState: MyPageAccountUiState,
    onThemeModeChange: (ThemeMode) -> Unit,
    onBackClick: () -> Unit,
    onAccountStatusClick: () -> Unit,
    onPolicyClick: (PolicyId) -> Unit,
    onEditProfileClick: () -> Unit,
    onLogoutConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MyPageViewModel = rememberMyPageViewModel(),
) {
    val isLoggedIn = when (accountUiState) {
        MyPageAccountUiState.Loading -> null
        MyPageAccountUiState.LoggedOut -> false
        is MyPageAccountUiState.LoggedIn -> true
    }
    LaunchedEffect(viewModel, isLoggedIn) {
        if (isLoggedIn != null) {
            viewModel.onScreenEntered()
        }
    }
    PlatformBackHandler(onBack = onBackClick)
    MyPageScreenContent(
        uiState = viewModel.uiState,
        themeMode = themeMode,
        appVersion = appVersion,
        policies = policies,
        accountUiState = accountUiState,
        onThemeModeChange = onThemeModeChange,
        onPreviousMonthClick = viewModel::onPreviousMonthClick,
        onNextMonthClick = viewModel::onNextMonthClick,
        onBackClick = onBackClick,
        onAccountStatusClick = onAccountStatusClick,
        onPolicyClick = onPolicyClick,
        onEditProfileClick = onEditProfileClick,
        onLogoutConfirm = onLogoutConfirm,
        modifier = modifier,
    )
}

@Composable
private fun rememberMyPageViewModel(): MyPageViewModel {
    val missionHistoryRepository = rememberMissionHistoryRepository()
    return viewModel {
        MyPageViewModel(
            getMissionSuccessDates = GetMissionSuccessDates(missionHistoryRepository),
            initialMonth = currentMissionYearMonth(),
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
    onBackClick: () -> Unit,
    onAccountStatusClick: () -> Unit,
    onPolicyClick: (PolicyId) -> Unit,
    accountUiState: MyPageAccountUiState = MyPageAccountUiState.LoggedOut,
    onEditProfileClick: () -> Unit = {},
    onLogoutConfirm: () -> Unit = {},
    onWithdrawConfirm: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = myPageColors()
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
            when (accountUiState) {
                MyPageAccountUiState.Loading -> {
                    Text(
                        text = "로그인 상태 확인 중…",
                        color = colors.secondaryText,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                MyPageAccountUiState.LoggedOut -> {
                    MyPageAccountStatus(onClick = onAccountStatusClick)
                }

                is MyPageAccountUiState.LoggedIn -> {
                    MyPageLoggedInAccountStatus(
                        nickname = accountUiState.nickname,
                        email = accountUiState.email,
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
        if (accountUiState is MyPageAccountUiState.LoggedIn) {
            item { Spacer(Modifier.height(10.dp)) }
            item {
                MyPageAccountManagementSection(
                    onLogoutClick = { pendingAccountAction = MyPageAccountAction.Logout },
                    onWithdrawClick = { pendingAccountAction = MyPageAccountAction.Withdraw },
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

private val PreviewLoggedInAccount = MyPageAccountUiState.LoggedIn(
    nickname = "닉네임닉네임12312313",
    email = "dsakdfsa@gmail.com",
)

@Composable
private fun MyPageLoggedInInteractivePreview(initialThemeMode: ThemeMode) {
    var previewThemeMode by remember { mutableStateOf(initialThemeMode) }

    RingoutTheme(previewThemeMode) {
        MyPageScreenContent(
            uiState = PreviewState,
            themeMode = previewThemeMode,
            appVersion = "1.0.0",
            policies = DefaultMyPagePolicies,
            onThemeModeChange = { previewThemeMode = it },
            onPreviousMonthClick = {},
            onNextMonthClick = {},
            onBackClick = {},
            onAccountStatusClick = {},
            onPolicyClick = {},
            accountUiState = PreviewLoggedInAccount,
        )
    }
}

@Composable
private fun MyPagePreview(
    themeMode: ThemeMode,
    state: MyPageUiState,
    accountUiState: MyPageAccountUiState = MyPageAccountUiState.LoggedOut,
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
            onBackClick = {},
            onAccountStatusClick = {},
            onPolicyClick = {},
            accountUiState = accountUiState,
        )
    }
}
