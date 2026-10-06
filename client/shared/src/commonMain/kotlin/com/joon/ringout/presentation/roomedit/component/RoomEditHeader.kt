package com.joon.ringout.presentation.roomedit.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode

@Composable
internal fun RoomEditHeader(
    onBackClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val arrowColor = MaterialTheme.colorScheme.onSurface
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onBackClick,
            enabled = enabled,
            modifier = Modifier
                .size(44.dp)
                .semantics { contentDescription = "뒤로 가기" },
        ) {
            Canvas(Modifier.size(22.dp)) {
                val stroke = 2.dp.toPx()
                drawLine(
                    color = arrowColor,
                    start = Offset(size.width * 0.78f, size.height * 0.5f),
                    end = Offset(size.width * 0.22f, size.height * 0.5f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = arrowColor,
                    start = Offset(size.width * 0.22f, size.height * 0.5f),
                    end = Offset(size.width * 0.5f, size.height * 0.22f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = arrowColor,
                    start = Offset(size.width * 0.22f, size.height * 0.5f),
                    end = Offset(size.width * 0.5f, size.height * 0.78f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = "모임 수정하기",
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 16.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
    }
}

@Preview(name = "모임 수정 헤더 · 라이트")
@Composable
private fun RoomEditHeaderLightPreview() {
    RingoutTheme(ThemeMode.Light) {
        RoomEditHeader(onBackClick = {})
    }
}

@Preview(name = "모임 수정 헤더 · 다크")
@Composable
private fun RoomEditHeaderDarkPreview() {
    RingoutTheme(ThemeMode.Dark) {
        RoomEditHeader(onBackClick = {})
    }
}
