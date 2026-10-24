package com.joon.ringout.presentation.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.joon.ringout.domain.auth.AuthSessionState
import com.joon.ringout.platform.IosDestinationMapListener
import com.joon.ringout.platform.IosFcmTokenListener
import com.joon.ringout.platform.IosNativeServices
import com.joon.ringout.platform.IosRoomActivityPushListener
import com.joon.ringout.platform.LocalIosNativeServices
import com.joon.ringout.presentation.navigation.AppRoute
import com.joon.ringout.presentation.navigation.withEffectComposition
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class HomeNotificationPermissionEffectTest {
    @Test
    fun `기존 사용자의 인증 복원이 끝나면 홈에서 알림 조건부 요청을 실행한다`() = runTest {
        val services = NotificationRequestNativeServices()
        var authState by mutableStateOf(AuthSessionState.Restoring)
        var revision by mutableStateOf(0)
        var displayedRevision = -1

        withEffectComposition(
            content = {
                val currentRevision = revision
                CompositionLocalProvider(LocalIosNativeServices provides services) {
                    HomeNotificationPermissionEffect(authState, AppRoute.Home, false, false)
                }
                SideEffect { displayedRevision = currentRevision }
            },
        ) { drain ->
            assertEquals(0, services.requestChecks)

            authState = AuthSessionState.Authenticated
            drain()
            assertEquals(1, services.requestChecks)

            revision++
            drain()
            assertEquals(1, displayedRevision)
            assertEquals(1, services.requestChecks)
        }
    }

    @Test
    fun `비로그인과 재인증 상태에서는 알림을 요청하지 않는다`() = runTest {
        val services = NotificationRequestNativeServices()
        var authState by mutableStateOf(AuthSessionState.Unauthenticated)

        withEffectComposition(
            content = {
                CompositionLocalProvider(LocalIosNativeServices provides services) {
                    HomeNotificationPermissionEffect(authState, AppRoute.Home, false, false)
                }
            },
        ) { drain ->
            assertEquals(0, services.requestChecks)
            authState = AuthSessionState.ReauthenticationRequired
            drain()
            assertEquals(0, services.requestChecks)
        }
    }

    @Test
    fun `로그인 화면이나 알람 울림에서는 요청하지 않고 홈에 도착한 뒤 확인한다`() = runTest {
        val services = NotificationRequestNativeServices()
        var route by mutableStateOf<AppRoute>(AppRoute.Login)

        withEffectComposition(
            content = {
                CompositionLocalProvider(LocalIosNativeServices provides services) {
                    HomeNotificationPermissionEffect(AuthSessionState.Authenticated, route, false, false)
                }
            },
        ) { drain ->
            assertEquals(0, services.requestChecks)
            route = AppRoute.AlarmRinging("alarm-1")
            drain()
            assertEquals(0, services.requestChecks)
            route = AppRoute.ActiveAlarmTracking("occurrence-1")
            drain()
            assertEquals(0, services.requestChecks)
            route = AppRoute.Home
            drain()
            assertEquals(1, services.requestChecks)
        }
    }

    @Test
    fun `알람 생성 가이드가 끝날 때까지 홈의 알림 요청을 보류한다`() = runTest {
        val services = NotificationRequestNativeServices()
        var isGuidePending by mutableStateOf(true)

        withEffectComposition(
            content = {
                CompositionLocalProvider(LocalIosNativeServices provides services) {
                    HomeNotificationPermissionEffect(AuthSessionState.Authenticated, AppRoute.Home, isGuidePending, false)
                }
            },
        ) { drain ->
            assertEquals(0, services.requestChecks)
            isGuidePending = false
            drain()
            assertEquals(1, services.requestChecks)
        }
    }

    @Test
    fun `약관이나 미션 강제 종료가 홈 조작을 막으면 알림 요청을 보류한다`() = runTest {
        val services = NotificationRequestNativeServices()
        var isServiceBlocked by mutableStateOf(true)

        withEffectComposition(
            content = {
                CompositionLocalProvider(LocalIosNativeServices provides services) {
                    HomeNotificationPermissionEffect(AuthSessionState.Authenticated, AppRoute.Home, false, isServiceBlocked)
                }
            },
        ) { drain ->
            assertEquals(0, services.requestChecks)
            isServiceBlocked = false
            drain()
            assertEquals(1, services.requestChecks)
        }
    }
}

/** Only the conditional native notification check is permitted from the Home effect. */
private class NotificationRequestNativeServices : IosNativeServices {
    var requestChecks = 0

    override fun requestNotificationPermissionIfNeeded() { requestChecks++ }
    override fun appleSignInService(): Nothing = error("사용하지 않는 서비스")
    override fun kakaoSignInService(): Nothing = error("사용하지 않는 서비스")
    override fun isMapsAvailable(): Nothing = error("사용하지 않는 서비스")
    override fun isPlacesAvailable(): Nothing = error("사용하지 않는 서비스")
    override fun createDestinationMapController(
        initialLatitude: Double,
        initialLongitude: Double,
        listener: IosDestinationMapListener,
    ): Nothing = error("사용하지 않는 서비스")
    override fun createActiveMissionMapController(
        destinationLatitude: Double,
        destinationLongitude: Double,
    ): Nothing = error("사용하지 않는 서비스")
    override fun destinationSearchService(): Nothing = error("사용하지 않는 서비스")
    override fun destinationLocationService(): Nothing = error("사용하지 않는 서비스")
    override fun alarmAuthorizationState(): Nothing = error("사용하지 않는 서비스")
    override fun normalizeAlarmId(id: String): Nothing = error("사용하지 않는 서비스")
    override fun alarmScheduler(): Nothing = error("사용하지 않는 서비스")
    override fun alarmMissionCommands(): Nothing = error("사용하지 않는 서비스")
    override fun alarmMissionEventInbox(): Nothing = error("사용하지 않는 서비스")
    override fun missionArrivalNotifier(): Nothing = error("사용하지 않는 서비스")
    override fun missionLocationService(): Nothing = error("사용하지 않는 서비스")
    override fun googleSignInService(): Nothing = error("사용하지 않는 서비스")
    override fun analyticsTracker(): Nothing = error("사용하지 않는 서비스")
    override fun latestFcmToken(): Nothing = error("사용하지 않는 서비스")
    override fun observeFcmToken(listener: IosFcmTokenListener): Nothing = error("사용하지 않는 서비스")
    override fun observeRoomActivityPush(listener: IosRoomActivityPushListener): Nothing = error("사용하지 않는 서비스")
    override fun removeRoomActivityPushObserver(observerId: Long): Nothing = error("사용하지 않는 서비스")
    override fun acknowledgeRoomActivityPush(requestId: String): Nothing = error("사용하지 않는 서비스")
    override fun onboardingPermissionService(): Nothing = error("홈에서는 설정 화면으로 이동하는 온보딩 요청을 사용하지 않습니다")
}
