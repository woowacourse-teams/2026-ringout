package com.joon.ringout.presentation.navigation

import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent

@OptIn(ExperimentalCoroutinesApi::class)
internal suspend fun TestScope.withEffectComposition(
    content: @Composable () -> Unit,
    block: suspend (drain: () -> Unit) -> Unit,
) {
    val frameClock = BroadcastFrameClock()
    val recomposer = Recomposer(coroutineContext + frameClock)
    val runner = launch(frameClock, start = CoroutineStart.UNDISPATCHED) {
        recomposer.runRecomposeAndApplyChanges()
    }
    val composition = Composition(NoOpApplier(), recomposer)
    var frameNanos = 0L

    fun drain() {
        repeat(20) {
            runCurrent()
            Snapshot.sendApplyNotifications()
            runCurrent()
            if (!frameClock.hasAwaiters && !recomposer.hasPendingWork) return
            if (frameClock.hasAwaiters) {
                frameNanos += 16_000_000L
                frameClock.sendFrame(frameNanos)
            }
        }
        error("재구성이 안정 상태에 도달하지 않았습니다.")
    }

    try {
        // 입력만 바꾸어 같은 Composition에서 Effect의 재실행을 검증한다.
        composition.setContent(content)
        drain()
        block(::drain)
    } finally {
        composition.dispose()
        recomposer.cancel()
        runner.cancelAndJoin()
        recomposer.join()
    }
}

private class NoOpApplier : AbstractApplier<Unit>(Unit) {
    override fun insertTopDown(index: Int, instance: Unit) = Unit

    override fun insertBottomUp(index: Int, instance: Unit) = Unit

    override fun remove(index: Int, count: Int) = Unit

    override fun move(from: Int, to: Int, count: Int) = Unit

    override fun onClear() = Unit
}
