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
 * Main and authentication flows are owned by Navigation 3. Alarm screens temporarily
 * keep their existing routing, without replacing the migrated back stack.
 */
internal class AppNavigationState(
    private val routes: NavBackStack<AppRoute> = NavBackStack(AppRoute.Home),
    legacyScreenName: MutableState<String?> = mutableStateOf(null),
) {
    private var legacyScreenName by legacyScreenName

    init {
        // Saved state from the previous migration can still contain legacy auth destinations.
        when (this.legacyScreenName) {
            AppScreen.Login.name -> navigate(AppRoute.Login)
            AppScreen.TermsAgreement.name -> navigate(AppRoute.TermsAgreement)
        }
    }

    val backStack: List<AppRoute>
        get() = routes

    val requestedScreen: AppScreen
        get() = legacyScreenName?.let(AppScreen::valueOf) ?: when (routes.last()) {
            AppRoute.Home -> AppScreen.Home
            AppRoute.MyPage -> AppScreen.MyPage
            AppRoute.NicknameChange -> AppScreen.NicknameChange
            AppRoute.Login -> AppScreen.Login
            AppRoute.TermsAgreement -> AppScreen.TermsAgreement
            else -> error("Only migrated routes belong in this back stack")
        }

    fun navigate(route: AppRoute) {
        val destinationStack = when (route) {
            AppRoute.Home -> listOf(AppRoute.Home)
            AppRoute.MyPage -> listOf(AppRoute.Home, AppRoute.MyPage)
            AppRoute.NicknameChange ->
                listOf(AppRoute.Home, AppRoute.MyPage, AppRoute.NicknameChange)
            // Login has always returned to MyPage, including after logout or reauthentication.
            AppRoute.Login -> listOf(AppRoute.Home, AppRoute.MyPage, AppRoute.Login)
            AppRoute.TermsAgreement ->
                listOf(AppRoute.Home, AppRoute.MyPage, AppRoute.Login, AppRoute.TermsAgreement)
            else -> error("Route has not been migrated: $route")
        }
        // Keep shared entries and their state; repeated taps do not add duplicates.
        val sharedSize = routes.zip(destinationStack).takeWhile { (a, b) -> a == b }.size
        routes.subList(sharedSize, routes.size).clear()
        routes.addAll(destinationStack.drop(sharedSize))
        legacyScreenName = null
    }

    /** Adapter for callbacks belonging to screens that have not been migrated yet. */
    fun navigate(screen: AppScreen) {
        when (screen) {
            AppScreen.Home -> navigate(AppRoute.Home)
            AppScreen.MyPage, AppScreen.Settings -> navigate(AppRoute.MyPage)
            AppScreen.NicknameChange -> navigate(AppRoute.NicknameChange)
            AppScreen.Login -> navigate(AppRoute.Login)
            AppScreen.TermsAgreement -> navigate(AppRoute.TermsAgreement)
            else -> legacyScreenName = screen.name
        }
    }

    fun popBackStack(from: AppRoute = routes.last()) {
        // Ignore callbacks from an outgoing entry and never remove the root.
        if (routes.size > 1 && isCurrentRoute(from)) {
            routes.removeLastOrNull()
        }
    }

    fun isCurrentRoute(route: AppRoute): Boolean =
        legacyScreenName == null && routes.last() == route

    /** Projects the existing display policy without mutating the requested back stack. */
    fun routesForScreen(screen: AppScreen): List<AppRoute>? {
        val route = when (screen) {
            AppScreen.Home -> AppRoute.Home
            AppScreen.MyPage, AppScreen.Settings -> AppRoute.MyPage
            AppScreen.NicknameChange -> AppRoute.NicknameChange
            AppScreen.Login -> AppRoute.Login
            AppScreen.TermsAgreement -> AppRoute.TermsAgreement
            else -> return null
        }
        val index = routes.indexOf(route)
        // A priority Login can be displayed before the existing redirect effect has run.
        return if (index >= 0) routes.take(index + 1) else listOf(route)
    }
}
