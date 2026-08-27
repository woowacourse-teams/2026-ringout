package com.joon.ringout.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.joon.ringout.AppScreen

/** Keeps migrated entry state alive while a legacy or priority screen is visible. */
@Composable
internal fun RingoutNavHost(
    navigationState: AppNavigationState,
    screen: AppScreen,
    graph: EntryProviderScope<AppRoute>.() -> Unit,
    modifier: Modifier = Modifier,
    legacyContent: @Composable () -> Unit,
) {
    val provider = entryProvider(builder = graph)
    val entries = rememberDecoratedNavEntries(
        entries = navigationState.backStack.map(provider),
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
    )
    val currentRoute = navigationState.backStack.last()

    when (screen) {
        AppScreen.Home,
        AppScreen.MyPage,
        AppScreen.Settings,
        AppScreen.NicknameChange,
        -> NavDisplay(
            // The legacy mission policy can temporarily resolve to Home before its effect runs.
            entries = if (screen == AppScreen.Home) entries.take(1) else entries,
            modifier = modifier,
            onBack = { navigationState.popBackStack(currentRoute) },
        )

        else -> legacyContent()
    }
}
