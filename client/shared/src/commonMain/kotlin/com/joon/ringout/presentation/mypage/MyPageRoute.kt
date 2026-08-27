package com.joon.ringout.presentation.mypage

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import com.joon.ringout.ThemeMode
import com.joon.ringout.presentation.mypage.model.MyPageAccountStatus

@Composable
internal fun MyPageRoute(
    viewModel: MyPageViewModel,
    themeMode: ThemeMode,
    appVersion: String,
    onThemeModeChange: (ThemeMode) -> Unit,
    onBackClick: () -> Unit,
    onLoginClick: () -> Unit,
    onNicknameChangeClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    val uiState = viewModel.uiState

    MyPageScreen(
        uiState = uiState,
        themeMode = themeMode,
        appVersion = appVersion,
        policies = DefaultMyPagePolicies,
        onScreenEntered = viewModel::onScreenEntered,
        onThemeModeChange = onThemeModeChange,
        onPreviousMonthClick = viewModel::onPreviousMonthClick,
        onNextMonthClick = viewModel::onNextMonthClick,
        onCalendarRetry = viewModel::retryCalendar,
        onBackClick = onBackClick,
        onAccountStatusClick = onLoginClick,
        onAccountRetry = viewModel::retryAccount,
        onEditProfileClick = {
            if (uiState.accountStatus is MyPageAccountStatus.LoggedIn) {
                onNicknameChangeClick()
            }
        },
        onLogoutConfirm = viewModel::logout,
        onWithdrawConfirm = viewModel::withdraw,
        onAccountActionErrorDismiss = viewModel::clearAccountActionError,
        onPolicyClick = { policyId ->
            findPolicyUrl(policyId)?.let { url ->
                runCatching { uriHandler.openUri(url) }
            }
        },
        modifier = modifier,
    )
}
