package com.joon.ringout.presentation.ringing.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.painterResource
import ringout.shared.generated.resources.Res
import ringout.shared.generated.resources.ringing_bell_dark
import ringout.shared.generated.resources.ringing_clock

internal data class AlarmRingingColors(
    val background: Color,
    val primaryText: Color,
    val secondaryText: Color,
)

internal fun alarmRingingColors(): AlarmRingingColors =
    AlarmRingingColors(
        background = Color.Black,
        primaryText = Color.White,
        secondaryText = Color.White.copy(alpha = 0.8f),
    )

@Composable
internal fun AlarmBellVisual(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(Res.drawable.ringing_bell_dark),
        contentDescription = "알람이 울리고 있어요",
        modifier = modifier.size(149.dp),
        contentScale = ContentScale.Fit,
    )
}

@Composable
internal fun AlarmTimeDetails(
    alarmTime: String,
    limitMinutes: Int,
    destinationName: String,
    modifier: Modifier = Modifier,
) {
    val colors = alarmRingingColors()

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = alarmTime,
            color = colors.primaryText,
            maxLines = 1,
            softWrap = false,
            style = MaterialTheme.typography.displayLarge.copy(
                fontSize = 120.sp,
                lineHeight = 143.sp,
                fontWeight = FontWeight.Black,
            ),
        )
        AlarmLimitBadge(limitMinutes = limitMinutes)
        Text(
            text = "목적지: $destinationName",
            modifier = Modifier.fillMaxWidth(),
            color = colors.secondaryText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 18.sp,
                lineHeight = 21.sp,
                fontWeight = FontWeight.SemiBold,
            ),
        )
    }
}

@Composable
private fun AlarmLimitBadge(
    limitMinutes: Int,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(999.dp)
    val accentColor = MaterialTheme.colorScheme.primary

    Row(
        modifier = modifier
            .clip(shape)
            .background(accentColor.copy(alpha = 0.1f))
            .border(1.dp, accentColor, shape)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(Res.drawable.ringing_clock),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = "제한시간 ${limitMinutes}분",
            color = accentColor,
            maxLines = 1,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 18.sp,
                lineHeight = 21.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
    }
}

@Composable
internal fun AlarmDismissButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accentColor = MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(accentColor)
            .clickable(
                role = Role.Button,
                onClickLabel = "알람 끄고 목적지로 이동",
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "알람 끄고 목적지로 이동하기",
            color = Color.White,
            maxLines = 1,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 18.sp,
                lineHeight = 21.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
    }
}
