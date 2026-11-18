package com.joon.ringout.presentation.arrival.component

import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.joon.ringout.RingoutTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.compose.resources.painterResource
import ringout.shared.generated.resources.Res
import ringout.shared.generated.resources.arrival_seed
import kotlin.coroutines.coroutineContext
import kotlin.math.roundToInt

// Test-only metadata; frames are decorative and must not trigger accessibility announcements.
internal val ArrivalSeedFrame = SemanticsPropertyKey<Int>("ArrivalSeedFrame")

@OptIn(ExperimentalResourceApi::class)
@Composable
internal fun ArrivalSeedAnimation(modifier: Modifier = Modifier) {
    if (LocalInspectionMode.current) {
        Image(
            painter = painterResource(Res.drawable.arrival_seed),
            contentDescription = null,
            modifier = modifier.size(186.dp).testTag("arrival-seed-static"),
            contentScale = ContentScale.Fit,
        )
        return
    }

    val atlas by produceState<ImageBitmap?>(null) {
        value = try {
            withContext(Dispatchers.Default) {
                Res.readBytes("drawable/arrival_seed_animation.webp").decodeToImageBitmap().also {
                    require(
                        it.width == SeedFrameSize * SeedAtlasColumns &&
                            it.height == SeedFrameSize * SeedAtlasRows,
                    )
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // A missing or unsupported asset must not leave the arrival illustration empty.
            null
        }
    }
    val frame = remember { mutableIntStateOf(0) }
    var isVisible by remember { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    LaunchedEffect(atlas, lifecycle, isVisible) {
        if (atlas == null || !isVisible) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val durationScale = coroutineContext[MotionDurationScale]
            snapshotFlow { durationScale?.scaleFactor ?: 1f }.collectLatest { scale ->
                if (scale <= 0f || !scale.isFinite()) return@collectLatest
                var elapsedNanos = frame.intValue.toDouble() * NanosPerSecond / SeedFramesPerSecond
                var previousFrameTime = withInfiniteAnimationFrameNanos { it }
                while (coroutineContext.isActive) {
                    val frameTime = withInfiniteAnimationFrameNanos { it }
                    elapsedNanos += (frameTime - previousFrameTime) / scale.toDouble()
                    previousFrameTime = frameTime
                    frame.intValue = (
                        (elapsedNanos * SeedFramesPerSecond / NanosPerSecond).toLong() % SeedFrameCount
                    ).toInt()
                }
            }
        }
    }

    Box(
        modifier = modifier.size(186.dp).onGloballyPositioned {
            isVisible = !it.boundsInWindow(clipBounds = true).isEmpty
        },
    ) {
        val bitmap = atlas
        if (bitmap == null) {
            Image(
                painter = painterResource(Res.drawable.arrival_seed),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().testTag("arrival-seed-static"),
                contentScale = ContentScale.Fit,
            )
        } else {
            Canvas(
                modifier = Modifier.fillMaxSize().testTag("arrival-seed-animation").semantics {
                    this[ArrivalSeedFrame] = frame.intValue
                },
            ) {
                val index = frame.intValue
                val side = minOf(size.width, size.height).roundToInt()
                drawImage(
                    image = bitmap,
                    srcOffset = IntOffset(
                        index % SeedAtlasColumns * SeedFrameSize,
                        index / SeedAtlasColumns * SeedFrameSize,
                    ),
                    srcSize = IntSize(SeedFrameSize, SeedFrameSize),
                    dstOffset = IntOffset(
                        ((size.width - side) / 2).roundToInt(),
                        ((size.height - side) / 2).roundToInt(),
                    ),
                    dstSize = IntSize(side, side),
                )
            }
        }
    }
}

private const val SeedFrameSize = 384
private const val SeedAtlasColumns = 7
private const val SeedAtlasRows = 6
private const val SeedFrameCount = 41
private const val SeedFramesPerSecond = 24
private const val NanosPerSecond = 1_000_000_000.0

@Preview(widthDp = 186, heightDp = 186)
@Composable
private fun ArrivalSeedAnimationPreview() {
    RingoutTheme { ArrivalSeedAnimation() }
}
