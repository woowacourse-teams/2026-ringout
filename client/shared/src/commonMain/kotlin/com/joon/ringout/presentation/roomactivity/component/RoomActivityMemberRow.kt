package com.joon.ringout.presentation.roomactivity.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.presentation.roomactivity.RoomActivityMemberUiModel
import com.joon.ringout.presentation.roomactivity.color
import com.joon.ringout.presentation.roomhome.roomHomeColors

@Composable
internal fun RoomActivityMemberRow(member: RoomActivityMemberUiModel, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = 79.dp).padding(horizontal = 10.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        RoomActivityMemberAvatar(profileImageUrl = member.profileImageUrl)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                member.displayName,
                color = roomHomeColors().content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 18.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold,
                ),
            )
            Text(
                member.status.label,
                color = member.status.color(),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 18.sp),
            )
        }
    }
}

@Preview(widthDp = 402)
@Composable
private fun RoomActivityMemberRowPreview() {
    RingoutTheme { RoomActivityMemberRow(RoomActivityPreviewState.members[1]) }
}
