package com.joon.ringout.presentation.roomhome.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import com.joon.ringout.presentation.roomhome.RoomHomeRecordEvent
import com.joon.ringout.presentation.roomhome.RoomHomeRecordUiModel
import com.joon.ringout.presentation.roomhome.roomHomeColors
import org.jetbrains.compose.resources.painterResource
import ringout.shared.generated.resources.Res
import ringout.shared.generated.resources.records_ringing

@Composable
internal fun RoomHomeRecordRow(
    record: RoomHomeRecordUiModel,
    modifier: Modifier = Modifier,
    hasPrevious: Boolean = false,
    hasNext: Boolean = false,
    memberCount: Int = 1,
    compactNickname: Boolean = false,
) {
    val colors = roomHomeColors()
    val primary = MaterialTheme.colorScheme.primary
    val iconBackground = when (record.event) {
        RoomHomeRecordEvent.Ringing, RoomHomeRecordEvent.Moving -> primary
        RoomHomeRecordEvent.Arrived -> colors.success
        RoomHomeRecordEvent.Dismissed, RoomHomeRecordEvent.ForceEnded -> colors.failure
    }
    val labelColor = when (record.event) {
        RoomHomeRecordEvent.Ringing -> primary
        RoomHomeRecordEvent.Arrived -> colors.successText
        RoomHomeRecordEvent.ForceEnded -> colors.secondary
        RoomHomeRecordEvent.Dismissed, RoomHomeRecordEvent.Moving -> colors.content
    }
    val memberSuffix = "님" + if (memberCount > 1) " 외 ${memberCount - 1}명" else ""
    val eventLabel = when (record.event) {
        RoomHomeRecordEvent.Ringing -> if (record.repeatCount != null) {
            ", 알람 재울림 (${record.repeatCount}회)"
        } else {
            ", 알람 울림" + if (record.ringCount > 1) " (${record.ringCount}회)" else ""
        }
        RoomHomeRecordEvent.Dismissed -> ", 알람 종료"
        RoomHomeRecordEvent.Moving -> ", 이동 시작"
        RoomHomeRecordEvent.Arrived -> "이 목적지에 도착"
        RoomHomeRecordEvent.ForceEnded -> ", 알람 강제 종료"
    }

    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp)
            .drawBehind {
                val x = 19.dp.toPx()
                if (hasPrevious) {
                    drawLine(colors.timeline, Offset(x, 0f), Offset(x, size.height / 2), strokeWidth = 5.dp.toPx())
                }
                if (hasNext) {
                    drawLine(
                        colors.timeline,
                        Offset(x, size.height / 2),
                        Offset(x, size.height + 8.dp.toPx()),
                        strokeWidth = 5.dp.toPx(),
                    )
                }
            }
            .semantics(mergeDescendants = true) {}
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(38.dp).background(iconBackground, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            when (record.event) {
                RoomHomeRecordEvent.Ringing, RoomHomeRecordEvent.Dismissed -> Image(
                    painter = painterResource(Res.drawable.records_ringing),
                    contentDescription = null,
                    modifier = Modifier.size(26.dp).then(
                        if (record.event == RoomHomeRecordEvent.Dismissed) {
                            Modifier.drawWithContent {
                                drawContent()
                                drawLine(
                                    color = colors.iconContent,
                                    start = Offset(size.width * 0.08f, size.height * 0.92f),
                                    end = Offset(size.width * 0.92f, size.height * 0.08f),
                                    strokeWidth = 2.dp.toPx(),
                                )
                            }
                        } else Modifier,
                    ),
                    contentScale = ContentScale.Crop,
                )
                RoomHomeRecordEvent.Moving -> Icon(
                    painter = painterResource(RoomHomeRecordMovingIconResource),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = colors.iconContent,
                )
                RoomHomeRecordEvent.Arrived -> Icon(
                    painter = painterResource(RoomHomeRecordArrivedIconResource),
                    contentDescription = null,
                    modifier = Modifier.size(26.dp),
                    tint = colors.iconContent,
                )
                RoomHomeRecordEvent.ForceEnded -> Icon(
                    painter = painterResource(RoomHomeRecordForceEndedIconResource),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = colors.iconContent,
                )
            }
        }
        Text(
            text = record.timeText,
            color = colors.content,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 16.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.SemiBold,
            ),
        )
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (record.event != RoomHomeRecordEvent.Ringing || memberCount == 1) {
                RoomHomeParticipantAvatars(
                    count = memberCount,
                    profileImageUrls = listOf(record.profileImageUrl),
                    avatarSize = if (record.event == RoomHomeRecordEvent.Arrived) 20.dp else 19.dp,
                )
            }
            val labelStyle = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Medium,
            )
            val fontScale = LocalDensity.current.fontScale
            BoxWithConstraints(modifier = Modifier.weight(1f)) {
                if (compactNickname && fontScale <= 1.2f && maxWidth >= 150.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = record.nickname,
                            modifier = Modifier.weight(1f, fill = false),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = labelColor,
                            style = labelStyle,
                        )
                        Text(text = memberSuffix + eventLabel, color = labelColor, style = labelStyle)
                    }
                } else {
                    Text(text = record.nickname + memberSuffix + eventLabel, color = labelColor, style = labelStyle)
                }
            }
        }
    }
}

@Preview(name = "모임 기록 행 · 모든 이벤트", widthDp = 342)
@Composable
private fun RoomHomeRecordRowPreview() {
    RingoutTheme(ThemeMode.Light) {
        Column(
            modifier = Modifier.background(roomHomeColors().background).padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RoomHomePreviewRecords.forEachIndexed { index, record ->
                RoomHomeRecordRow(
                    record = record,
                    compactNickname = true,
                    hasPrevious = index > 0,
                    hasNext = index < RoomHomePreviewRecords.lastIndex,
                )
            }
        }
    }
}

@Preview(name = "모임 기록 행 · 다크", widthDp = 342)
@Composable
private fun RoomHomeRecordRowDarkPreview() {
    RingoutTheme(ThemeMode.Dark) {
        Column(
            modifier = Modifier.background(roomHomeColors().background).padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(RoomHomeRecordEvent.Moving, RoomHomeRecordEvent.Arrived, RoomHomeRecordEvent.ForceEnded).forEach { event ->
                RoomHomeRecordRow(
                    record = RoomHomePreviewRecords.first { it.event == event },
                    compactNickname = true,
                )
            }
        }
    }
}
