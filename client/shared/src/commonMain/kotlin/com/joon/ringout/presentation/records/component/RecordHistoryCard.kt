package com.joon.ringout.presentation.records.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.domain.missionhistory.AlarmUsageRecord
import com.joon.ringout.domain.missionhistory.AlarmUsageRecordGroup
import com.joon.ringout.domain.missionhistory.MissionDate
import com.joon.ringout.domain.missionhistory.MissionResult
import com.joon.ringout.domain.missionhistory.groupByAlarm
import com.joon.ringout.presentation.records.recordTimes
import com.joon.ringout.presentation.records.recordsColors

@Composable
internal fun RecordHistoryCard(
    record: AlarmUsageRecordGroup,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = recordsColors()
    val first = record.entries.first()
    val last = record.entries.last()
    val firstTimes = first.recordTimes()
    val lastTimes = last.recordTimes()
    val firstRinging = record.entries.firstOrNull {
        it.ringingCount > 0 || it.result == null
    }
    val lastRowCenter = when (last.result) {
        MissionResult.FAILURE -> 29.dp
        MissionResult.SUCCESS -> 24.dp
        null -> 28.dp
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.card)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClickLabel = "알람 기록 자세히 보기",
                onClick = onClick,
            )
            .padding(10.dp),
    ) {
        Text(
            text = firstTimes.title,
            modifier = Modifier.padding(
                horizontal = 10.dp,
                vertical = 5.dp,
            ),
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = colors.text,
        )

        Column(
            modifier = Modifier.drawBehind {
                if (firstRinging != null && last.result != null) {
                    drawLine(
                        color = colors.secondaryText,
                        start = Offset(
                            x = 19.dp.toPx(),
                            y = 28.dp.toPx(),
                        ),
                        end = Offset(
                            x = 19.dp.toPx(),
                            y = size.height - lastRowCenter.toPx(),
                        ),
                        strokeWidth = 2.dp.toPx(),
                    )
                }
            },
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            firstRinging?.let {
                RecordRingingRow(it.recordTimes())
            }

            when (last.result) {
                MissionResult.SUCCESS -> {
                    RecordArrivalRow(
                        completedTime = lastTimes.completedTime,
                    )
                }

                MissionResult.FAILURE -> {
                    RecordForceEndRow(
                        completedTime = lastTimes.completedTime,
                    )
                }

                null -> Unit
            }
        }
    }
}

@Preview(widthDp = 342)
@Composable
private fun RecordHistoryCardPreview() {
    RingoutTheme {
        RecordHistoryCard(
            record = listOf(
                AlarmUsageRecord(
                    key = "preview",
                    date = MissionDate.of(2026, 9, 28),
                    result = MissionResult.SUCCESS,
                    ringingStartedAtEpochMillis = 1790546400000L,
                    ringingStoppedAtEpochMillis = 1790546700000L,
                    missionCompletedAtEpochMillis = 1790547480000L,
                ),
            ).groupByAlarm().single(),
            onClick = {},
        )
    }
}
