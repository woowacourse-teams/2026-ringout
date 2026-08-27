package com.joon.ringout.presentation.navigation

import androidx.navigation3.runtime.EntryProviderScope
import com.joon.ringout.ThemeMode
import com.joon.ringout.alarm.ActiveAlarmMission
import com.joon.ringout.alarm.AlarmController
import com.joon.ringout.alarm.AlarmScheduleRequest
import com.joon.ringout.domain.auth.AuthSessionState
import com.joon.ringout.domain.member.MemberRepository
import com.joon.ringout.presentation.home.HomeRoute
import com.joon.ringout.presentation.home.HomeViewModel
import com.joon.ringout.presentation.mypage.MyPageRoute
import com.joon.ringout.presentation.mypage.MyPageViewModel
import com.joon.ringout.presentation.nickname.NicknameChangeRoute

// Home/MyPage still share their ViewModels with legacy app effects until the scope migration.
internal fun EntryProviderScope<AppRoute>.homeGraph(
    navigationState: AppNavigationState,
    homeViewModel: HomeViewModel,
    myPageViewModel: MyPageViewModel,
    memberRepository: MemberRepository,
    authSessionState: AuthSessionState,
    themeMode: ThemeMode,
    appVersion: String,
    alarmController: AlarmController,
    activeAlarmMission: ActiveAlarmMission?,
    onThemeModeChange: (ThemeMode) -> Unit,
    onAddAlarm: () -> Unit,
    onEditAlarm: (AlarmScheduleRequest) -> Unit,
    onLogin: () -> Unit,
    onActiveAlarmMissionClick: () -> Unit,
    onActiveAlarmMissionExpired: () -> Unit,
) {
    entry<AppRoute.Home> {
        HomeRoute(
            viewModel = homeViewModel,
            alarmController = alarmController,
            activeAlarmMission = activeAlarmMission,
            onAddAlarm = onAddAlarm,
            onEditAlarm = onEditAlarm,
            onMyPageClick = { navigationState.navigate(AppRoute.MyPage) },
            onActiveAlarmMissionClick = onActiveAlarmMissionClick,
            onActiveAlarmMissionExpired = onActiveAlarmMissionExpired,
        )
    }

    entry<AppRoute.MyPage> {
        MyPageRoute(
            viewModel = myPageViewModel,
            themeMode = themeMode,
            appVersion = appVersion,
            onThemeModeChange = onThemeModeChange,
            onBackClick = { navigationState.popBackStack(AppRoute.MyPage) },
            onLoginClick = onLogin,
            onNicknameChangeClick = { navigationState.navigate(AppRoute.NicknameChange) },
        )
    }

    entry<AppRoute.NicknameChange> {
        NicknameChangeRoute(
            accountStatus = myPageViewModel.uiState.accountStatus,
            authSessionState = authSessionState,
            memberRepository = memberRepository,
            onBackClick = { navigationState.popBackStack(AppRoute.NicknameChange) },
            onNicknameChanged = { updatedNickname ->
                myPageViewModel.onNicknameUpdated(updatedNickname)
                navigationState.popBackStack(AppRoute.NicknameChange)
            },
        )
    }
}
