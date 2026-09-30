package com.joon.ringout.presentation.mypage

import com.joon.ringout.presentation.common.component.LoadingOverlay
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import com.joon.ringout.presentation.mypage.component.MyPageAccountActionDialog
import com.joon.ringout.presentation.mypage.component.MyPageAccountManagementSection
import com.joon.ringout.presentation.mypage.component.MyPageDataActionDialog
import com.joon.ringout.presentation.mypage.component.MyPageDataSection
import com.joon.ringout.presentation.mypage.model.MyPageAccountAction
import com.joon.ringout.presentation.mypage.model.MyPageDataAction
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import com.joon.ringout.presentation.mypage.component.MyPageAccountLoadError
import com.joon.ringout.presentation.mypage.component.MyPageLoggedInAccountStatus
import com.joon.ringout.presentation.mypage.model.MyPageAccountStatus as AccountStatus
import com.joon.ringout.presentation.mypage.component.MyPageAccountStatus
import com.joon.ringout.presentation.mypage.component.MyPageAppVersionRow
import com.joon.ringout.presentation.mypage.component.MyPagePolicySection
import com.joon.ringout.presentation.mypage.component.MyPageThemeCard
import com.joon.ringout.presentation.mypage.component.myPageColors

@Composable
internal fun MyPageScreen(
    themeMode: ThemeMode,
    appVersion: String,
    policies: List<PolicyInfo>,
    onThemeModeChange: (ThemeMode) -> Unit,
    onPolicyClick: (PolicyId) -> Unit,
    modifier: Modifier = Modifier,
    onLoginClick: () -> Unit = {},
    accountStatus: AccountStatus = AccountStatus.LoggedOut,
    onAccountRetry: () -> Unit = {},
    onEditProfileClick: () -> Unit = {},
    isAccountActionInProgress: Boolean = false,
    onConfirmAccountAction: (MyPageAccountAction) -> Unit = {},
    onConfirmDataAction: (MyPageDataAction) -> Unit = {},
) {
    MyPageScreenContent(
        themeMode = themeMode,
        appVersion = appVersion,
        policies = policies,
        onThemeModeChange = onThemeModeChange,
        onPolicyClick = onPolicyClick,
        onLoginClick = onLoginClick,
        accountStatus = accountStatus,
        onAccountRetry = onAccountRetry,
        onEditProfileClick = onEditProfileClick,
        isAccountActionInProgress = isAccountActionInProgress,
        onConfirmAccountAction = onConfirmAccountAction,
        onConfirmDataAction = onConfirmDataAction,
        modifier = modifier,
    )
}

@Composable
fun MyPageScreenContent(
    themeMode: ThemeMode,
    appVersion: String,
    policies: List<PolicyInfo>,
    onThemeModeChange: (ThemeMode) -> Unit,
    onPolicyClick: (PolicyId) -> Unit,
    modifier: Modifier = Modifier,
    onLoginClick: () -> Unit = {},
    accountStatus: AccountStatus = AccountStatus.LoggedOut,
    onAccountRetry: () -> Unit = {},
    onEditProfileClick: () -> Unit = {},
    isAccountActionInProgress: Boolean = false,
    onConfirmAccountAction: (MyPageAccountAction) -> Unit = {},
    onConfirmDataAction: (MyPageDataAction) -> Unit = {},
) {
    val colors = myPageColors()
    var pendingActionName by rememberSaveable(accountStatus is AccountStatus.LoggedIn) {
        mutableStateOf<String?>(null)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = MyPageBottomContentPadding),
    ) {
        item {
            when (accountStatus) {
                AccountStatus.Loading -> Text(
                    text = "계정 정보를 불러오는 중이에요.",
                    color = colors.secondaryText,
                    modifier = Modifier.padding(vertical = 20.dp),
                )
                AccountStatus.LoggedOut -> MyPageAccountStatus(onClick = onLoginClick)
                AccountStatus.Error -> MyPageAccountLoadError(onRetry = onAccountRetry)
                is AccountStatus.LoggedIn -> MyPageLoggedInAccountStatus(
                    nickname = accountStatus.nickname,
                    email = accountStatus.email,
                    onEditClick = if (isAccountActionInProgress) null else onEditProfileClick,
                )
            }
        }
        item { Spacer(Modifier.height(10.dp)) }
        item {
            MyPageThemeCard(
                themeMode = themeMode,
                onThemeModeChange = onThemeModeChange,
            )
        }
        item { Spacer(Modifier.height(10.dp)) }
        item {
            MyPageDataSection(
                enabled = !isAccountActionInProgress &&
                    (accountStatus is AccountStatus.LoggedIn || accountStatus == AccountStatus.LoggedOut),
                onSaveClick = {
                    if (accountStatus is AccountStatus.LoggedIn) {
                        pendingActionName = MyPageConfirmation.SaveData.name
                    } else if (accountStatus == AccountStatus.LoggedOut) {
                        onLoginClick()
                    }
                },
                onLoadClick = {
                    if (accountStatus is AccountStatus.LoggedIn) {
                        pendingActionName = MyPageConfirmation.LoadData.name
                    } else if (accountStatus == AccountStatus.LoggedOut) {
                        onLoginClick()
                    }
                },
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
        if (accountStatus is AccountStatus.LoggedIn) {
            item { Spacer(Modifier.height(10.dp)) }
            item {
                MyPageAccountManagementSection(
                    enabled = !isAccountActionInProgress,
                    onLogoutClick = { pendingActionName = MyPageConfirmation.Logout.name },
                    onWithdrawClick = { pendingActionName = MyPageConfirmation.Withdraw.name },
                )
            }
        }
        item { Spacer(Modifier.height(10.dp)) }
        item { MyPageAppVersionRow(appVersion = appVersion) }
    }
    if (isAccountActionInProgress) {
        LoadingOverlay(message = "계정 처리 중")
    }
    if (accountStatus is AccountStatus.LoggedIn && !isAccountActionInProgress) {
        pendingActionName?.let { actionName ->
            val action = MyPageConfirmation.valueOf(actionName)
            action.accountAction?.let { accountAction ->
                MyPageAccountActionDialog(
                    action = accountAction,
                    onDismiss = { pendingActionName = null },
                    onConfirm = {
                        if (pendingActionName == actionName) {
                            pendingActionName = null
                            onConfirmAccountAction(accountAction)
                        }
                    },
                )
            }
            action.dataAction?.let { dataAction ->
                MyPageDataActionDialog(
                    action = dataAction,
                    onDismiss = { pendingActionName = null },
                    onConfirm = {
                        if (pendingActionName == actionName) {
                            pendingActionName = null
                            onConfirmDataAction(dataAction)
                        }
                    },
                )
            }
        }
    }
}

private enum class MyPageConfirmation(
    val accountAction: MyPageAccountAction? = null,
    val dataAction: MyPageDataAction? = null,
) {
    Logout(accountAction = MyPageAccountAction.Logout),
    Withdraw(accountAction = MyPageAccountAction.Withdraw),
    SaveData(dataAction = MyPageDataAction.Save),
    LoadData(dataAction = MyPageDataAction.Load),
}

@Preview(name = "Dark My Page", widthDp = 402, heightDp = 800)
@Composable
private fun MyPageDarkPreview() = MyPagePreview(ThemeMode.Dark)

@Preview(name = "Light My Page", widthDp = 402, heightDp = 800)
@Composable
private fun MyPageLightPreview() = MyPagePreview(ThemeMode.Light)

@Preview(name = "Small My Page", widthDp = 360, heightDp = 800)
@Composable
private fun MyPageSmallPreview() = MyPagePreview(ThemeMode.Dark)

@Composable
private fun MyPagePreview(
    themeMode: ThemeMode,
    accountStatus: AccountStatus = AccountStatus.LoggedOut,
) {
    RingoutTheme(themeMode) {
        MyPageScreenContent(
            themeMode = themeMode,
            appVersion = "1.0.0",
            policies = DefaultMyPagePolicies,
            accountStatus = accountStatus,
            onThemeModeChange = {},
            onPolicyClick = {},
        )
    }
}

@Preview(name = "Logged in My Page", widthDp = 402, heightDp = 800)
@Composable
private fun MyPageLoggedInPreview() = MyPagePreview(
    ThemeMode.Dark,
    AccountStatus.LoggedIn(nickname = "링아웃", email = "ringout@example.com"),
)

@Preview(name = "Loading My Page", widthDp = 402, heightDp = 800)
@Composable
private fun MyPageLoadingPreview() = MyPagePreview(ThemeMode.Dark, AccountStatus.Loading)

@Preview(name = "Account error My Page", widthDp = 402, heightDp = 800)
@Composable
private fun MyPageAccountErrorPreview() = MyPagePreview(ThemeMode.Dark, AccountStatus.Error)

private val MyPageBottomContentPadding = 96.dp

@Preview(name = "Logged in My Page - Short screen", widthDp = 360, heightDp = 480)
@Composable
private fun MyPageScrollablePreview() = MyPagePreview(
    ThemeMode.Dark,
    AccountStatus.LoggedIn(nickname = "링아웃", email = "ringout@example.com"),
)
