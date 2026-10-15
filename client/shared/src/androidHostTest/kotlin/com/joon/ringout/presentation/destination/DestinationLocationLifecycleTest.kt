package com.joon.ringout.presentation.destination

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DestinationLocationLifecycleTest {
    @Test
    fun `재진입 화면은 전환이 끝나야 위치 조회를 시작한다`() = runTest {
        val lifecycle = createLifecycle()
        repeat(2) {
            lifecycle.currentState = Lifecycle.State.STARTED
            val ready = async { lifecycle.awaitDestinationLocationReady() }
            runCurrent()
            assertFalse(ready.isCompleted)

            lifecycle.currentState = Lifecycle.State.RESUMED
            assertTrue(ready.await())
        }
    }

    @Test
    fun `이미 활성화된 화면은 즉시 위치를 조회한다`() = runTest {
        val lifecycle = createLifecycle()
        lifecycle.currentState = Lifecycle.State.RESUMED
        assertTrue(lifecycle.awaitDestinationLocationReady())
    }

    @Test
    fun `활성화 전에 화면이 제거되면 위치를 조회하지 않는다`() = runTest {
        val lifecycle = createLifecycle()
        lifecycle.currentState = Lifecycle.State.STARTED
        val ready = async { lifecycle.awaitDestinationLocationReady() }
        runCurrent()
        lifecycle.currentState = Lifecycle.State.DESTROYED
        assertFalse(ready.await())
    }

    @Test
    fun `사용자 선택으로 대기를 취소하면 활성화되어도 요청을 재개하지 않는다`() = runTest {
        val lifecycle = createLifecycle()
        lifecycle.currentState = Lifecycle.State.STARTED
        val ready = async { lifecycle.awaitDestinationLocationReady() }
        runCurrent()
        ready.cancel()
        lifecycle.currentState = Lifecycle.State.RESUMED
        runCurrent()
        assertTrue(ready.isCancelled)
    }

    private fun createLifecycle(): LifecycleRegistry {
        val owner = object : LifecycleOwner {
            override val lifecycle: LifecycleRegistry = LifecycleRegistry.createUnsafe(this)
        }
        return owner.lifecycle
    }
}
