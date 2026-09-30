package com.joon.ringout.presentation.navigation

import com.joon.ringout.presentation.roomlist.RoomListRoute
import com.joon.ringout.presentation.roomlist.RoomListViewModel
import com.joon.ringout.presentation.roomlist.roomdetail.RoomDetailRoute
import com.joon.ringout.presentation.records.RecordsRoute
import com.joon.ringout.presentation.records.RecordsViewModel
import androidx.navigation3.runtime.EntryProviderScope
import com.joon.ringout.domain.auth.AuthSessionState
import com.joon.ringout.domain.member.MemberRepository
import com.joon.ringout.presentation.profilechange.ProfileChangeRoute
import com.joon.ringout.ThemeMode
import com.joon.ringout.alarm.ActiveAlarmMission
import com.joon.ringout.alarm.AlarmController
import com.joon.ringout.alarm.AlarmScheduleRequest
import com.joon.ringout.presentation.home.HomeRoute
import com.joon.ringout.presentation.home.HomeViewModel
import com.joon.ringout.presentation.mypage.MyPageRoute
import com.joon.ringout.presentation.mypage.MyPageViewModel
import com.joon.ringout.presentation.roomcreate.RoomCreateRoute
import com.joon.ringout.presentation.roomcreate.RoomCreateViewModel
import com.joon.ringout.presentation.roomcreate.model.RoomCreateDraft

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
    onJoinRoom: (String) -> Unit,
    onRoomCreateDraft: (RoomCreateDraft) -> Unit,
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
        RoomListRoute(
            viewModel = viewModelScopes.get(AppRoute.Social, RoomListViewModel::class),
            authSessionState = authSessionState,
            onCreateRoom = { navigationState.navigate(AppRoute.RoomCreate) },
            onLoginClick = { navigationState.navigate(AppRoute.Login) },
            onRoomClick = { roomId -> navigationState.navigate(AppRoute.RoomDetail(roomId)) },
        )
    }
    entry<AppRoute.RoomCreate>(clazzContentKey = AppRoute::viewModelStoreKey) { route ->
        val viewModel = viewModelScopes.get(route, RoomCreateViewModel::class)
        RoomCreateRoute(
            viewModel = viewModel,
            onBackClick = { navigationState.popBackStack(route) },
            onCreateDraft = onRoomCreateDraft,
        )
    }
    entry<AppRoute.RoomDetail>(clazzContentKey = AppRoute::viewModelStoreKey) { route ->
        val roomListViewModel = viewModelScopes.get(AppRoute.Social, RoomListViewModel::class)
        val roomListUiState = roomListViewModel.uiState
        RoomDetailRoute(
            room = roomListUiState.allRooms.firstOrNull { it.id == route.roomId },
            isLoading = roomListViewModel.isRoomListUninitialized || roomListUiState.isLoadingAllRooms,
            errorMessage = roomListUiState.allRoomsErrorMessage,
            authSessionState = authSessionState,
            onRouteVisible = roomListViewModel::onRouteVisible,
            onBackClick = { navigationState.popBackStack(route) },
            onLoginClick = { navigationState.navigate(AppRoute.Login) },
            onJoinRoom = onJoinRoom,
            onRetryRooms = roomListViewModel::onRetryRooms,
        )
    }
    entry<AppRoute.Records>(clazzContentKey = AppRoute::viewModelStoreKey) {
        RecordsRoute(viewModelScopes.get(AppRoute.Records, RecordsViewModel::class))
    }

    entry<AppRoute.NicknameChange>(clazzContentKey = AppRoute::viewModelStoreKey) {
        val myPage = checkNotNull(myPageViewModel)
        ProfileChangeRoute(
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
