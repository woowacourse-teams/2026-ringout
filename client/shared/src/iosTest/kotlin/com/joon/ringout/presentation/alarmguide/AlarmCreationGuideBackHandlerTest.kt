package com.joon.ringout.presentation.alarmguide

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.NavigationEventInput
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.joon.ringout.presentation.navigation.AppNavigationState
import com.joon.ringout.presentation.navigation.AppRoute
import com.joon.ringout.presentation.navigation.withEffectComposition
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class AlarmCreationGuideBackHandlerTest {
    @Test
    fun `iOS 뒤로가기 제스처는 작성 화면을 유지하고 가이드 종료를 한 번만 요청한다`() = runTest {
        val fixture = Fixture()
        withBackHandlers(fixture) { drain ->
            fixture.input.start()
            fixture.input.progress()
            drain()
            assertEquals(0, fixture.finishCalls)

            fixture.input.complete()
            drain()
            fixture.input.gesture()
            drain()

            assertEquals(1, fixture.finishCalls)
            assertEquals(listOf(AppRoute.Home, AppRoute.AddAlarm), fixture.navigation.backStack.toList())
        }
    }

    @Test
    fun `iOS 뒤로가기 제스처 취소는 가이드를 유지하고 숨겨진 가이드는 화면 뒤로가기를 허용한다`() = runTest {
        val fixture = Fixture()
        withBackHandlers(fixture) { drain ->
            fixture.input.start()
            fixture.input.progress()
            fixture.input.cancel()
            drain()

            assertEquals(0, fixture.finishCalls)
            assertEquals(listOf(AppRoute.Home, AppRoute.AddAlarm), fixture.navigation.backStack.toList())

            fixture.visible = false
            drain()
            fixture.input.gesture()
            drain()

            assertEquals(0, fixture.finishCalls)
            assertEquals(listOf(AppRoute.Home), fixture.navigation.backStack.toList())
        }
    }

    @Test
    fun `iOS 알람 저장 중 뒤로가기 제스처는 종료 요청과 화면 이동을 모두 막는다`() = runTest {
        val fixture = Fixture().apply { isBusy = true }
        withBackHandlers(fixture) { drain ->
            fixture.input.gesture()
            drain()

            assertEquals(0, fixture.finishCalls)
            assertEquals(listOf(AppRoute.Home, AppRoute.AddAlarm), fixture.navigation.backStack.toList())

            fixture.isBusy = false
            drain()
            fixture.input.gesture()
            drain()

            assertEquals(1, fixture.finishCalls)
            assertEquals(listOf(AppRoute.Home, AppRoute.AddAlarm), fixture.navigation.backStack.toList())
        }
    }

    private suspend fun TestScope.withBackHandlers(
        fixture: Fixture,
        block: suspend (drain: () -> Unit) -> Unit,
    ) {
        fixture.navigationEventDispatcher.addInput(fixture.input)
        try {
            withEffectComposition(
                content = {
                    CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides fixture) {
                        // The screen's handler is registered before the guide, as in RingoutNavHost.
                        NavigationBackHandler(
                            state = rememberNavigationEventState(NavigationEventInfo.None),
                            onBackCompleted = { fixture.navigation.popBackStack() },
                        )
                        AlarmCreationGuideBackHandler(
                            visible = fixture.visible,
                            isBusy = fixture.isBusy,
                            onFinish = {
                                fixture.finishCalls++
                                fixture.isBusy = true
                            },
                        )
                    }
                },
                block = block,
            )
        } finally {
            fixture.navigationEventDispatcher.dispose()
        }
    }

    private class Fixture : NavigationEventDispatcherOwner {
        override val navigationEventDispatcher = NavigationEventDispatcher()
        val input = GestureInput()
        val navigation = AppNavigationState().apply { navigate(AppRoute.AddAlarm) }
        var visible by mutableStateOf(true)
        var isBusy by mutableStateOf(false)
        var finishCalls = 0
    }

    private class GestureInput : NavigationEventInput() {
        fun start() = dispatchOnBackStarted(NavigationEvent(swipeEdge = NavigationEvent.EDGE_LEFT))
        fun progress() = dispatchOnBackProgressed(
            NavigationEvent(swipeEdge = NavigationEvent.EDGE_LEFT, progress = 0.5f),
        )
        fun complete() = dispatchOnBackCompleted()
        fun cancel() = dispatchOnBackCancelled()
        fun gesture() {
            start()
            progress()
            complete()
        }
    }
}
