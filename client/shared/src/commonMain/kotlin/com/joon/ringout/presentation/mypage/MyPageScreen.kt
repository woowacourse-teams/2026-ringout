package com.joon.ringout.presentation.mypage

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
import com.joon.ringout.presentation.mypage.component.MyPageHeader
import com.joon.ringout.presentation.mypage.component.MyPagePolicySection
import com.joon.ringout.presentation.mypage.component.MyPageThemeCard
import com.joon.ringout.presentation.mypage.component.myPageColors

@Composable
internal fun MyPageScreen(
    themeMode: ThemeMode,
    appVersion: String,
    policies: List<PolicyInfo>,
    onThemeModeChange: (ThemeMode) -> Unit,
    onBackClick: () -> Unit,
    onPolicyClick: (PolicyId) -> Unit,
    modifier: Modifier = Modifier,
    onLoginClick: () -> Unit = {},
    accountStatus: AccountStatus = AccountStatus.LoggedOut,
    onAccountRetry: () -> Unit = {},
) {
    MyPageScreenContent(
        themeMode = themeMode,
        appVersion = appVersion,
        policies = policies,
        onThemeModeChange = onThemeModeChange,
        onBackClick = onBackClick,
        onPolicyClick = onPolicyClick,
        onLoginClick = onLoginClick,
        accountStatus = accountStatus,
        onAccountRetry = onAccountRetry,
        modifier = modifier,
    )
}

@Composable
fun MyPageScreenContent(
    themeMode: ThemeMode,
    appVersion: String,
    policies: List<PolicyInfo>,
    onThemeModeChange: (ThemeMode) -> Unit,
    onBackClick: () -> Unit,
    onPolicyClick: (PolicyId) -> Unit,
    modifier: Modifier = Modifier,
    onLoginClick: () -> Unit = {},
    accountStatus: AccountStatus = AccountStatus.LoggedOut,
    onAccountRetry: () -> Unit = {},
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
        item { Spacer(Modifier.height(16.dp)) }
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
            onBackClick = {},
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
