package com.joon.ringout.presentation.records.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.domain.missionhistory.MissionDate
import com.joon.ringout.domain.missionhistory.MissionHistoryEntry
import com.joon.ringout.domain.missionhistory.MissionResult
import com.joon.ringout.presentation.records.recordsColors
import org.jetbrains.compose.resources.painterResource
import ringout.shared.generated.resources.Res
import ringout.shared.generated.resources.mypage_mission_stamp
import ringout.shared.generated.resources.records_chevron
import ringout.shared.generated.resources.records_failure
import ringout.shared.generated.resources.records_ringing

@Composable
internal fun RecordHistoryCard(
    record: MissionHistoryEntry,
    number: Int,
    expanded: Boolean,
    onExpandedChange: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = recordsColors()
    val success = record.result == MissionResult.SUCCESS
    Column(modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.card)) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .clickable(role = Role.Button, onClickLabel = if (expanded) "기록 접기" else "기록 펼치기", onClick = onExpandedChange)
                .semantics { stateDescription = if (expanded) "펼쳐짐" else "접힘" }
                .heightIn(min = 44.dp).padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("알람 사용 기록 $number", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = colors.text)
            Icon(painterResource(Res.drawable.records_chevron), null, Modifier.size(20.dp).rotate(if (expanded) -90f else 90f), tint = colors.text)
        }
        Column(
            modifier = Modifier.padding(start = 10.dp, end = 16.dp, bottom = 16.dp)
                .drawBehind {
                    if (expanded) {
                        drawLine(
                            color = colors.secondaryText,
                            start = Offset(19.dp.toPx(), 24.dp.toPx()),
                            end = Offset(19.dp.toPx(), size.height - 24.dp.toPx()),
                            strokeWidth = 4.dp.toPx(),
                        )
                    }
                },
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (expanded) {
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        Modifier.size(38.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            painterResource(Res.drawable.records_ringing), null,
                            modifier = Modifier.size(26.dp).clip(RoundedCornerShape(0.dp)), contentScale = ContentScale.Crop,
                        )
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("--:-- ~ --:--", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = colors.text)
                        Text("울림·종료 시각 기록 없음", style = MaterialTheme.typography.bodySmall, color = colors.secondaryText)
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    Modifier.size(38.dp).clip(CircleShape).background(if (success) colors.successSurface else colors.failureSurface),
                    contentAlignment = Alignment.Center,
                ) {
                    if (success) Image(painterResource(Res.drawable.mypage_mission_stamp), null, Modifier.size(30.dp))
                    else Icon(painterResource(Res.drawable.records_failure), null, Modifier.size(30.dp), tint = colors.failure)
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        if (success) "목적지 도착 완료!" else "미션 실패",
                        fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (success) colors.success else colors.failure,
                    )
                    if (expanded) Text("완료 시각 기록 없음", style = MaterialTheme.typography.bodySmall, color = colors.secondaryText)
                }
            }
        }
    }
}

@Preview
@Composable
private fun RecordHistoryCardPreview() {
    RingoutTheme { RecordHistoryCard(MissionHistoryEntry(MissionResult.SUCCESS, MissionDate.of(2026, 9, 28)), 1, true, {}) }
}

@Preview
@Composable
private fun RecordHistoryCardFailurePreview() {
    RingoutTheme { RecordHistoryCard(MissionHistoryEntry(MissionResult.FAILURE, MissionDate.of(2026, 9, 28)), 2, false, {}) }
}
