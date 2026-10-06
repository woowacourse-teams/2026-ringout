package com.joon.ringout.presentation.roomhome.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.presentation.roomhome.roomHomeColors

@Composable
internal fun RoomHomeDescription(description: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "소개",
            color = roomHomeColors().content,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
        )
        Text(
            text = description,
            color = roomHomeColors().secondary,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun RoomHomeDescriptionPreview() {
    RingoutTheme {
        RoomHomeDescription(description = "매일 아침 함께 달려요. 편한 날에 참여해 주세요.")
    }
}
