package com.joon.ringout.presentation.ringing.components

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import com.joon.ringout.ringoutColors
import org.jetbrains.compose.resources.painterResource

internal data class AlarmRingingColors(
    val background: Color,
    val primaryText: Color,
    val emphasisText: Color,
    val secondaryText: Color,
)

internal fun alarmRingingColors(): AlarmRingingColors =
    AlarmRingingColors(
        background = Color.Black,
        primaryText = Color(0xFFF5F5F6),
        emphasisText = Color.White,
        secondaryText = Color(0xFFA7A9B0),
    )

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
            color = colors.emphasisText,
            maxLines = 1,
            softWrap = false,
            style = MaterialTheme.typography.displayLarge.copy(
                fontSize = 120.sp,
                lineHeight = 143.sp,
                fontWeight = FontWeight.Black,
            ),
        )
        AlarmIntervalBadge(intervalMinutes = limitMinutes)
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
private fun AlarmIntervalBadge(
    intervalMinutes: Int,
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
        Icon(
            painter = painterResource(AlarmRingingClockIconResource),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = Color.Unspecified,
        )
        Text(
            text = "알람 간격 ${intervalMinutes}분",
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
            .width(308.dp)
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
            color = MaterialTheme.ringoutColors.primaryActionContent,
            maxLines = 1,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 18.sp,
                lineHeight = 21.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
    }
}

@Preview(widthDp = 402, heightDp = 300)
@Composable
private fun AlarmTimeDetailsPreview() {
    RingoutTheme(themeMode = ThemeMode.Dark) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(alarmRingingColors().background)
                .padding(horizontal = 20.dp, vertical = 24.dp),
        ) {
            AlarmTimeDetails(
                alarmTime = "7:00",
                limitMinutes = 10,
                destinationName = "스터디카페",
            )
        }
    }
}

@Preview(widthDp = 402, heightDp = 112)
@Composable
private fun AlarmDismissButtonPreview() {
    RingoutTheme(themeMode = ThemeMode.Dark) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(alarmRingingColors().background)
                .padding(vertical = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            AlarmDismissButton(onClick = {})
        }
    }
}
