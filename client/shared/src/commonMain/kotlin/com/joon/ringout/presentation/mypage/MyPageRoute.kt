package com.joon.ringout.presentation.mypage

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.joon.ringout.presentation.mypage.component.MyPageAccountActionErrorDialog
import com.joon.ringout.presentation.mypage.model.MyPageAccountAction
import com.joon.ringout.presentation.mypage.model.MyPageAccountActionState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import com.joon.ringout.ThemeMode

@Composable
internal fun MyPageRoute(
    viewModel: MyPageViewModel,
    themeMode: ThemeMode,
    appVersion: String,
    onThemeModeChange: (ThemeMode) -> Unit,
    onBackClick: () -> Unit,
    onLoginClick: () -> Unit,
    modifier: Modifier = Modifier,
    onEditProfileClick: () -> Unit = {},
) {
    val uriHandler = LocalUriHandler.current
    val accountAction = viewModel.uiState.accountAction
    LaunchedEffect(accountAction) {
        if (accountAction is MyPageAccountActionState.Completed) {
            viewModel.consumeAccountActionCompletedEvent(accountAction.eventId)
        }
    }

    MyPageScreen(
        accountStatus = viewModel.uiState.accountStatus,
        onAccountRetry = viewModel::retryAccount,
        isAccountActionInProgress = accountAction is MyPageAccountActionState.InProgress,
        onConfirmAccountAction = { action ->
            when (action) {
                MyPageAccountAction.Logout -> viewModel.logout()
                MyPageAccountAction.Withdraw -> viewModel.withdraw()
            }
        },
        themeMode = themeMode,
        appVersion = appVersion,
        policies = DefaultMyPagePolicies,
        onThemeModeChange = onThemeModeChange,
        onBackClick = onBackClick,
        onLoginClick = onLoginClick,
        onEditProfileClick = onEditProfileClick,
        onPolicyClick = { policyId ->
            findPolicyUrl(policyId)?.let { url ->
                runCatching { uriHandler.openUri(url) }
            }
        },
        modifier = modifier,
    )

    if (accountAction is MyPageAccountActionState.Error) {
        MyPageAccountActionErrorDialog(
            action = accountAction.action,
            message = accountAction.message,
            onDismiss = viewModel::clearAccountActionError,
        )
    }
}
