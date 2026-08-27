package com.joon.ringout.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSerializable
import androidx.compose.runtime.setValue
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
    val legacyScreenName = rememberSaveable { mutableStateOf<String?>(null) }
    return remember(backStack, legacyScreenName) {
        AppNavigationState(backStack, legacyScreenName)
    }
}

/**
 * 일반 화면 이동은 Navigation 3가 관리한다. 알람 울림과 진행 중인 미션 화면은
 * 이전이 완료된 백스택을 교체하지 않고, 당분간 기존 화면 이동 방식을 유지한다.
 */
internal class AppNavigationState(
    private val routes: NavBackStack<AppRoute> = NavBackStack(AppRoute.Home),
    legacyScreenName: MutableState<String?> = mutableStateOf(null),
) {
    private var legacyScreenName by legacyScreenName

    init {
        // 앞선 이전 단계에서 저장된 상태에는 기존 인증 화면 경로가 남아 있을 수 있다.
        when (this.legacyScreenName) {
            AppScreen.Login.name -> navigate(AppRoute.Login)
            AppScreen.TermsAgreement.name -> navigate(AppRoute.TermsAgreement)
            // 기존 편집 화면 경로에는 부모 식별자나 복원할 수 있는 초안이 없다.
            AppScreen.AddAlarm.name,
            AppScreen.EditAlarm.name,
            AppScreen.Destination.name,
            AppScreen.AlarmSound.name,
            -> navigate(AppRoute.Home)
        }
    }

    val backStack: List<AppRoute>
        get() = routes

    val editorRoute: AppRoute?
        get() = routes.lastOrNull { it == AppRoute.AddAlarm || it is AppRoute.EditAlarm }

    val requestedScreen: AppScreen
        get() = legacyScreenName?.let(AppScreen::valueOf) ?: when (routes.last()) {
            AppRoute.Home -> AppScreen.Home
            AppRoute.MyPage -> AppScreen.MyPage
            AppRoute.NicknameChange -> AppScreen.NicknameChange
            AppRoute.Login -> AppScreen.Login
            AppRoute.TermsAgreement -> AppScreen.TermsAgreement
            AppRoute.AddAlarm -> AppScreen.AddAlarm
            is AppRoute.EditAlarm -> AppScreen.EditAlarm
            is AppRoute.Destination -> AppScreen.Destination
            AppRoute.AlarmSound -> AppScreen.AlarmSound
            else -> error("Only migrated routes belong in this back stack")
        }

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
            else -> error("Route has not been migrated: $route")
        }
        // 공통 백스택 항목과 상태를 유지하고, 반복해서 눌러도 중복 항목을 추가하지 않는다.
        val sharedSize = routes.zip(destinationStack).takeWhile { (a, b) -> a == b }.size
        routes.subList(sharedSize, routes.size).clear()
        routes.addAll(destinationStack.drop(sharedSize))
        legacyScreenName = null
    }

    /** 아직 이전하지 않은 화면의 콜백을 연결하는 어댑터다. */
    fun navigate(screen: AppScreen) {
        when (screen) {
            AppScreen.Home -> navigate(AppRoute.Home)
            AppScreen.MyPage, AppScreen.Settings -> navigate(AppRoute.MyPage)
            AppScreen.NicknameChange -> navigate(AppRoute.NicknameChange)
            AppScreen.Login -> navigate(AppRoute.Login)
            AppScreen.TermsAgreement -> navigate(AppRoute.TermsAgreement)
            AppScreen.AddAlarm -> navigate(AppRoute.AddAlarm)
            AppScreen.AlarmSound -> navigate(AppRoute.AlarmSound)
            AppScreen.EditAlarm, AppScreen.Destination ->
                throw IllegalArgumentException("Use an AppRoute with the required identifier for $screen")
            else -> legacyScreenName = screen.name
        }
    }

    fun popBackStack(from: AppRoute = routes.last()) {
        // 이미 벗어난 백스택 항목의 콜백은 무시하고, 루트 항목은 제거하지 않는다.
        if (routes.size > 1 && isCurrentRoute(from)) {
            routes.removeLastOrNull()
        }
    }

    fun isCurrentRoute(route: AppRoute): Boolean =
        legacyScreenName == null && routes.last() == route

    fun retainedRoutes(screen: AppScreen): List<AppRoute> =
        (backStack + routesForScreen(screen).orEmpty()).distinct()

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
