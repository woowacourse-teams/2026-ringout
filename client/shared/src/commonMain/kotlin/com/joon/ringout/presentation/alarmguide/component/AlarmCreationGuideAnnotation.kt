package com.joon.ringout.presentation.alarmguide.component

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.alarmGuideFontFamily
import com.joon.ringout.presentation.alarmguide.AlarmCreationGuideStep
import kotlin.math.roundToInt

internal val AlarmCreationGuideStep.guideDescription: String
    get() = when (this) {
        AlarmCreationGuideStep.AddAlarm -> "여기에서 새 알람을 만들 수 있어요."
        AlarmCreationGuideStep.Time -> "알람이 울릴 시간을 정할 수 있어요."
        AlarmCreationGuideStep.Weekdays -> "알람이 울릴 요일을 정할 수 있어요."
        AlarmCreationGuideStep.Destination -> "여기에서 알람을 끄고 이동할 목적지를 정해요.\n목적지에 도착하면 알람이 더 이상 울리지 않아요."
        AlarmCreationGuideStep.Interval -> "알람이 반복해서 울릴 간격을 설정해요.\n시간 안에 목적지에 도착하지 않으면\n알람이 다시 울려요."
        AlarmCreationGuideStep.Sound -> "여기에서 알람음을 선택할 수 있어요."
        AlarmCreationGuideStep.CreateAlarm -> "이제 원하는 설정으로 알람을 만들어 보세요."
    }

@Composable
internal fun AlarmCreationGuideAnnotation(
    step: AlarmCreationGuideStep,
    targetBounds: Rect,
    reservedTop: Int,
    reservedBottom: Int,
    modifier: Modifier = Modifier,
) {
    val descriptionScrollState = key(step) { rememberScrollState() }
    val prefersAbove = step in listOf(
        AlarmCreationGuideStep.AddAlarm,
        AlarmCreationGuideStep.Interval,
        AlarmCreationGuideStep.Sound,
        AlarmCreationGuideStep.CreateAlarm,
    )
    // A full-screen Layout positions copy against the measured, unchanged UI beneath it.
    Layout(
        modifier = modifier,
        content = {
            Text(
                text = step.guideDescription,
                modifier = Modifier
                    .verticalScroll(descriptionScrollState)
                    .testTag("guide-description")
                    .semantics { liveRegion = LiveRegionMode.Polite },
                color = MaterialTheme.colorScheme.primary,
                fontFamily = MaterialTheme.alarmGuideFontFamily,
                fontSize = 20.sp,
                lineHeight = 22.sp,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge.copy(lineBreak = LineBreak.Heading),
            )
            AlarmCreationGuideArrow(step)
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val margin = 20.dp.roundToPx()
        val gap = 8.dp.roundToPx()
        val above = (targetBounds.top.roundToInt() - reservedTop - gap).coerceAtLeast(0)
        val below = (height - reservedBottom - targetBounds.bottom.roundToInt() - gap).coerceAtLeast(0)
        val arrow = measurables[1].measure(Constraints())
        val maxTextWidth = (width - margin * 2).coerceAtLeast(1)
        val designTextWidth = when (step) {
            AlarmCreationGuideStep.AddAlarm, AlarmCreationGuideStep.Time, AlarmCreationGuideStep.Weekdays -> 260.dp.roundToPx()
            else -> maxTextWidth
        }
        val textWidth = if (fontScale > 1.2f) maxTextWidth else designTextWidth.coerceAtMost(maxTextWidth)
        val neededHeight = measurables[0].minIntrinsicHeight(textWidth) + arrow.height + gap
        val placeAbove = when {
            prefersAbove && above >= neededHeight -> true
            !prefersAbove && below >= neededHeight -> false
            else -> above >= below
        }
        val availableHeight = if (placeAbove) above else below
        // On compact displays, keep at least two readable lines alongside the original arrow.
        // Stacking a large arrow above a large-font paragraph can otherwise leave a one-line slit.
        val sideBySide = step != AlarmCreationGuideStep.AddAlarm &&
            availableHeight - arrow.height - gap < 22.sp.toPx() * 2
        val measuredTextWidth = if (sideBySide) {
            (maxTextWidth - arrow.width - gap).coerceAtLeast(1)
        } else {
            textWidth
        }
        val description = measurables[0].measure(
            Constraints(
                minWidth = measuredTextWidth,
                maxWidth = measuredTextWidth,
                maxHeight = (if (sideBySide) availableHeight else availableHeight - arrow.height - gap).coerceAtLeast(1),
            ),
        )
        val textX = when (step) {
            AlarmCreationGuideStep.AddAlarm -> (targetBounds.left - description.width - 11.dp.toPx()).roundToInt()
            AlarmCreationGuideStep.Time, AlarmCreationGuideStep.Weekdays -> (width * .41f - description.width / 2).roundToInt()
            else -> (width - description.width) / 2
        }.coerceIn(margin.coerceAtMost(width / 2), (width - margin - description.width).coerceAtLeast(margin))

        layout(width, height) {
            if (step == AlarmCreationGuideStep.AddAlarm) {
                // The home arrow sweeps horizontally into the existing floating action button.
                description.place(textX, (targetBounds.top - description.height - gap).roundToInt().coerceAtLeast(reservedTop))
                arrow.place(
                    (targetBounds.left - arrow.width - 10.dp.toPx()).roundToInt().coerceAtLeast(margin),
                    (targetBounds.center.y - arrow.height / 2).roundToInt(),
                )
            } else if (sideBySide) {
                val arrowY = if (placeAbove) {
                    (targetBounds.top - arrow.height - gap).roundToInt()
                } else {
                    (targetBounds.bottom + gap).roundToInt()
                }
                arrow.placeWithLayer(margin, arrowY) {
                    rotationZ = if (placeAbove != prefersAbove) 180f else 0f
                }
                description.place(
                    margin + arrow.width + gap,
                    if (placeAbove) {
                        (targetBounds.top - description.height - gap).roundToInt().coerceAtLeast(reservedTop)
                    } else {
                        (targetBounds.bottom + gap).roundToInt()
                    },
                )
            } else {
                val arrowX = when (step) {
                    AlarmCreationGuideStep.Time, AlarmCreationGuideStep.Weekdays -> width * .32f
                    AlarmCreationGuideStep.Destination -> width * .315f
                    AlarmCreationGuideStep.Interval -> width * .258f
                    AlarmCreationGuideStep.Sound -> width * .21f
                    AlarmCreationGuideStep.CreateAlarm -> targetBounds.center.x - arrow.width / 2
                }.roundToInt().coerceIn(0, (width - arrow.width).coerceAtLeast(0))
                if (placeAbove) {
                    val arrowY = (targetBounds.top - arrow.height - gap).roundToInt()
                    arrow.placeWithLayer(arrowX, arrowY) {
                        rotationZ = if (placeAbove != prefersAbove) 180f else 0f
                    }
                    description.place(textX, (arrowY - description.height - gap).coerceAtLeast(reservedTop))
                } else {
                    val arrowY = (targetBounds.bottom + gap).roundToInt()
                    arrow.placeWithLayer(arrowX, arrowY) {
                        rotationZ = if (placeAbove != prefersAbove) 180f else 0f
                    }
                    description.place(textX, arrowY + arrow.height + gap)
                }
            }
        }
    }
}

@Preview(widthDp = 402, heightDp = 941)
@Composable
private fun AlarmCreationGuideAnnotationPreview() {
    RingoutTheme {
        AlarmCreationGuideAnnotation(
            step = AlarmCreationGuideStep.Time,
            targetBounds = Rect(20f, 103f, 382f, 362f),
            reservedTop = 100,
            reservedBottom = 20,
        )
    }
}
