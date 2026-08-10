package com.joon.ringout.presentation.mypage.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import com.joon.ringout.domain.missionhistory.MissionResult
import org.jetbrains.compose.resources.painterResource
import ringout.shared.generated.resources.Res
import ringout.shared.generated.resources.mypage_mission_stamp

@Composable
internal fun MissionResultIndicator(
    day: Int,
    result: MissionResult,
    modifier: Modifier = Modifier,
) {
    when (result) {
        MissionResult.SUCCESS -> Image(
            painter = painterResource(Res.drawable.mypage_mission_stamp),
            contentDescription = "${day}일 미션 성공",
            modifier = modifier.size(23.dp),
        )

        MissionResult.FAILURE -> MissionFailureMark(
            day = day,
            modifier = modifier,
        )
    }
}

@Composable
private fun MissionFailureMark(
    day: Int,
    modifier: Modifier = Modifier,
) {
    val failureColor = MaterialTheme.colorScheme.error
    Canvas(
        modifier = modifier
            .size(18.dp)
            .semantics { contentDescription = "${day}일 미션 실패" },
    ) {
        val inset = size.minDimension * 0.24f
        val strokeWidth = 2.dp.toPx()
        drawLine(
            color = failureColor,
            start = Offset(inset, inset),
            end = Offset(size.width - inset, size.height - inset),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = failureColor,
            start = Offset(size.width - inset, inset),
            end = Offset(inset, size.height - inset),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
    }
}

@Preview
@Composable
private fun MissionResultIndicatorPreview() {
    RingoutTheme(ThemeMode.Light) {
        Row(
            modifier = Modifier.padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            MissionResultIndicator(day = 3, result = MissionResult.SUCCESS)
            MissionResultIndicator(day = 4, result = MissionResult.FAILURE)
        }
    }
}
