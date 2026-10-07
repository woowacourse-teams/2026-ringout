package com.joon.ringout.presentation.roomhome.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.presentation.roomhome.roomHomeColors

@Composable
internal fun RoomHomeOngoingActivityCard(
    participantCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp))
            .background(MaterialTheme.colorScheme.primary)
            .clickable(role = Role.Button, onClickLabel = "모임 활동 보기", onClick = onClick)
            .heightIn(min = 131.dp).padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
    ) {
        Text(
            "현재 진행 중인 활동이 있어요!",
            color = roomHomeColors().iconContent,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${participantCount}명",
                color = roomHomeColors().iconContent,
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 58.sp, fontWeight = FontWeight.Bold),
            )
            Text(
                "모임 참여 중",
                color = roomHomeColors().iconContent,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp),
            )
        }
    }
}

@Preview(widthDp = 362)
@Composable
private fun RoomHomeOngoingActivityCardPreview() {
    RingoutTheme { RoomHomeOngoingActivityCard(3, {}) }
}
