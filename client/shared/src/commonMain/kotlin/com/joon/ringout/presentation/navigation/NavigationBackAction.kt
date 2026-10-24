package com.joon.ringout.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf

internal typealias RegisterNavigationBackAction = (onBack: () -> Unit) -> (() -> Unit)

internal val LocalNavigationBackActionRegistrar = staticCompositionLocalOf<RegisterNavigationBackAction?> { null }

/** Keep a screen's completion guards and side effects while NavDisplay owns gesture progress. */
@Composable
internal fun NavigationBackAction(onBack: () -> Unit) {
    val register = LocalNavigationBackActionRegistrar.current ?: return
    val currentOnBack by rememberUpdatedState(onBack)
    DisposableEffect(register) {
        val unregister = register { currentOnBack() }
        onDispose { unregister() }
    }
}

internal class NavigationBackActions {
    private val actions = mutableMapOf<AppRoute, () -> Unit>()

    fun register(route: AppRoute, onBack: () -> Unit): () -> Unit {
        actions[route] = onBack
        return { if (actions[route] === onBack) actions.remove(route) }
    }

    fun dispatch(route: AppRoute): Boolean {
        val action = actions[route] ?: return false
        action()
        return true
    }
}
