package com.joon.ringout.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavBackStack
import com.joon.ringout.LocalAppNavigationStoreProviders
import com.joon.ringout.domain.missionhistory.MissionDate
import com.joon.ringout.domain.push.RoomActivityPushTarget

@Composable
internal fun rememberAppNavigationState(sessionIdentity: Any? = null): AppNavigationState =
    rememberNavigationState("service", sessionIdentity, authenticationOnlyMode = false)

@Composable
internal fun rememberAuthOnlyNavigationState(): AppNavigationState =
    rememberNavigationState("auth", sessionIdentity = null, authenticationOnlyMode = true)

@Composable
private fun rememberNavigationState(
    scopeKey: String,
    sessionIdentity: Any?,
    authenticationOnlyMode: Boolean,
): AppNavigationState {
    val stores = LocalAppNavigationStoreProviders.current?.getValue(scopeKey)
    val createState = {
        AppNavigationState(
            routes = if (authenticationOnlyMode) NavBackStack(AppRoute.Login) else NavBackStack(AppRoute.Home),
            authenticationOnlyMode = authenticationOnlyMode,
        )
    }
    // 호스트가 있는 Android에서는 차단 중에도 백스택을 해당 화면 모델과 함께 유지한다.
    return if (stores != null) {
        val owner = stores.getOrCreateOwner("ringout-navigation-state")
        viewModel(viewModelStoreOwner = owner) { NavigationStateViewModel(createState()) }.navigationState
    } else {
        remember(sessionIdentity) { createState() }
    }
}

private class NavigationStateViewModel(val navigationState: AppNavigationState) : ViewModel()

/** 화면 이동 요청과 표시 경로를 타입 안전한 Navigation 3 백스택으로 관리한다. */
internal class AppNavigationState(
    private val routes: NavBackStack<AppRoute> = NavBackStack(AppRoute.Home),
    private val guestOnlyMode: Boolean = false,
    private val authenticationOnlyMode: Boolean = false,
) {
    init {
        check(
            authenticationOnlyMode || routes.none {
                it == AppRoute.Login || it == AppRoute.TermsAgreement
            },
        ) { "Authentication routes must use the root authentication navigation state." }
    }

    // Not saved: a restored stack must never replay an old back animation.
    private var lastBackTransition: Pair<AppRoute, AppRoute>? by mutableStateOf(null)

    val backStack: List<AppRoute>
        get() = routes

    val editorRoute: AppRoute?
        get() = routes.lastOrNull { it == AppRoute.AddAlarm || it is AppRoute.EditAlarm }

    val requestedRoute: AppRoute
        get() = routes.last()

    /**
     * 요청한 목적지 스택이 현재 백스택과 같으면 singleTop으로 기존 항목을 유지한다.
     * 경로의 식별자가 다르면 같은 화면 유형이어도 최상위 항목을 교체한다.
     */
    fun navigate(route: AppRoute) {
        if (authenticationOnlyMode) {
            val destinationStack = when (route) {
                AppRoute.Login -> listOf(AppRoute.Login)
                AppRoute.TermsAgreement -> listOf(AppRoute.Login, AppRoute.TermsAgreement)
                else -> return
            }
            replaceBackStack(destinationStack)
            return
        }
        check(route != AppRoute.Login && route != AppRoute.TermsAgreement) {
            "Authentication routes must use the root authentication navigation state."
        }
        if (guestOnlyMode) {
            when (route) {
                AppRoute.NicknameChange,
                -> {
                    navigate(AppRoute.MyPage)
                    return
                }

                else -> Unit
            }
        }
        val destinationStack = when (route) {
            AppRoute.Home -> listOf(AppRoute.Home)
            AppRoute.Forest -> listOf(AppRoute.Home, AppRoute.Forest)
            AppRoute.MyPage -> listOf(AppRoute.Home, AppRoute.MyPage)
            AppRoute.Social -> listOf(AppRoute.Home, AppRoute.Social)
            AppRoute.RoomCreate -> listOf(AppRoute.Home, AppRoute.Social, AppRoute.RoomCreate)
            is AppRoute.RoomDetail -> listOf(AppRoute.Home, AppRoute.Social, route)
            is AppRoute.RoomHome -> listOf(AppRoute.Home, AppRoute.Social, route)
            is AppRoute.RoomActivity -> when {
                !route.isValid() -> routes.toList()
                routes.lastOrNull() == AppRoute.RoomHome(route.roomId) -> routes.toList() + route
                routes.lastOrNull() is AppRoute.RoomActivity &&
                    routes.dropLast(1).lastOrNull() == AppRoute.RoomHome(route.roomId) ->
                    if (routes.lastOrNull() == route) routes.toList() else routes.dropLast(1) + route
                else -> routes.toList()
            }
            is AppRoute.RoomEdit -> when {
                routes.lastOrNull() is AppRoute.RoomEdit -> routes.dropLast(1) + route
                else -> routes.toList() + route
            }
            is AppRoute.RoomMemberManagement -> when {
                routes.lastOrNull() is AppRoute.RoomMemberManagement -> routes.dropLast(1) + route
                else -> routes.toList() + route
            }
            AppRoute.Records -> listOf(AppRoute.Home, AppRoute.Records)
            is AppRoute.RecordsDetail -> if (route.isValid()) {
                listOf(AppRoute.Home, AppRoute.Records, route)
            } else routes.toList()
            is AppRoute.RecordShare -> if (route.isValid()) {
                listOf(AppRoute.Home, AppRoute.Records, AppRoute.RecordsDetail(route.date), route)
            } else routes.toList()
            AppRoute.NicknameChange ->
                listOf(AppRoute.Home, AppRoute.MyPage, AppRoute.NicknameChange)
            AppRoute.Login,
            AppRoute.TermsAgreement,
            -> error("Authentication routes must use the root authentication navigation state.")
            AppRoute.AddAlarm -> listOf(AppRoute.Home, AppRoute.AddAlarm)
            is AppRoute.EditAlarm -> listOf(AppRoute.Home, route)
            is AppRoute.Destination,
            AppRoute.AlarmSound,
            -> {
                val parent = requireNotNull(editorRoute) { "An alarm picker requires an editor parent" }
                routes.take(routes.indexOf(parent) + 1) + route
            }
            is AppRoute.ActiveAlarmTracking ->
                routes.filterNot { it is AppRoute.ActiveAlarmTracking } + route
            is AppRoute.Arrival -> listOf(AppRoute.Home, route)
            is AppRoute.ArrivalShare -> listOf(AppRoute.Home, AppRoute.Arrival(route.occurrenceId), route)
            AppRoute.Onboarding -> error("온보딩은 앱 내비게이션 진입 전에 관리한다: $route")
            is AppRoute.AlarmRinging ->
                error("알람 울림 경로는 플랫폼 상태에서만 표시한다: $route")
        }
        // 동일 경로 요청은 현재 항목과 화면 상태를 그대로 사용한다.
        if (routes.toList() == destinationStack) return

        replaceBackStack(destinationStack)
    }

    /** 성공한 생성/가입 화면만 RoomHome으로 바꾸며, 이미 나간 화면의 완료는 무시한다. */
    fun navigateToRoomHomeFrom(sourceRoute: AppRoute, roomId: String): Boolean {
        if (!isCurrentRoute(sourceRoute)) return false
        if (sourceRoute != AppRoute.RoomCreate && sourceRoute !is AppRoute.RoomDetail) return false
        val expectedStack = listOf(AppRoute.Home, AppRoute.Social, sourceRoute)
        if (routes.toList() != expectedStack) return false
        val destination = AppRoute.RoomHome(roomId)
        if (routes.lastOrNull() == destination) return true
        replaceBackStack(listOf(AppRoute.Home, AppRoute.Social, destination))
        return true
    }

    /** Builds the complete room activity stack for an explicit notification click. */
    fun openRoomActivityFromPush(target: RoomActivityPushTarget): Boolean {
        val activityRoute = AppRoute.RoomActivity(target.roomId, target.activityDate)
        if (!activityRoute.isValid()) return false

        val destinationStack = listOf(
            AppRoute.Home,
            AppRoute.Social,
            AppRoute.RoomHome(target.roomId),
            activityRoute,
        )
        if (routes.toList() != destinationStack) {
            replaceBackStack(destinationStack)
        }
        return true
    }

    fun popBackStack(from: AppRoute = routes.last()) {
        // 이미 벗어난 백스택 항목의 콜백은 무시하고, 루트 항목은 제거하지 않는다.
        if (routes.size > 1 && isCurrentRoute(from)) {
            lastBackTransition = from to routes[routes.lastIndex - 1]
            routes.removeLastOrNull()
        }
    }

    /** Only a nested screen's explicit pop or gesture preview animates, never a tab/reset. */
    internal fun isBackTransition(from: AppRoute, to: AppRoute): Boolean {
        if (from.mainNavigationTab() != null && to.mainNavigationTab() != null) return false
        if (from is AppRoute.AlarmRinging || from is AppRoute.ActiveAlarmTracking ||
            to is AppRoute.AlarmRinging || to is AppRoute.ActiveAlarmTracking
        ) return false
        return lastBackTransition == (from to to) ||
            (routes.lastOrNull() == from && routes.getOrNull(routes.lastIndex - 1) == to)
    }

    fun isCurrentRoute(route: AppRoute): Boolean = routes.last() == route

    private fun replaceBackStack(destinationStack: List<AppRoute>) {
        lastBackTransition = null
        // 공통 백스택 항목과 화면 상태는 유지하고 달라진 뒷부분만 교체한다.
        val sharedSize = routes.zip(destinationStack).takeWhile { (a, b) -> a == b }.size
        routes.subList(sharedSize, routes.size).clear()
        routes.addAll(destinationStack.drop(sharedSize))
    }

    private fun AppRoute.RoomActivity.isValid(): Boolean =
        roomId.toLongOrNull()?.let { it > 0L } == true &&
            runCatching { MissionDate.parse(activityDate) }.getOrNull()?.iso8601 == activityDate

    private fun AppRoute.RecordsDetail.isValid(): Boolean =
        runCatching { MissionDate.parse(date) }.getOrNull()?.iso8601 == date

    private fun AppRoute.RecordShare.isValid(): Boolean =
        AppRoute.RecordsDetail(date).isValid() && groupKey.isNotBlank() && successRecordKey.isNotBlank() &&
            (occurrenceId == null || occurrenceId.isNotBlank())

    /** 아직 제공하지 않는 닉네임 수정 화면은 마이페이지로 되돌린다. */
    internal fun normalizeForGuestMode() {
        when (requestedRoute) {
            AppRoute.NicknameChange,
            -> navigate(AppRoute.MyPage)

            else -> Unit
        }
    }

    /** 실제 백스택과 플랫폼에서 우선 표시하는 경로의 화면 상태를 함께 유지한다. */
    fun retainedRoutes(displayedRoute: AppRoute): List<AppRoute> =
        (routes + displayedRoute).distinct()

    /** 현재 화면에 표시할 경로를 반환한다. 런타임 화면은 아래 백스택을 노출하지 않는다. */
    fun routesForDisplayedRoute(displayedRoute: AppRoute): List<AppRoute>? = when {
        displayedRoute is AppRoute.AlarmRinging -> listOf(displayedRoute)
        displayedRoute is AppRoute.ActiveAlarmTracking && isCurrentRoute(displayedRoute) ->
            listOf(displayedRoute)
        isCurrentRoute(displayedRoute) -> routes.toList()
        else -> null
    }
}
