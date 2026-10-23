package com.joon.ringout.presentation.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.joon.ringout.presentation.navigation.component.MainNavigationBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.lifecycle.viewmodel.ViewModelStoreProvider
import androidx.lifecycle.viewmodel.navigation3.ViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.navigation3.scene.Scene
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
    backTransitions: NavigationBackTransitions = platformNavigationBackTransitions(),
) {
    val provider = entryProvider(builder = graph)
    val visibleRoutes = checkNotNull(navigationState.routesForDisplayedRoute(displayedRoute)) {
        "표시 경로는 현재 백스택 또는 알람 울림 경로여야 한다: $displayedRoute"
    }
    val retainedRoutes = navigationState.retainedRoutes(displayedRoute)
    val currentDisplayedRoute by rememberUpdatedState(displayedRoute)
    val backActions = remember { NavigationBackActions() }
    val surfaceDecorator = remember(navigationState, backTransitions.enabled) {
        NavEntryDecorator<AppRoute> { entry ->
            val route = entry.appRoute
            val isActive = route == currentDisplayedRoute
            val registerBackAction = remember(route) {
                { onBack: () -> Unit -> backActions.register(route, onBack) }
            }
            // Keep the bar with its screen beneath the outgoing screen during a pop/preview.
            Box(
                modifier = Modifier.fillMaxSize().then(
                    if (backTransitions.enabled && !isActive) Modifier.blockInactiveEntryInput() else Modifier,
                ),
            ) {
                CompositionLocalProvider(LocalNavigationBackActionRegistrar provides registerBackAction) {
                    entry.Content()
                }
                route.mainNavigationTab()?.let { selectedTab ->
                    MainNavigationBar(
                        selectedTab = selectedTab,
                        onTabSelected = { if (isActive) navigationState.navigate(it.route) },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(top = 12.dp, bottom = 12.dp),
                    )
                }
            }
        }
    }
    val entries = rememberDecoratedNavEntries(
        entries = retainedRoutes.map { route ->
            val entry = provider(route)
            NavEntry(
                key = route,
                contentKey = entry.contentKey,
                metadata = entry.metadata + (AppRouteMetadataKey to route),
            ) { entry.Content() }
        },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            remember(viewModelStoreProvider) {
                ViewModelStoreNavEntryDecorator<AppRoute>(viewModelStoreProvider)
            },
            surfaceDecorator,
        ),
    )
    val entriesByRoute = retainedRoutes.zip(entries).toMap()
    Box(modifier = modifier) {
        NavDisplay(
            entries = visibleRoutes.map(entriesByRoute::getValue),
            modifier = Modifier.fillMaxSize(),
            onBack = {
                val route = visibleRoutes.last()
                if (!isBackBlocked && !backActions.dispatch(route)) onBack(route)
            },
            transitionSpec = {
                // A cancelled gesture still targets the previous scene while the stack is unchanged.
                if (backTransitions.enabled && isBackTransition(navigationState)) backTransitions.pop(this)
                else noNavigationTransition()
            },
            popTransitionSpec = {
                if (backTransitions.enabled && isBackTransition(navigationState)) backTransitions.pop(this)
                else noNavigationTransition()
            },
            predictivePopTransitionSpec = { edge ->
                if (backTransitions.enabled && isBackTransition(navigationState)) backTransitions.predictivePop(this, edge)
                else noNavigationTransition()
            },
            sizeTransform = null,
        )
    }
    // 두 플랫폼 모두 NavDisplay의 뒤로 가기 미리보기나 백스택 제거 전에 차단된 뒤로 가기 제스처를 소비한다.
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = isBackBlocked,
        onBackCompleted = {},
    )
}

private fun AnimatedContentTransitionScope<Scene<AppRoute>>.isBackTransition(
    navigationState: AppNavigationState,
): Boolean = navigationState.isBackTransition(initialState.entries.last().appRoute, targetState.entries.last().appRoute)

private const val AppRouteMetadataKey = "com.joon.ringout.navigation.route"
private val NavEntry<AppRoute>.appRoute: AppRoute
    get() = metadata.getValue(AppRouteMetadataKey) as AppRoute

/** A popped/previewed entry remains visible, but must not dispatch another navigation action. */
private fun Modifier.blockInactiveEntryInput(): Modifier =
    clearAndSetSemantics {}.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
            }
        }
    }
