package com.joon.ringout.presentation.records.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.key
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.domain.missionhistory.MissionDate
import com.joon.ringout.domain.missionhistory.AlarmUsageRecord
import com.joon.ringout.domain.missionhistory.AlarmUsageRecordGroup
import com.joon.ringout.domain.missionhistory.groupByAlarm
import com.joon.ringout.domain.missionhistory.MissionResult
import com.joon.ringout.presentation.records.recordsColors
import com.joon.ringout.presentation.records.recordTimes
import org.jetbrains.compose.resources.painterResource
import ringout.shared.generated.resources.Res
import ringout.shared.generated.resources.records_chevron

@Composable
internal fun RecordHistoryCard(
    record: AlarmUsageRecordGroup,
    expanded: Boolean,
    onExpandedChange: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = recordsColors()
    val lastResult = record.entries.last().result
    val canCollapse = lastResult != null
    val showDetails = expanded || !canCollapse
    val times = record.entries.map { it.recordTimes() }
    val rowCount = if (showDetails) record.entries.size + record.entries.count { it.result != null } else 1
    val lastRowCenter = when (lastResult) {
        MissionResult.SUCCESS -> 24.dp
        MissionResult.FAILURE -> 29.dp
        null -> 28.dp
    }
    Column(modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.card)) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .then(if (canCollapse) Modifier
                    .clickable(role = Role.Button, onClickLabel = if (expanded) "기록 접기" else "기록 펼치기", onClick = onExpandedChange)
                    .semantics { stateDescription = if (expanded) "펼쳐짐" else "접힘" }
                else Modifier)
                .heightIn(min = 44.dp).padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(times.first().title, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = colors.text)
            if (canCollapse) Icon(painterResource(Res.drawable.records_chevron), null, Modifier.size(20.dp).rotate(if (expanded) -90f else 90f), tint = colors.text)
        }
        Column(
            modifier = Modifier.padding(start = 10.dp, end = 16.dp, bottom = 16.dp)
                .drawBehind {
                    if (rowCount > 1) {
                        drawLine(
                            color = colors.secondaryText,
                            start = Offset(19.dp.toPx(), 28.dp.toPx()),
                            end = Offset(19.dp.toPx(), size.height - lastRowCenter.toPx()),
                            strokeWidth = 4.dp.toPx(),
                        )
                    }
                },
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (showDetails) {
                record.entries.forEachIndexed { index, entry ->
                    key(entry.key) {
                        RecordRingingRow(times[index])
                        when (entry.result) {
                            MissionResult.SUCCESS -> RecordArrivalRow(completedTime = times[index].completedTime)
                            MissionResult.FAILURE -> RecordForceEndRow(completedTime = times[index].completedTime)
                            null -> Unit
                        }
                    }
                }
            } else {
                when (lastResult) {
                    MissionResult.SUCCESS -> RecordArrivalRow(completedTime = times.last().completedTime, showTime = false)
                    MissionResult.FAILURE -> RecordForceEndRow(completedTime = times.last().completedTime, showTime = false)
                }
            }
        }
    }
}

@Preview
@Composable
private fun RecordHistoryCardPreview() {
    RingoutTheme {
        RecordHistoryCard(
            listOf(AlarmUsageRecord(
                key = "preview", date = MissionDate.of(2026, 9, 28), result = MissionResult.SUCCESS,
                ringingStartedAtEpochMillis = 1790546400000L,
                ringingStoppedAtEpochMillis = 1790546700000L,
                missionCompletedAtEpochMillis = 1790547480000L,
            )).groupByAlarm().single(),
            expanded = true,
            onExpandedChange = {},
        )
    }
}

@Preview
@Composable
private fun RecordHistoryCardBeforeArrivalPreview() {
    RingoutTheme {
        RecordHistoryCard(
            listOf(AlarmUsageRecord(
                key = "preview", date = MissionDate.of(2026, 9, 28),
                ringingStartedAtEpochMillis = 1790546400000L,
                ringingStoppedAtEpochMillis = 1790546700000L,
            )).groupByAlarm().single(),
            expanded = false,
            onExpandedChange = {},
        )
    }
}

@Preview
@Composable
private fun RecordHistoryCardRingingPreview() {
    RingoutTheme {
        RecordHistoryCard(
            listOf(AlarmUsageRecord(
                key = "preview", date = MissionDate.of(2026, 9, 28),
                ringingStartedAtEpochMillis = 1790546400000L,
            )).groupByAlarm().single(),
            expanded = false,
            onExpandedChange = {},
        )
    }
}

@Preview(widthDp = 342)
@Composable
private fun RecordHistoryCardRepeatedRingingPreview() {
    RingoutTheme {
        RecordHistoryCard(previewRepeatedRinging(), expanded = false, onExpandedChange = {})
    }
}

@Preview(widthDp = 342)
@Composable
private fun RecordHistoryCardRepeatedArrivalPreview() {
    RingoutTheme {
        RecordHistoryCard(previewRepeatedRinging(result = MissionResult.SUCCESS), expanded = true, onExpandedChange = {})
    }
}

@Preview(widthDp = 342)
@Composable
private fun RecordHistoryCardForceEndPreview() {
    RingoutTheme {
        RecordHistoryCard(previewRepeatedRinging(result = MissionResult.FAILURE), expanded = true, onExpandedChange = {})
    }
}

@Preview(widthDp = 342)
@Composable
private fun RecordHistoryCardForceEndCollapsedPreview() {
    RingoutTheme {
        RecordHistoryCard(previewRepeatedRinging(result = MissionResult.FAILURE), expanded = false, onExpandedChange = {})
    }
}

private fun previewRepeatedRinging(result: MissionResult? = null): AlarmUsageRecordGroup =
    (0..2).map { index ->
        AlarmUsageRecord(
            key = "preview-$index", date = MissionDate.of(2026, 9, 28), alarmId = "same-alarm",
            ringingStartedAtEpochMillis = 1790546400000L + index * 300_000,
            ringingStoppedAtEpochMillis = 1790546460000L + index * 300_000,
            result = if (index == 2) result else null,
            missionCompletedAtEpochMillis = if (result != null && index == 2) 1790547480000L else null,
        )
    }.groupByAlarm().single()
