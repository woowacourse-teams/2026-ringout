package com.joon.ringout.presentation.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.joon.ringout.AppScreen

/** Keeps migrated entry state alive while a legacy or priority screen is visible. */
@Composable
internal fun RingoutNavHost(
    navigationState: AppNavigationState,
    screen: AppScreen,
    graph: EntryProviderScope<AppRoute>.() -> Unit,
    modifier: Modifier = Modifier,
    isBackBlocked: Boolean = false,
    onBack: (AppRoute) -> Unit = { navigationState.popBackStack(it) },
    legacyContent: @Composable () -> Unit,
) {
    val provider = entryProvider(builder = graph)
    val visibleRoutes = navigationState.routesForScreen(screen)
    // Also retain a priority route displayed before its redirect effect updates the real stack.
    val retainedRoutes = (navigationState.backStack + visibleRoutes.orEmpty()).distinct()
    val entries = rememberDecoratedNavEntries(
        entries = retainedRoutes.map(provider),
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
    )
    if (visibleRoutes != null) {
        val entriesByRoute = retainedRoutes.zip(entries).toMap()
        NavDisplay(
            entries = visibleRoutes.map(entriesByRoute::getValue),
            modifier = modifier,
            onBack = { if (!isBackBlocked) onBack(visibleRoutes.last()) },
            transitionSpec = { EnterTransition.None togetherWith ExitTransition.None },
            popTransitionSpec = { EnterTransition.None togetherWith ExitTransition.None },
            predictivePopTransitionSpec = { EnterTransition.None togetherWith ExitTransition.None },
            sizeTransform = null,
        )
        // Consume busy-auth back gestures on both platforms before NavDisplay can preview/pop.
        NavigationBackHandler(
            state = rememberNavigationEventState(NavigationEventInfo.None),
            isBackEnabled = isBackBlocked,
            onBackCompleted = {},
        )
    } else {
        legacyContent()
    }
}
