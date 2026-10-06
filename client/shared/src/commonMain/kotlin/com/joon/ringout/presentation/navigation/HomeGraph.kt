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
import com.joon.ringout.domain.room.RoomCreateInput
import com.joon.ringout.presentation.roomedit.RoomEditRoute
import com.joon.ringout.presentation.roomedit.RoomEditViewModel
import com.joon.ringout.presentation.roomedit.model.RoomEditDraft
import com.joon.ringout.presentation.roomhome.RoomHomeRoute
import com.joon.ringout.presentation.roomhome.RoomHomeViewModel
import com.joon.ringout.presentation.roommembermanagement.RoomMemberManagementRoute
import com.joon.ringout.presentation.roommembermanagement.RoomMemberManagementViewModel
import com.joon.ringout.presentation.roomlist.model.RoomMutationSource
import androidx.compose.ui.graphics.ImageBitmap

// 홈과 마이페이지는 각 백스택 항목의 저장소를 사용한다.
internal fun EntryProviderScope<AppRoute>.homeGraph(
    navigationState: AppNavigationState,
    homeViewModel: HomeViewModel,
    viewModelScopes: NavigationViewModelScopes,
    sessionIdentity: Any?,
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
    onJoinRoom: (RoomMutationSource) -> Unit,
    onRoomCreateDraft: (RoomMutationSource, RoomCreateInput) -> Unit,
    onRoomEditDraft: (RoomEditDraft, ImageBitmap?) -> Unit,
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
            onLoginClick = { navigationState.navigate(AppRoute.Login) },
            onEditProfileClick = { navigationState.navigate(AppRoute.NicknameChange) },
        )
    }

    entry<AppRoute.Social>(clazzContentKey = AppRoute::viewModelStoreKey) {
        RoomListRoute(
            viewModel = viewModelScopes.get(AppRoute.Social, RoomListViewModel::class),
            authSessionState = authSessionState,
            sessionIdentity = sessionIdentity,
            onCreateRoom = { navigationState.navigate(AppRoute.RoomCreate) },
            onLoginClick = { navigationState.navigate(AppRoute.Login) },
            onRoomClick = { roomId -> navigationState.navigate(AppRoute.RoomDetail(roomId)) },
            onJoinedRoomClick = { roomId -> navigationState.navigate(AppRoute.RoomHome(roomId)) },
        )
    }
    entry<AppRoute.RoomCreate>(clazzContentKey = AppRoute::viewModelStoreKey) { route ->
        val viewModel = viewModelScopes.get(route, RoomCreateViewModel::class)
        val roomListViewModel = viewModelScopes.get(AppRoute.Social, RoomListViewModel::class)
        RoomCreateRoute(
            viewModel = viewModel,
            authSessionState = authSessionState,
            sessionIdentity = sessionIdentity,
            onBackClick = { navigationState.popBackStack(route) },
            mutationState = roomListViewModel.mutationState,
            onMutationSourceVisible = roomListViewModel::onMutationSourceVisible,
            onMutationSourceHidden = roomListViewModel::onMutationSourceHidden,
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
            sessionIdentity = sessionIdentity,
            onRouteVisible = roomListViewModel::onRouteVisible,
            mutationState = roomListViewModel.mutationState,
            onMutationSourceVisible = roomListViewModel::onMutationSourceVisible,
            onMutationSourceHidden = roomListViewModel::onMutationSourceHidden,
            onBackClick = { navigationState.popBackStack(route) },
            onLoginClick = { navigationState.navigate(AppRoute.Login) },
            onJoinRoom = onJoinRoom,
            onDetailViewed = roomListViewModel::recordDetailViewed,
            onRetryRooms = roomListViewModel::onRetryRooms,
        )
    }
    entry<AppRoute.RoomHome>(clazzContentKey = AppRoute::viewModelStoreKey) { route ->
        val roomHomeViewModel = viewModelScopes.get(route, RoomHomeViewModel::class)
        val roomListViewModel = viewModelScopes.get(AppRoute.Social, RoomListViewModel::class)
        RoomHomeRoute(
            viewModel = roomHomeViewModel,
            roomId = route.roomId,
            authSessionState = authSessionState,
            sessionIdentity = sessionIdentity,
            onBackClick = { navigationState.popBackStack(route) },
            onEditRoomClick = { navigationState.navigate(AppRoute.RoomEdit(route.roomId)) },
            onManageMembersClick = { navigationState.navigate(AppRoute.RoomMemberManagement(route.roomId)) },
            onMenuActionSucceeded = { completion ->
                if (completion.sessionIdentity === sessionIdentity) {
                    when (completion.actionType) {
                        com.joon.ringout.presentation.roomhome.RoomHomeActionType.Delete ->
                            roomListViewModel.onRoomDeleted(completion.roomId, completion.sessionIdentity)
                        com.joon.ringout.presentation.roomhome.RoomHomeActionType.Leave -> {
                            roomListViewModel.onRoomLeft(completion.roomId, completion.sessionIdentity)
                            if (navigationState.isCurrentRoute(route)) navigationState.navigate(AppRoute.Home)
                        }
                    }
                }
            },
            onMenuActionHomeClick = { completion ->
                if (
                    completion.sessionIdentity === sessionIdentity &&
                    navigationState.isCurrentRoute(route)
                ) {
                    navigationState.navigate(AppRoute.Home)
                }
            },
            onMenuActionNeedsListRefresh = { roomListViewModel.onRetryRooms() },
            onRetry = roomHomeViewModel::onRetry,
        )
    }
    entry<AppRoute.RoomMemberManagement>(clazzContentKey = AppRoute::viewModelStoreKey) { route ->
        val roomHomeViewModel = viewModelScopes.get(AppRoute.RoomHome(route.roomId), RoomHomeViewModel::class)
        RoomMemberManagementRoute(
            viewModel = viewModelScopes.get(route, RoomMemberManagementViewModel::class),
            roomId = route.roomId,
            authSessionState = authSessionState,
            sessionIdentity = sessionIdentity,
            onBackClick = {
                roomHomeViewModel.refreshRoomDetails()
                navigationState.popBackStack(route)
            },
        )
    }
    entry<AppRoute.RoomEdit>(clazzContentKey = AppRoute::viewModelStoreKey) { route ->
        val roomListViewModel = viewModelScopes.get(AppRoute.Social, RoomListViewModel::class)
        val roomListUiState = roomListViewModel.uiState
        val roomHomeViewModel = viewModelScopes.get(AppRoute.RoomHome(route.roomId), RoomHomeViewModel::class)
        RoomEditRoute(
            roomId = route.roomId,
            originalRoom = roomListUiState.allRooms.firstOrNull { it.id == route.roomId }
                ?: roomHomeViewModel.uiState.value.room?.takeIf { it.id == route.roomId },
            isLoading = roomListViewModel.isRoomListUninitialized || roomListUiState.isLoadingAllRooms,
            loadError = roomListUiState.allRoomsErrorMessage,
            viewModel = viewModelScopes.get(route, RoomEditViewModel::class),
            onRouteVisible = { roomListViewModel.onRouteVisible(authSessionState) },
            onRetry = roomListViewModel::onRetryRooms,
            onBackClick = { navigationState.popBackStack(route) },
            onDraft = onRoomEditDraft,
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
            onBackClick = {
                myPage.refreshProfileFromCache()
                navigationState.popBackStack(AppRoute.NicknameChange)
            },
            onNicknameChanged = { nickname ->
                myPage.onNicknameUpdated(nickname)
                myPage.refreshProfileFromCache()
                navigationState.popBackStack(AppRoute.NicknameChange)
            },
        )
    }
}
