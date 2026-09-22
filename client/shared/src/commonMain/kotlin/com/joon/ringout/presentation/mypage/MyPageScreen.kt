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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
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
) {
    MyPageScreenContent(
        themeMode = themeMode,
        appVersion = appVersion,
        policies = policies,
        onThemeModeChange = onThemeModeChange,
        onBackClick = onBackClick,
        onPolicyClick = onPolicyClick,
        onLoginClick = onLoginClick,
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
        item { MyPageAccountStatus(onClick = onLoginClick) }
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
private fun MyPagePreview(themeMode: ThemeMode) {
    RingoutTheme(themeMode) {
        MyPageScreenContent(
            themeMode = themeMode,
            appVersion = "1.0.0",
            policies = DefaultMyPagePolicies,
            onThemeModeChange = {},
            onBackClick = {},
            onPolicyClick = {},
        )
    }
}
