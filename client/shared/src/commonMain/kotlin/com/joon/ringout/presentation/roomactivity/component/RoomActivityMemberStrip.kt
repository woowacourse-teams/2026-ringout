package com.joon.ringout.presentation.roomactivity.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.presentation.roomactivity.RoomActivityMemberStatus
import com.joon.ringout.presentation.roomactivity.RoomActivityMemberUiModel
import com.joon.ringout.presentation.roomactivity.color
import com.joon.ringout.presentation.roomhome.roomHomeColors

@Composable
internal fun RoomActivityMemberStrip(
    members: List<RoomActivityMemberUiModel>,
    onMembersClick: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    BoxWithConstraints(modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
        val minimumMemberWidth = ((if (fontScale > 1.2f) 70 else 60) * fontScale).dp
        val capacity = ((maxWidth - 50.dp) / minimumMemberWidth).toInt().coerceIn(1, 4)
        val memberWidth = ((maxWidth - 50.dp) / capacity).coerceAtMost((70 * fontScale).dp)
        val ordered = members.sortedByDescending { it.isMe }
        val visible = ordered.take(capacity)
        val hidden = ordered.drop(capacity)
        Row(
            modifier = Modifier.fillMaxWidth().clickable(
                enabled = ordered.isNotEmpty(),
                role = Role.Button,
                onClick = { onMembersClick(ordered.map { it.id }) },
            ),
            horizontalArrangement = Arrangement.Center,
        ) {
            visible.forEach { member ->
                Column(
                    modifier = Modifier.width(memberWidth).padding(horizontal = 5.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    RoomActivityMemberAvatar(
                        highlighted = member.status == RoomActivityMemberStatus.Moving ||
                            member.status == RoomActivityMemberStatus.Arrived,
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        member.displayName,
                        color = roomHomeColors().content,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium,
                        ),
                    )
                    Text(
                        member.status.label,
                        color = member.status.color(),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp),
                    )
                }
            }
            if (hidden.isNotEmpty()) {
                Box(
                    modifier = Modifier.width(50.dp).padding(top = 5.dp).sizeIn(minHeight = 48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier.size(38.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "+${hidden.size}",
                            color = roomHomeColors().iconContent,
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                        )
                    }
                }
            }
        }
    }
}

@Preview(widthDp = 402)
@Composable
private fun RoomActivityMemberStripPreview() {
    RingoutTheme { RoomActivityMemberStrip(RoomActivityPreviewState.members, {}) }
}
