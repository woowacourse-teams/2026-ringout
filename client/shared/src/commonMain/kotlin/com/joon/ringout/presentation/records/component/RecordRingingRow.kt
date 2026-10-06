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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import com.joon.ringout.domain.missionhistory.AlarmUsageRecord
import com.joon.ringout.domain.missionhistory.MissionDate
import com.joon.ringout.presentation.records.RecordTimesUiState
import com.joon.ringout.presentation.records.recordTimes
import com.joon.ringout.presentation.records.recordsColors
import org.jetbrains.compose.resources.painterResource
import ringout.shared.generated.resources.Res
import ringout.shared.generated.resources.records_ringing

@Composable
internal fun RecordRingingRow(
    times: RecordTimesUiState,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(38.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painterResource(Res.drawable.records_ringing), null,
                modifier = Modifier.size(26.dp).clip(RoundedCornerShape(0.dp)), contentScale = ContentScale.Crop,
            )
        }
        Text(
            text = times.ringingRange,
            modifier = Modifier.weight(1f).semantics {
                contentDescription = "${times.ringingDescription}: ${times.ringingRange}"
            },
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            color = recordsColors().text,
        )
    }
}

@Preview(widthDp = 313)
@Composable
private fun RecordRingingRowPreview() {
    RingoutTheme {
        RecordRingingRow(
            AlarmUsageRecord(
                key = "preview", date = MissionDate.of(2026, 9, 28),
                ringingStartedAtEpochMillis = 1790546400000L,
                ringingStoppedAtEpochMillis = 1790546460000L,
            ).recordTimes(),
            modifier = Modifier.background(recordsColors().card),
        )
    }
}
