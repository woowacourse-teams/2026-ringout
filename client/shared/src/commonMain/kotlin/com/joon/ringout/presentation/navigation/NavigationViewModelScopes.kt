package com.joon.ringout.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.ViewModelStoreProvider
import androidx.lifecycle.viewmodel.compose.rememberViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.rememberViewModelStoreProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.joon.ringout.presentation.roomlist.RoomListViewModel
import com.joon.ringout.presentation.records.RecordsViewModel
import com.joon.ringout.di.AppContainer
import com.joon.ringout.domain.room.RoomSummary
import com.joon.ringout.domain.missionhistory.GetMissionSuccessDates
import com.joon.ringout.domain.missionhistory.GetRecordsHistory
import com.joon.ringout.presentation.alarmsetup.AlarmSetupViewModel
import com.joon.ringout.presentation.destination.DestinationViewModel
import com.joon.ringout.presentation.home.HomeViewModel
import com.joon.ringout.presentation.login.LoginViewModel
import com.joon.ringout.presentation.mypage.MyPageViewModel
import com.joon.ringout.presentation.mypage.currentMissionYearMonth
import com.joon.ringout.presentation.records.currentRecordsDate
import com.joon.ringout.presentation.signup.SignupViewModel
import com.joon.ringout.presentation.roomcreate.RoomCreateViewModel
import com.joon.ringout.presentation.roomedit.RoomEditViewModel
import com.joon.ringout.presentation.roomhome.RoomHomeViewModel
import com.joon.ringout.presentation.roomactivity.RoomActivityViewModel
import com.joon.ringout.presentation.roommembermanagement.RoomMemberManagementViewModel
import com.joon.ringout.presentation.roomlist.model.toRoomUiModel
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlin.reflect.KClass

/** 백스택 항목 등록과 부모 스코프를 직접 조회할 때 같은 키를 사용한다. */
internal fun AppRoute.viewModelStoreKey(): String = Json.encodeToString(AppRoute.serializer(), this)

@Composable
internal fun rememberNavigationViewModelScopes(
    appContainer: AppContainer,
    retainedRoutes: List<AppRoute>,
): NavigationViewModelScopes {
    val stores = rememberViewModelStoreProvider(key = "ringout-navigation")
    // 부모 화면이 가려져도 앱 루트의 부수 효과에서 부모 ViewModel을 사용할 수 있다.
    // 백스택에 남아 있는 경로의 소유자만 유지하고, 경로가 완전히 제거되면 항목 데코레이터가 정리한다.
    for (route in retainedRoutes) {
        key(route.viewModelStoreKey()) {
            rememberViewModelStoreOwner(
                key = route.viewModelStoreKey(),
                provider = stores,
                savedStateRegistryOwner = null,
            )
        }
    }
    val factory = remember(appContainer) { navigationViewModelFactory(appContainer) }
    return remember(stores, factory) { NavigationViewModelScopes(stores, factory) }
}

internal class NavigationViewModelScopes(
    val storeProvider: ViewModelStoreProvider,
    private val factory: ViewModelProvider.Factory,
) {
    fun <T : ViewModel> get(route: AppRoute, modelClass: KClass<T>): T {
        val owner = storeProvider.getOrCreateOwner(
            key = route.viewModelStoreKey(),
            savedStateRegistryOwner = null,
        )
        return ViewModelProvider.create(owner, factory)[modelClass]
    }

    fun createAlarmEditorNavigation(
        navigationState: AppNavigationState,
        parent: AppRoute,
    ): AlarmEditorNavigation {
        require(parent == AppRoute.AddAlarm || parent is AppRoute.EditAlarm)
        return AlarmEditorNavigation(
            navigationState,
            get(parent, AlarmSetupViewModel::class),
            get(parent, DestinationViewModel::class),
        )
    }
}

private fun navigationViewModelFactory(container: AppContainer): ViewModelProvider.Factory =
    viewModelFactory {
        initializer { HomeViewModel() }
        initializer {
            RoomListViewModel(
                analytics = container.productAnalyticsRecorder,
                loadRooms = {
                    try {
                        Result.success(container.roomRepository.getRooms().map(RoomSummary::toRoomUiModel))
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Throwable) {
                        Result.failure(error)
                    }
                },
                createRoom = { input ->
                    try {
                        Result.success(container.roomRepository.createRoom(input))
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Throwable) {
                        Result.failure(error)
                    }
                },
                joinRoom = { roomId ->
                    try {
                        Result.success(container.roomRepository.joinRoom(roomId))
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Throwable) {
                        Result.failure(error)
                    }
                },
                authSession = container.authSession,
            )
        }
        initializer { RoomCreateViewModel() }
        initializer {
            RoomEditViewModel(
                loadRoom = container.roomRepository::getRoom,
                updateRoom = container.roomRepository::updateRoom,
                authSession = container.authSession,
                analytics = container.productAnalyticsRecorder,
            )
        }
        initializer {
            RoomMemberManagementViewModel(
                loadMembers = container.roomRepository::getMembersForManagement,
                kickMember = container.roomRepository::kickMember,
                authSession = container.authSession,
            )
        }
        initializer {
            RoomHomeViewModel(
                analytics = container.productAnalyticsRecorder,
                loadRoom = container.roomRepository::getRoom,
                loadRecords = container.roomRepository::getRoomRecords,
                deleteRoom = container.roomRepository::deleteRoom,
                leaveRoom = container.roomRepository::leaveRoom,
                authSession = container.authSession,
            )
        }
        initializer {
            RoomActivityViewModel(
                loadMembers = container.roomRepository::getMemberMovements,
                loadRecords = container.roomRepository::getRoomRecords,
                authSession = container.authSession,
            )
        }
        initializer {
            RecordsViewModel(
                activityRepository = container.alarmActivityRepository,
                getRecordsHistory = GetRecordsHistory(container.missionHistoryRepository),
                initialDate = currentRecordsDate(),
                currentDate = ::currentRecordsDate,
            )
        }
        initializer {
            MyPageViewModel(
                getMissionSuccessDates = GetMissionSuccessDates(container.missionHistoryRepository),
                memberRepository = container.memberRepository,
                authRepository = container.authRepository,
                productAnalyticsRecorder = container.productAnalyticsRecorder,
                initialMonth = currentMissionYearMonth(),
            )
        }
        initializer {
            LoginViewModel(
                authRepository = container.authRepository,
                productAnalyticsRecorder = container.productAnalyticsRecorder,
            )
        }
        initializer {
            SignupViewModel(
                authRepository = container.authRepository,
                destinationRepository = container.destinationRepository,
                productAnalyticsRecorder = container.productAnalyticsRecorder,
            )
        }
        initializer { AlarmSetupViewModel() }
        initializer {
            DestinationViewModel(
                repository = container.destinationRepository,
                productAnalyticsRecorder = container.productAnalyticsRecorder,
            )
        }
    }
