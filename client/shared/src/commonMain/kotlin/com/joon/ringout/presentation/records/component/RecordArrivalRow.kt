package com.joon.ringout.presentation.records.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.presentation.records.recordsColors
import org.jetbrains.compose.resources.painterResource
import ringout.shared.generated.resources.Res
import ringout.shared.generated.resources.mypage_mission_stamp

@Composable
internal fun RecordArrivalRow(
    completedTime: String?,
    modifier: Modifier = Modifier,
    showTime: Boolean = true,
) {
    val colors = recordsColors()
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier.size(38.dp).clip(CircleShape).background(colors.successSurface),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(Res.drawable.mypage_mission_stamp),
                contentDescription = null,
                modifier = Modifier.size(30.dp),
                contentScale = ContentScale.Crop,
            )
        }
        if (showTime) {
            Text(
                text = completedTime ?: "--:--",
                modifier = Modifier.semantics {
                    contentDescription = completedTime?.let { "미션 완료 $it" } ?: "완료 시각 기록 없음"
                },
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.text,
            )
        }
        Text(
            text = "목적지 도착 완료!",
            modifier = Modifier.weight(1f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = colors.success,
        )
    }
}

@Preview(widthDp = 313)
@Composable
private fun RecordArrivalRowPreview() {
    RingoutTheme {
        RecordArrivalRow("06:53", Modifier.background(recordsColors().card))
    }
}

@Preview(widthDp = 313)
@Composable
private fun RecordArrivalRowUnknownTimePreview() {
    RingoutTheme {
        RecordArrivalRow(null, Modifier.background(recordsColors().card))
    }
}
