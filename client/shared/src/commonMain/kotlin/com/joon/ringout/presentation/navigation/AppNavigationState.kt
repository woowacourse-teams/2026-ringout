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
        AppNavigationState(
            routes = backStack,
            guestOnlyMode = true,
        ).apply {
            normalizeForGuestMode()
        }
    }
}

/** 화면 이동 요청과 표시 경로를 타입 안전한 Navigation 3 백스택으로 관리한다. */
internal class AppNavigationState(
    private val routes: NavBackStack<AppRoute> = NavBackStack(AppRoute.Home),
    private val guestOnlyMode: Boolean = false,
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
        if (guestOnlyMode) {
            when (route) {
                AppRoute.Login,
                AppRoute.TermsAgreement,
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
            AppRoute.Onboarding -> error("온보딩은 앱 내비게이션 진입 전에 관리한다: $route")
            is AppRoute.AlarmRinging ->
                error("알람 울림 경로는 플랫폼 상태에서만 표시한다: $route")
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

    /** 이전 로그인 버전에서 복원된 계정 화면을 비로그인 마이페이지로 되돌린다. */
    internal fun normalizeForGuestMode() {
        when (requestedRoute) {
            AppRoute.Login,
            AppRoute.TermsAgreement,
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
