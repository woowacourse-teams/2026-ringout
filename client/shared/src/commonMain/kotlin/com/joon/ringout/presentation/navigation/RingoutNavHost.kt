package com.joon.ringout.presentation.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.ViewModelStoreProvider
import androidx.lifecycle.viewmodel.navigation3.ViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState

/** 실제 백스택과 플랫폼에서 우선 표시하는 경로의 상태를 유지하며 현재 화면을 표시한다. */
@Composable
internal fun RingoutNavHost(
    navigationState: AppNavigationState,
    displayedRoute: AppRoute,
    viewModelStoreProvider: ViewModelStoreProvider,
    graph: EntryProviderScope<AppRoute>.() -> Unit,
    modifier: Modifier = Modifier,
    isBackBlocked: Boolean = false,
    onBack: (AppRoute) -> Unit = { navigationState.popBackStack(it) },
) {
    val provider = entryProvider(builder = graph)
    val visibleRoutes = checkNotNull(navigationState.routesForDisplayedRoute(displayedRoute)) {
        "표시 경로는 현재 백스택 또는 알람 울림 경로여야 한다: $displayedRoute"
    }
    val retainedRoutes = navigationState.retainedRoutes(displayedRoute)
    val entries = rememberDecoratedNavEntries(
        entries = retainedRoutes.map(provider),
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            remember(viewModelStoreProvider) {
                ViewModelStoreNavEntryDecorator<AppRoute>(viewModelStoreProvider)
            },
        ),
    )
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
    // 두 플랫폼 모두 NavDisplay의 뒤로 가기 미리보기나 백스택 제거 전에 차단된 뒤로 가기 제스처를 소비한다.
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = isBackBlocked,
        onBackCompleted = {},
    )
}
