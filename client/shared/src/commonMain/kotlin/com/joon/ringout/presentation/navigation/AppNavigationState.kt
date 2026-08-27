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
 * Home → MyPage → Nickname is owned by Navigation 3. Other screens temporarily
 * keep their existing routing, without replacing the migrated back stack.
 */
internal class AppNavigationState(
    private val routes: NavBackStack<AppRoute> = NavBackStack(AppRoute.Home),
    legacyScreenName: MutableState<String?> = mutableStateOf(null),
) {
    private var legacyScreenName by legacyScreenName

    val backStack: List<AppRoute>
        get() = routes

    val requestedScreen: AppScreen
        get() = legacyScreenName?.let(AppScreen::valueOf) ?: when (routes.last()) {
            AppRoute.Home -> AppScreen.Home
            AppRoute.MyPage -> AppScreen.MyPage
            AppRoute.NicknameChange -> AppScreen.NicknameChange
            else -> error("Only migrated routes belong in this back stack")
        }

    fun navigate(route: AppRoute) {
        val destinationStack = when (route) {
            AppRoute.Home -> listOf(AppRoute.Home)
            AppRoute.MyPage -> listOf(AppRoute.Home, AppRoute.MyPage)
            AppRoute.NicknameChange ->
                listOf(AppRoute.Home, AppRoute.MyPage, AppRoute.NicknameChange)
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
            else -> legacyScreenName = screen.name
        }
    }

    fun popBackStack(from: AppRoute = routes.last()) {
        // Ignore callbacks from an outgoing entry and never remove the root.
        if (legacyScreenName == null && routes.size > 1 && routes.last() == from) {
            routes.removeLastOrNull()
        }
    }
}
