package com.joon.ringout.presentation.alarmsetup.components

import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import org.jetbrains.compose.resources.painterResource
import ringout.shared.generated.resources.Res
import ringout.shared.generated.resources.alarm_setup_sound

@Composable
fun AlarmSoundCard(
    soundName: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = alarmSetupColors()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(99.dp)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AlarmSetupSectionLabel("알람음", colors.primaryText)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(55.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(
                    role = Role.Button,
                    onClickLabel = "알람음 변경",
                    onClick = onClick,
                )
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(Res.drawable.alarm_setup_sound),
                    contentDescription = null,
                    modifier = Modifier.size(width = 22.dp, height = 22.dp),
                )
            }
            Text(
                text = soundName,
                modifier = Modifier.weight(1f),
                color = colors.primaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 18.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Image(
                painter = painterResource(colors.chevronIcon),
                contentDescription = null,
                modifier = Modifier
                    .size(width = 12.dp, height = 17.dp)
                    .graphicsLayer { scaleX = -1f },
            )
        }
    }
}

@Preview(widthDp = 402)
@Composable
private fun AlarmSoundCardPreview() {
    RingoutTheme(themeMode = ThemeMode.Dark) {
        AlarmSetupPreviewSurface {
            AlarmSoundCard(soundName = "Ring Ring Ring", onClick = {})
        }
    }
}
