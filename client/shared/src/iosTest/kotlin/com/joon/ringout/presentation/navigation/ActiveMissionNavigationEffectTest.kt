package com.joon.ringout.presentation.navigation

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.runtime.setValue
import com.joon.ringout.AppScreen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ActiveMissionNavigationEffectTest {
    @Test
    fun `같은 미션 회차는 홈으로 나온 뒤 재강제하지 않고 새 회차만 다시 처리한다`() = runTest {
        val navigationState = AppNavigationState()
        var activeMissionOccurrenceId by mutableStateOf<String?>("occurrence-1")
        var unrelatedRevision by mutableStateOf(0)
        var appliedRevision = -1

        withEffectComposition(
            content = {
                val currentRevision = unrelatedRevision
                ActiveMissionNavigationEffect(activeMissionOccurrenceId, navigationState)
                SideEffect { appliedRevision = currentRevision }
            },
        ) { drain ->
            assertEquals(AppScreen.ActiveAlarmTracking, navigationState.requestedScreen)

            navigationState.navigate(AppScreen.Home)
            unrelatedRevision++
            drain()

            assertEquals(1, appliedRevision)
            assertEquals(AppScreen.Home, navigationState.requestedScreen)
            assertEquals(listOf(AppRoute.Home), navigationState.backStack.toList())

            activeMissionOccurrenceId = "occurrence-2"
            drain()

            assertEquals(AppScreen.ActiveAlarmTracking, navigationState.requestedScreen)
        }
    }

    @Test
    fun `미션 종료는 추적 화면만 홈으로 복귀시키고 다음 회차를 다시 처리한다`() = runTest {
        val navigationState = AppNavigationState()
        var activeMissionOccurrenceId by mutableStateOf<String?>("occurrence-1")

        withEffectComposition(
            content = {
                ActiveMissionNavigationEffect(activeMissionOccurrenceId, navigationState)
            },
        ) { drain ->
            assertEquals(AppScreen.ActiveAlarmTracking, navigationState.requestedScreen)

            activeMissionOccurrenceId = null
            drain()
            assertEquals(AppScreen.Home, navigationState.requestedScreen)

            activeMissionOccurrenceId = "occurrence-2"
            drain()
            assertEquals(AppScreen.ActiveAlarmTracking, navigationState.requestedScreen)

            navigationState.navigate(AppRoute.MyPage)
            activeMissionOccurrenceId = null
            drain()
            assertEquals(AppScreen.MyPage, navigationState.requestedScreen)
            assertEquals(
                listOf(AppRoute.Home, AppRoute.MyPage),
                navigationState.backStack.toList(),
            )

            activeMissionOccurrenceId = "occurrence-3"
            drain()
            assertEquals(AppScreen.ActiveAlarmTracking, navigationState.requestedScreen)
        }
    }

    @Test
    fun `저장 상태를 복원해도 처리한 같은 미션 회차를 다시 강제하지 않는다`() = runTest {
        val firstRegistry = SaveableStateRegistry(restoredValues = null, canBeSaved = { true })
        val firstNavigationState = AppNavigationState()
        var savedValues: Map<String, List<Any?>> = emptyMap()

        withRestorableActiveMissionEffect(firstRegistry, firstNavigationState) { drain ->
            assertEquals(AppScreen.ActiveAlarmTracking, firstNavigationState.requestedScreen)

            firstNavigationState.navigate(AppScreen.Home)
            drain()
            savedValues = firstRegistry.performSave()

            assertTrue(savedValues.isNotEmpty())
        }

        val restoredRegistry = SaveableStateRegistry(savedValues, canBeSaved = { true })
        val restoredNavigationState = AppNavigationState()
        withRestorableActiveMissionEffect(restoredRegistry, restoredNavigationState) {
            assertEquals(AppScreen.Home, restoredNavigationState.requestedScreen)
            assertEquals(listOf(AppRoute.Home), restoredNavigationState.backStack.toList())
        }
    }
}

private suspend fun TestScope.withRestorableActiveMissionEffect(
    saveableStateRegistry: SaveableStateRegistry,
    navigationState: AppNavigationState,
    block: suspend (drain: () -> Unit) -> Unit,
) {
    withEffectComposition(
        content = {
            CompositionLocalProvider(LocalSaveableStateRegistry provides saveableStateRegistry) {
                ActiveMissionNavigationEffect(
                    activeMissionOccurrenceId = "occurrence-1",
                    navigationState = navigationState,
                )
            }
        },
        block = block,
    )
}
