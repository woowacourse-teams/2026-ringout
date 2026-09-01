package com.joon.ringout.presentation.mypage

import androidx.compose.runtime.Composable
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
        onPolicyClick = { policyId ->
            findPolicyUrl(policyId)?.let { url ->
                runCatching { uriHandler.openUri(url) }
            }
        },
        modifier = modifier,
    )

    // TODO(RINGOUT_ACCOUNT): 로그인 재도입 시 계정 상태, 로그인, 프로필 수정,
    // 로그아웃, 회원 탈퇴 콜백을 MyPageScreen에 다시 연결한다.
}
