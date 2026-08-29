package com.joon.ringout.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSerializable
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.serialization.NavBackStackSerializer
import com.joon.ringout.AppScreen

@Composable
internal fun rememberAppNavigationState(): AppNavigationState {
    val backStack = rememberSerializable(
        serializer = NavBackStackSerializer(AppRoute.serializer()),
    ) {
        NavBackStack<AppRoute>(AppRoute.Home)
    }
    return remember(backStack) {
        AppNavigationState(backStack)
    }
}

/**
 * 화면 이동 요청은 타입 안전한 Navigation 3 백스택으로 관리한다. 알람 울림과 진행 중인
 * 미션 화면의 렌더링은 내비게이션 그래프 이전이 끝날 때까지 기존 화면 분기를 사용한다.
 */
internal class AppNavigationState(
    private val routes: NavBackStack<AppRoute> = NavBackStack(AppRoute.Home),
) {
    val backStack: List<AppRoute>
        get() = routes

    val editorRoute: AppRoute?
        get() = routes.lastOrNull { it == AppRoute.AddAlarm || it is AppRoute.EditAlarm }

    val requestedScreen: AppScreen
        get() = when (routes.last()) {
            AppRoute.Onboarding -> error("Onboarding is managed before app navigation starts")
            AppRoute.Home -> AppScreen.Home
            AppRoute.MyPage -> AppScreen.MyPage
            AppRoute.NicknameChange -> AppScreen.NicknameChange
            AppRoute.Login -> AppScreen.Login
            AppRoute.TermsAgreement -> AppScreen.TermsAgreement
            AppRoute.AddAlarm -> AppScreen.AddAlarm
            is AppRoute.EditAlarm -> AppScreen.EditAlarm
            is AppRoute.Destination -> AppScreen.Destination
            AppRoute.AlarmSound -> AppScreen.AlarmSound
            is AppRoute.AlarmRinging -> AppScreen.AlarmRinging
            is AppRoute.ActiveAlarmTracking -> AppScreen.ActiveAlarmTracking
        }

    /**
     * 요청한 목적지 스택이 현재 백스택과 같으면 singleTop으로 기존 항목을 유지한다.
     * 경로의 식별자가 다르면 같은 화면 유형이어도 최상위 항목을 교체한다.
     */
    fun navigate(route: AppRoute) {
        val destinationStack = when (route) {
            AppRoute.Home -> listOf(AppRoute.Home)
            AppRoute.MyPage -> listOf(AppRoute.Home, AppRoute.MyPage)
            AppRoute.NicknameChange ->
                listOf(AppRoute.Home, AppRoute.MyPage, AppRoute.NicknameChange)
            // 로그아웃이나 재인증 후에도 로그인 화면에서 뒤로 가면 마이페이지로 돌아간다.
            AppRoute.Login -> listOf(AppRoute.Home, AppRoute.MyPage, AppRoute.Login)
            AppRoute.TermsAgreement ->
                listOf(AppRoute.Home, AppRoute.MyPage, AppRoute.Login, AppRoute.TermsAgreement)
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
            AppRoute.Onboarding,
            is AppRoute.AlarmRinging,
            -> error("Route has not been migrated: $route")
        }
        // 동일 경로 요청은 현재 항목과 화면 상태를 그대로 사용한다.
        if (routes.toList() == destinationStack) return

        // 나머지 이동은 공통 백스택 항목과 상태를 유지하고 달라진 뒷부분만 교체한다.
        val sharedSize = routes.zip(destinationStack).takeWhile { (a, b) -> a == b }.size
        routes.subList(sharedSize, routes.size).clear()
        routes.addAll(destinationStack.drop(sharedSize))
    }

    /** AppScreen 제거가 끝날 때까지 식별자가 필요 없는 기존 콜백을 연결하는 어댑터다. */
    fun navigate(screen: AppScreen) {
        when (screen) {
            AppScreen.Home -> navigate(AppRoute.Home)
            AppScreen.MyPage, AppScreen.Settings -> navigate(AppRoute.MyPage)
            AppScreen.NicknameChange -> navigate(AppRoute.NicknameChange)
            AppScreen.Login -> navigate(AppRoute.Login)
            AppScreen.TermsAgreement -> navigate(AppRoute.TermsAgreement)
            AppScreen.AddAlarm -> navigate(AppRoute.AddAlarm)
            AppScreen.AlarmSound -> navigate(AppRoute.AlarmSound)
            AppScreen.EditAlarm,
            AppScreen.Destination,
            AppScreen.AlarmRinging,
            AppScreen.ActiveAlarmTracking,
            ->
                throw IllegalArgumentException("Use an AppRoute with the required identifier for $screen")
        }
    }

    fun popBackStack(from: AppRoute = routes.last()) {
        // 이미 벗어난 백스택 항목의 콜백은 무시하고, 루트 항목은 제거하지 않는다.
        if (routes.size > 1 && isCurrentRoute(from)) {
            routes.removeLastOrNull()
        }
    }

    fun isCurrentRoute(route: AppRoute): Boolean = routes.last() == route

    fun retainedRoutes(screen: AppScreen): List<AppRoute> =
        (routes.filterNot(AppRoute::usesLegacyContent) + routesForScreen(screen).orEmpty()).distinct()

    /** 요청된 백스택을 변경하지 않고 기존 화면 표시 정책에 맞는 경로를 반환한다. */
    fun routesForScreen(screen: AppScreen): List<AppRoute>? {
        val route = when (screen) {
            AppScreen.Home -> AppRoute.Home
            AppScreen.MyPage, AppScreen.Settings -> AppRoute.MyPage
            AppScreen.NicknameChange -> AppRoute.NicknameChange
            AppScreen.Login -> AppRoute.Login
            AppScreen.TermsAgreement -> AppRoute.TermsAgreement
            AppScreen.AddAlarm -> AppRoute.AddAlarm
            AppScreen.EditAlarm -> routes.lastOrNull { it is AppRoute.EditAlarm } ?: return null
            AppScreen.Destination -> routes.lastOrNull { it is AppRoute.Destination } ?: return null
            AppScreen.AlarmSound -> routes.lastOrNull { it == AppRoute.AlarmSound } ?: return null
            else -> return null
        }
        val index = routes.indexOf(route)
        // 기존 화면 전환 부수 효과가 실행되기 전에 로그인 화면이 우선 표시될 수 있다.
        return if (index >= 0) routes.take(index + 1) else listOf(route)
    }
}

private fun AppRoute.usesLegacyContent(): Boolean =
    this is AppRoute.AlarmRinging || this is AppRoute.ActiveAlarmTracking
