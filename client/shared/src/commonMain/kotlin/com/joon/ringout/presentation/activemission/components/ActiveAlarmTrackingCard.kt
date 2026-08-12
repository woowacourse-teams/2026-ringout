package com.joon.ringout.presentation.activemission.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode

@Composable
internal fun ActiveAlarmTrackingCard(
    destinationName: String,
    countdown: String,
    onForceEndHoldStarted: () -> Unit,
    onForceEndHoldCancelled: (holdDurationMillis: Long) -> Unit,
    onForceEndHoldComplete: (holdDurationMillis: Long) -> Unit,
    modifier: Modifier = Modifier,
    isForceEndEnabled: Boolean = true,
) {
    val colors = activeAlarmTrackingColors()
    val shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(243.dp)
            .dropShadow(
                shape = shape,
                shadow = Shadow(
                    radius = 24.dp,
                    color = colors.panelShadow,
                    spread = (-8).dp,
                    offset = DpOffset(0.dp, 8.dp),
                    alpha = 0.28f,
                ),
            )
            .clip(shape)
            .background(MaterialTheme.colorScheme.primary)
            .padding(20.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(112.dp)
                .semantics(mergeDescendants = true) {
                    contentDescription = "알람이 다시 울리기까지 $countdown"
                },
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(
                space = 4.dp,
                alignment = Alignment.CenterVertically,
            ),
        ) {
            Text(
                text = "알람이 다시 울리기까지",
                color = colors.panelContent,
                maxLines = 1,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
            Text(
                text = countdown,
                color = colors.panelContent,
                maxLines = 1,
                style = MaterialTheme.typography.displaySmall.copy(
                    fontFamily = FontFamily.Default,
                    fontSize = 36.sp,
                    lineHeight = 44.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .semantics(mergeDescendants = true) {
                        contentDescription = "설정한 목적지 $destinationName"
                    },
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "설정한 목적지",
                    color = colors.panelContent,
                    maxLines = 1,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 12.sp,
                        lineHeight = 14.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                )
                Text(
                    text = destinationName,
                    color = colors.panelContent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 22.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                    ),
                )
            }

            ForceEndHoldButton(
                onHoldStarted = onForceEndHoldStarted,
                onHoldCancelled = onForceEndHoldCancelled,
                onHoldComplete = onForceEndHoldComplete,
                enabled = isForceEndEnabled,
            )
        }
    }
}

@Preview(widthDp = 402, heightDp = 243)
@Composable
private fun ActiveAlarmTrackingCardPreview() {
    RingoutTheme(themeMode = ThemeMode.Dark) {
        ActiveAlarmTrackingCard(
            destinationName = "스터디카페",
            countdown = "11:42",
            onForceEndHoldStarted = {},
            onForceEndHoldCancelled = {},
            onForceEndHoldComplete = {},
        )
    }
}
