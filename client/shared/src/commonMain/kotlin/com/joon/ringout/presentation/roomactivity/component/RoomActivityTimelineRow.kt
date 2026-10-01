package com.joon.ringout.presentation.roomactivity.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.presentation.roomactivity.RoomActivityTimelineUiModel
import com.joon.ringout.presentation.roomhome.component.RoomHomeRecordRow
import com.joon.ringout.presentation.roomhome.roomHomeColors

@Composable
internal fun RoomActivityTimelineRow(
    item: RoomActivityTimelineUiModel,
    onMembersClick: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
    hasPrevious: Boolean = false,
    hasNext: Boolean = false,
) {
    val memberIds = item.memberIds.distinct()
    Column(modifier = modifier) {
        item.dateLabel?.let { date ->
            Text(
                date,
                modifier = Modifier.padding(start = 48.dp, top = 8.dp, bottom = 4.dp),
                color = roomHomeColors().secondary,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        RoomHomeRecordRow(
            record = item.record,
            modifier = if (memberIds.size > 1) Modifier.clickable(
                role = Role.Button,
                onClickLabel = "함께 기록된 회원 보기",
                onClick = { onMembersClick(memberIds) },
            ) else Modifier,
            hasPrevious = hasPrevious && item.dateLabel == null,
            hasNext = hasNext,
            memberCount = memberIds.size.coerceAtLeast(1),
            compactNickname = true,
        )
    }
}

@Preview(widthDp = 342)
@Composable
private fun RoomActivityTimelineRowPreview() {
    RingoutTheme { RoomActivityTimelineRow(RoomActivityAllEventsPreviewState.timeline.first(), {}) }
}
