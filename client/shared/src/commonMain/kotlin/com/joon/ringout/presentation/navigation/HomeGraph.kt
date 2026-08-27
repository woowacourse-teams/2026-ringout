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

// 홈과 마이페이지는 각 백스택 항목의 저장소를 사용하고, 닉네임 변경은 마이페이지의 프로필 상태를 공유한다.
internal fun EntryProviderScope<AppRoute>.homeGraph(
    navigationState: AppNavigationState,
    homeViewModel: HomeViewModel,
    myPageViewModel: MyPageViewModel?,
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
    entry<AppRoute.Home>(clazzContentKey = AppRoute::viewModelStoreKey) {
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

    entry<AppRoute.MyPage>(clazzContentKey = AppRoute::viewModelStoreKey) {
        MyPageRoute(
            viewModel = checkNotNull(myPageViewModel),
            themeMode = themeMode,
            appVersion = appVersion,
            onThemeModeChange = onThemeModeChange,
            onBackClick = { navigationState.popBackStack(AppRoute.MyPage) },
            onLoginClick = onLogin,
            onNicknameChangeClick = { navigationState.navigate(AppRoute.NicknameChange) },
        )
    }

    entry<AppRoute.NicknameChange>(clazzContentKey = AppRoute::viewModelStoreKey) {
        val myPageViewModel = checkNotNull(myPageViewModel)
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
