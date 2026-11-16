package com.joon.ringout.presentation.records.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.svg.SvgDecoder
import com.joon.ringout.RingoutTheme
import com.joon.ringout.domain.missionhistory.AlarmRecordShareData
import com.joon.ringout.domain.missionhistory.AlarmUsageRecord
import com.joon.ringout.domain.missionhistory.AlarmUsageRecordGroup
import com.joon.ringout.domain.missionhistory.MissionDate
import com.joon.ringout.domain.missionhistory.MissionResult
import com.joon.ringout.domain.missionhistory.groupByAlarm
import com.joon.ringout.domain.missionhistory.toShareableAlarmRecord
import com.joon.ringout.presentation.records.recordTimes
import com.joon.ringout.presentation.records.recordsColors
import org.jetbrains.compose.resources.ExperimentalResourceApi
import ringout.shared.generated.resources.Res

@OptIn(ExperimentalResourceApi::class)
@Composable
internal fun RecordDetailGroup(
    record: AlarmUsageRecordGroup,
    onShare: (AlarmRecordShareData) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = recordsColors()
    val primary = MaterialTheme.colorScheme.primary
    val times = record.entries.map { it.recordTimes() }
    val share = record.toShareableAlarmRecord()
    val rowCount = record.entries.size + record.entries.count { it.result != null }
    val lastCenter = when (record.entries.last().result) {
        MissionResult.SUCCESS -> 24.dp
        MissionResult.FAILURE -> 29.dp
        null -> 28.dp
    }
    Column(modifier.fillMaxWidth().drawBehind {
        drawLine(primary, Offset(0f, size.height - 1.dp.toPx()), Offset(size.width, size.height - 1.dp.toPx()), 2.dp.toPx())
    }.padding(vertical = 10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(start = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(times.first().title, Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = colors.text)
            if (share != null) {
                val context = LocalPlatformContext.current
                val request = remember(context) {
                    ImageRequest.Builder(context)
                        .data(Res.getUri("drawable/records_share.svg"))
                        .decoderFactory(SvgDecoder.Factory())
                        .build()
                }
                AsyncImage(
                    model = request,
                    contentDescription = "알람 기록 공유하기",
                    modifier = Modifier.size(44.dp).clickable(
                        interactionSource = remember { MutableInteractionSource() }, indication = null,
                        role = Role.Button, onClick = { onShare(share) },
                    ).padding(11.dp),
                    colorFilter = ColorFilter.tint(colors.text),
                    contentScale = ContentScale.Fit,
                )
            }
        }
        Column(
            modifier = Modifier.padding(bottom = 10.dp).drawBehind {
                if (rowCount > 1) drawLine(
                    color = colors.secondaryText,
                    start = Offset(19.dp.toPx(), 28.dp.toPx()),
                    end = Offset(19.dp.toPx(), size.height - lastCenter.toPx()),
                    strokeWidth = 2.dp.toPx(),
                )
            },
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            record.entries.forEachIndexed { index, entry ->
                key(entry.key) {
                    RecordRingingRow(times[index])
                    when (entry.result) {
                        MissionResult.SUCCESS -> RecordArrivalRow(times[index].completedTime)
                        MissionResult.FAILURE -> RecordForceEndRow(times[index].completedTime)
                        null -> Unit
                    }
                }
            }
        }
    }
}

@Preview(widthDp = 342)
@Composable
private fun RecordDetailGroupPreview() {
    RingoutTheme {
        val date = MissionDate.of(2026, 8, 17)
        RecordDetailGroup(
            listOf(AlarmUsageRecord("sample", date, result = MissionResult.SUCCESS,
                ringingStartedAtEpochMillis = 1786944600000, missionCompletedAtEpochMillis = 1786945980000)).groupByAlarm().single(),
            onShare = {}, modifier = Modifier.background(recordsColors().background),
        )
    }
}
