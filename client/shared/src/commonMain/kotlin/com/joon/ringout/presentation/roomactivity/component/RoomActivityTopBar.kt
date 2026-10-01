package com.joon.ringout.presentation.roomactivity.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.presentation.roomhome.component.RoomHomeBackIconResource
import com.joon.ringout.presentation.roomhome.roomHomeColors
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun RoomActivityTopBar(
    title: String,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = 61.dp).padding(end = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(48.dp).clickable(role = Role.Button, onClick = onBackClick),
            contentAlignment = Alignment.CenterStart,
        ) {
            Icon(
                painter = painterResource(RoomHomeBackIconResource),
                contentDescription = "뒤로",
                modifier = Modifier.size(44.dp),
                tint = roomHomeColors().content,
            )
        }
        Text(
            text = title,
            modifier = Modifier.padding(start = 6.dp).semantics { heading() },
            color = roomHomeColors().content,
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 22.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold,
            ),
        )
    }
}

@Preview
@Composable
private fun RoomActivityTopBarPreview() {
    RingoutTheme { RoomActivityTopBar("활동", {}) }
}
