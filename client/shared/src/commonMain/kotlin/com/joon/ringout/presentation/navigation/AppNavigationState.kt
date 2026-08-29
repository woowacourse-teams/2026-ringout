package com.joon.ringout.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSerializable
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.serialization.NavBackStackSerializer

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

    val requestedRoute: AppRoute
        get() = routes.last()

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

    fun popBackStack(from: AppRoute = routes.last()) {
        // 이미 벗어난 백스택 항목의 콜백은 무시하고, 루트 항목은 제거하지 않는다.
        if (routes.size > 1 && isCurrentRoute(from)) {
            routes.removeLastOrNull()
        }
    }

    fun isCurrentRoute(route: AppRoute): Boolean = routes.last() == route

    fun retainedRoutes(displayedRoute: AppRoute): List<AppRoute> =
        (
            routes.filterNot(AppRoute::usesLegacyContent) +
                routesForDisplayedRoute(displayedRoute).orEmpty()
        ).distinct()

    /** 요청된 백스택을 변경하지 않고 기존 화면 표시 정책에 맞는 경로를 반환한다. */
    fun routesForDisplayedRoute(displayedRoute: AppRoute): List<AppRoute>? {
        if (displayedRoute.usesLegacyContent()) return null

        val index = routes.indexOf(displayedRoute)
        if (index >= 0) return routes.take(index + 1)

        return when (displayedRoute) {
            // 편집 화면은 유효한 부모와 초안 없이 표시 경로만 합성하지 않는다.
            AppRoute.Onboarding,
            is AppRoute.EditAlarm,
            is AppRoute.Destination,
            AppRoute.AlarmSound,
            -> null

            // 기존 화면 전환 부수 효과가 실행되기 전에도 우선 화면을 표시한다.
            AppRoute.Home,
            AppRoute.AddAlarm,
            AppRoute.MyPage,
            AppRoute.NicknameChange,
            AppRoute.Login,
            AppRoute.TermsAgreement,
            -> listOf(displayedRoute)

            is AppRoute.AlarmRinging,
            is AppRoute.ActiveAlarmTracking,
            -> null
        }
    }
}

private fun AppRoute.usesLegacyContent(): Boolean =
    this is AppRoute.AlarmRinging || this is AppRoute.ActiveAlarmTracking
