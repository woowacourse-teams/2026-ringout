package com.joon.ringout.presentation.arrival

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import com.joon.ringout.presentation.arrival.component.ArrivalSeedAnimation
import com.joon.ringout.presentation.arrival.component.ArrivalSeedFrame
import com.joon.ringout.presentation.launchWithFontScale
import com.joon.ringout.ringoutColors
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w402dp-h941dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ArrivalSeedAnimationTest {
    private val motionScale = object : MotionDurationScale {
        override var scaleFactor by mutableFloatStateOf(1f)
    }
    @get:Rule val compose = createEmptyComposeRule(effectContext = motionScale)

    @Test
    fun `투명 씨앗을 재생하고 마지막 프레임 뒤에는 처음부터 반복한다`() = withManualClock {
        val owner = AnimationLifecycleOwner()
        var theme by mutableStateOf(ThemeMode.Dark)
        launchWithFontScale(1f) {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                RingoutTheme(theme) {
                    Box(
                        Modifier.fillMaxSize().background(MaterialTheme.ringoutColors.mainScreenBackground),
                        contentAlignment = Alignment.Center,
                    ) { ArrivalSeedAnimation() }
                }
            }
        }.use { content ->
            awaitAtlas()
            assertEquals(0, frame())
            compose.runOnIdle { content.captureLayout("arrival-animation-dark-first") }
            compose.runOnIdle { owner.lifecycle.currentState = Lifecycle.State.RESUMED }
            compose.mainClock.advanceTimeBy(32)
            compose.mainClock.advanceTimeBy(500)
            assertTrue(frame() in 11..13, "500ms frame=${frame()}")
            compose.runOnIdle { content.captureLayout("arrival-animation-dark-moving") }

            compose.mainClock.advanceTimeBy(1_360)
            assertTrue(frame() in 2..5, "loop frame=${frame()}")
            compose.runOnIdle { theme = ThemeMode.Light }
            compose.mainClock.advanceTimeByFrame()
            compose.runOnIdle { content.captureLayout("arrival-animation-light-moving") }
        }
    }

    @Test
    fun `앱이 일시 정지되거나 씨앗이 화면 밖으로 나가면 정지하고 다시 보이면 재생한다`() = withManualClock {
        val owner = AnimationLifecycleOwner()
        var hidden by mutableStateOf(false)
        launchWithFontScale(1f) {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                Box(Modifier.fillMaxSize().clipToBounds()) {
                    ArrivalSeedAnimation(Modifier.offset(y = if (hidden) 2_000.dp else 0.dp))
                }
            }
        }.use {
            awaitAtlas()
            compose.runOnIdle { owner.lifecycle.currentState = Lifecycle.State.RESUMED }
            compose.mainClock.advanceTimeBy(300)
            assertTrue(frame() > 0)
            compose.runOnIdle { owner.lifecycle.currentState = Lifecycle.State.STARTED }
            compose.mainClock.advanceTimeBy(32)
            val paused = frame()
            compose.mainClock.advanceTimeBy(400)
            assertEquals(paused, frame())

            compose.runOnIdle { owner.lifecycle.currentState = Lifecycle.State.RESUMED }
            compose.mainClock.advanceTimeBy(300)
            assertNotEquals(paused, frame())
            compose.runOnIdle { hidden = true }
            compose.mainClock.advanceTimeBy(64)
            val offscreen = frame()
            compose.mainClock.advanceTimeBy(400)
            assertEquals(offscreen, frame())

            compose.runOnIdle { hidden = false }
            settleLayout()
            compose.mainClock.advanceTimeBy(300)
            assertNotEquals(offscreen, frame())
        }
    }

    @Test
    fun `시스템에서 모션을 끄면 정지하고 다시 켜면 이어서 재생한다`() = withManualClock {
        val owner = AnimationLifecycleOwner()
        motionScale.scaleFactor = 0f
        launchWithFontScale(1f) {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                Box(Modifier.fillMaxSize()) { ArrivalSeedAnimation() }
            }
        }.use {
            awaitAtlas()
            compose.runOnIdle { owner.lifecycle.currentState = Lifecycle.State.RESUMED }
            compose.mainClock.advanceTimeBy(300)
            assertEquals(0, frame())
            compose.runOnIdle { motionScale.scaleFactor = 1f }
            settleLayout()
            compose.mainClock.advanceTimeBy(300)
            assertTrue(frame() > 0)
            compose.runOnIdle { motionScale.scaleFactor = 0f }
            compose.mainClock.advanceTimeBy(32)
            val paused = frame()
            compose.mainClock.advanceTimeBy(500)
            assertEquals(paused, frame())
        }
    }

    @Test
    fun `프리뷰는 영상 리소스를 로드하지 않고 정적 씨앗을 표시한다`() {
        launchWithFontScale(1f) {
            CompositionLocalProvider(LocalInspectionMode provides true) { ArrivalSeedAnimation() }
        }.use {
            compose.onNodeWithTag("arrival-seed-static").assertIsDisplayed()
            compose.onNodeWithTag("arrival-seed-animation").assertDoesNotExist()
        }
    }

    private fun awaitAtlas() {
        compose.waitUntil(10_000) {
            compose.mainClock.advanceTimeByFrame()
            compose.onAllNodesWithTag("arrival-seed-animation").fetchSemanticsNodes().size == 1
        }
        compose.waitForIdle()
    }

    private fun frame(): Int = compose.onNodeWithTag("arrival-seed-animation")
        .fetchSemanticsNode().config[ArrivalSeedFrame]

    private fun settleLayout() {
        // Layout and snapshot notifications are not driven by a single batched clock advance.
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
    }

    private inline fun withManualClock(block: () -> Unit) {
        compose.mainClock.autoAdvance = false
        try {
            block()
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }
}

private class AnimationLifecycleOwner : LifecycleOwner {
    override val lifecycle = LifecycleRegistry.createUnsafe(this).apply {
        currentState = Lifecycle.State.STARTED
    }
}
