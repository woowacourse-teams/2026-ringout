package com.joon.ringout.presentation.navigation

import com.joon.ringout.presentation.social.SocialScreen
import com.joon.ringout.presentation.social.SocialViewModel
import com.joon.ringout.presentation.records.RecordsScreen
import com.joon.ringout.presentation.records.RecordsViewModel
import androidx.navigation3.runtime.EntryProviderScope
import com.joon.ringout.domain.auth.AuthSessionState
import com.joon.ringout.domain.member.MemberRepository
import com.joon.ringout.presentation.nickname.NicknameChangeRoute
import com.joon.ringout.ThemeMode
import com.joon.ringout.alarm.ActiveAlarmMission
import com.joon.ringout.alarm.AlarmController
import com.joon.ringout.alarm.AlarmScheduleRequest
import com.joon.ringout.presentation.home.HomeRoute
import com.joon.ringout.presentation.home.HomeViewModel
import com.joon.ringout.presentation.mypage.MyPageRoute
import com.joon.ringout.presentation.mypage.MyPageViewModel

// 홈과 마이페이지는 각 백스택 항목의 저장소를 사용한다.
internal fun EntryProviderScope<AppRoute>.homeGraph(
    navigationState: AppNavigationState,
    homeViewModel: HomeViewModel,
    viewModelScopes: NavigationViewModelScopes,
    myPageViewModel: MyPageViewModel?,
    authSessionState: AuthSessionState,
    memberRepository: MemberRepository,
    themeMode: ThemeMode,
    appVersion: String,
    alarmController: AlarmController,
    activeAlarmMission: ActiveAlarmMission?,
    onThemeModeChange: (ThemeMode) -> Unit,
    onAddAlarm: () -> Unit,
    onEditAlarm: (AlarmScheduleRequest) -> Unit,
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
            onLoginClick = { navigationState.navigate(AppRoute.Login) },
            onEditProfileClick = { navigationState.navigate(AppRoute.NicknameChange) },
        )
    }

    entry<AppRoute.Social>(clazzContentKey = AppRoute::viewModelStoreKey) {
        SocialScreen(viewModelScopes.get(AppRoute.Social, SocialViewModel::class).uiState)
    }
    entry<AppRoute.Records>(clazzContentKey = AppRoute::viewModelStoreKey) {
        RecordsScreen(viewModelScopes.get(AppRoute.Records, RecordsViewModel::class).uiState)
    }

    entry<AppRoute.NicknameChange>(clazzContentKey = AppRoute::viewModelStoreKey) {
        val myPage = checkNotNull(myPageViewModel)
        NicknameChangeRoute(
            accountStatus = myPage.uiState.accountStatus,
            authSessionState = authSessionState,
            memberRepository = memberRepository,
            onBackClick = { navigationState.popBackStack(AppRoute.NicknameChange) },
            onNicknameChanged = { nickname ->
                myPage.onNicknameUpdated(nickname)
                navigationState.popBackStack(AppRoute.NicknameChange)
            },
        )
    }
}
