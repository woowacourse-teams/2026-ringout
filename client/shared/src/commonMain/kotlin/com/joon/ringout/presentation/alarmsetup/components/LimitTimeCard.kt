package com.joon.ringout.presentation.alarmsetup.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import com.joon.ringout.presentation.alarmsetup.MaxAlarmLimitMinutes
import com.joon.ringout.presentation.alarmsetup.MinAlarmLimitMinutes
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.painterResource

@Composable
fun LimitTimeCard(
    minutes: Int,
    onMinutesChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = alarmSetupColors()
    val sliderMinutes = minutes.coerceIn(
        minimumValue = MinAlarmLimitMinutes,
        maximumValue = MaxAlarmLimitMinutes,
    )
    val progress =
        (sliderMinutes - MinAlarmLimitMinutes).toFloat() /
            (MaxAlarmLimitMinutes - MinAlarmLimitMinutes).toFloat()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(142.dp)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AlarmSetupSectionLabel("반복 알람 간격", colors.primaryText)
            Text(
                text = buildAnnotatedString {
                    append("움직이지 않으면 ")
                    withStyle(
                        SpanStyle(
                            color = colors.accent,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    ) {
                        append("${sliderMinutes}분마다")
                    }
                    append(" 알람이 다시 울려요")
                },
                color = colors.supportingText,
                maxLines = 1,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 16.sp,
                    lineHeight = 27.sp,
                    fontWeight = FontWeight.Normal,
                ),
            )
        }
        Column(modifier = Modifier.fillMaxWidth()) {
            Slider(
                value = sliderMinutes.toFloat(),
                onValueChange = { value ->
                    onMinutesChange(
                        value.roundToInt().coerceIn(
                            MinAlarmLimitMinutes,
                            MaxAlarmLimitMinutes,
                        ),
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp),
                valueRange =
                    MinAlarmLimitMinutes.toFloat()..MaxAlarmLimitMinutes.toFloat(),
                steps = MaxAlarmLimitMinutes - MinAlarmLimitMinutes - 1,
                thumb = {
                    Image(
                        painter = painterResource(colors.sliderThumb),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                },
                track = {
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp),
                    ) {
                        val trackSlotWidth = maxWidth
                        val fullTrackWidth = trackSlotWidth + SliderThumbSize
                        Box(
                            modifier = Modifier
                                .requiredWidth(fullTrackWidth)
                                .fillMaxHeight()
                                .background(
                                    color = colors.inactiveTrack,
                                    shape = RoundedCornerShape(15.dp),
                                ),
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(SliderThumbRadius + trackSlotWidth * progress)
                                    .fillMaxHeight()
                                    .background(
                                        color = colors.accent,
                                        shape = RoundedCornerShape(15.dp),
                                    ),
                            )
                        }
                    }
                },
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SliderEndpointLabel("1분", colors.secondaryText)
                SliderEndpointLabel("30분", colors.secondaryText)
            }
        }
    }
}

@Composable
private fun SliderEndpointLabel(
    text: String,
    color: Color,
) {
    Text(
        text = text,
        color = color,
        maxLines = 1,
        style = MaterialTheme.typography.labelMedium.copy(
            fontSize = 12.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.Medium,
        ),
    )
}

private val SliderThumbSize = 16.dp
private val SliderThumbRadius = SliderThumbSize / 2

@Preview(widthDp = 402)
@Composable
private fun LimitTimeCardPreview() {
    RingoutTheme(themeMode = ThemeMode.Dark) {
        AlarmSetupPreviewSurface {
            LimitTimeCard(minutes = 13, onMinutesChange = {})
        }
    }
}
