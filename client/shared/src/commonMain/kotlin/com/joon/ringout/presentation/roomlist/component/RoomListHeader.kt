package com.joon.ringout.presentation.roomlist.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.presentation.home.components.homeAlarmColors
import com.joon.ringout.ringoutColors

@Composable
internal fun RoomListHeader(
    onCreateRoom: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val plusColor = MaterialTheme.colorScheme.onBackground
    val descriptionColor = homeAlarmColors().secondaryText

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "모임",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontSize = 28.sp,
                    lineHeight = 34.sp,
                    fontWeight = FontWeight.Black,
                ),
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "모임에 참여하거나 직접 만들어보세요",
                color = descriptionColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 18.sp,
                    lineHeight = 21.6.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
        }
        IconButton(
            onClick = onCreateRoom,
            modifier = Modifier
                .size(48.dp)
                .offset(x = 10.dp)
                .semantics { contentDescription = "모임 만들기" },
        ) {
            Canvas(Modifier.size(26.dp)) {
                val stroke = 3.dp.toPx()
                drawLine(
                    color = plusColor,
                    start = Offset(size.width / 2f, size.height * 0.12f),
                    end = Offset(size.width / 2f, size.height * 0.88f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = plusColor,
                    start = Offset(size.width * 0.12f, size.height / 2f),
                    end = Offset(size.width * 0.88f, size.height / 2f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

@Preview(name = "모임 목록 헤더", widthDp = 402, showBackground = true)
@Composable
private fun RoomListHeaderPreview() {
    RingoutTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.ringoutColors.mainScreenBackground)
                .padding(20.dp),
        ) {
            RoomListHeader(onCreateRoom = {})
        }
    }
}
