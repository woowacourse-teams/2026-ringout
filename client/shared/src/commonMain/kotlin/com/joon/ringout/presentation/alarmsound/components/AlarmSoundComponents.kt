package com.joon.ringout.presentation.alarmsound.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joon.ringout.LocalRingoutThemeMode
import com.joon.ringout.ThemeMode
import com.joon.ringout.presentation.alarmsetup.AlarmSoundSelection
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import ringout.shared.generated.resources.Res
import ringout.shared.generated.resources.alarm_setup_back_dark
import ringout.shared.generated.resources.alarm_setup_back_light
import ringout.shared.generated.resources.alarm_setup_sound

internal val AlarmSoundOrange = Color(0xFFFF6D2E)

internal data class AlarmSoundColors(
    val background: Color,
    val title: Color,
    val itemBackground: Color,
    val selectedItemBackground: Color,
    val itemText: Color,
    val selectedItemText: Color,
    val unselectedSoundIcon: Color,
    val unselectedIndicator: Color,
    val backIcon: DrawableResource,
)

@Composable
internal fun alarmSoundColors(): AlarmSoundColors =
    if (LocalRingoutThemeMode.current == ThemeMode.Dark) {
        AlarmSoundColors(
            background = Color.Black,
            title = Color.White,
            itemBackground = Color(0xFF1A1A1A),
            selectedItemBackground = Color(0xFF1A1A1A),
            itemText = Color.White,
            selectedItemText = Color.White,
            unselectedSoundIcon = AlarmSoundOrange,
            unselectedIndicator = Color(0xFF8C8C8C),
            backIcon = Res.drawable.alarm_setup_back_dark,
        )
    } else {
        AlarmSoundColors(
            background = Color.White,
            title = Color(0xFF111827),
            itemBackground = Color(0xFFF5F5F5),
            selectedItemBackground = Color(0xFFFFF7ED),
            itemText = Color(0xFF374151),
            selectedItemText = Color(0xFF111827),
            unselectedSoundIcon = Color(0xFF6B7280),
            unselectedIndicator = Color(0xFFD1D5DB),
            backIcon = Res.drawable.alarm_setup_back_light,
        )
    }

@Composable
internal fun AlarmSoundHeader(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = alarmSoundColors()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable(
                    role = Role.Button,
                    onClickLabel = "이전 화면으로 이동",
                    onClick = onBackClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(colors.backIcon),
                contentDescription = "뒤로",
                modifier = Modifier.fillMaxSize(),
            )
        }
        Text(
            text = "알람음",
            color = colors.title,
            maxLines = 1,
            style = MaterialTheme.typography.headlineSmall.copy(
                fontSize = 24.sp,
                lineHeight = 29.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
    }
}

@Composable
internal fun AlarmSoundListItem(
    sound: AlarmSoundSelection,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = alarmSoundColors()
    val shape = RoundedCornerShape(12.dp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(shape)
            .background(
                if (selected) colors.selectedItemBackground else colors.itemBackground,
            )
            .then(
                if (selected) {
                    Modifier.border(1.dp, AlarmSoundOrange, shape)
                } else {
                    Modifier
                },
            )
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(Res.drawable.alarm_setup_sound),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            colorFilter = ColorFilter.tint(
                if (selected) AlarmSoundOrange else colors.unselectedSoundIcon,
            ),
        )
        Text(
            text = sound.name,
            modifier = Modifier.weight(1f),
            color = if (selected) colors.selectedItemText else colors.itemText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 18.sp,
                lineHeight = 22.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            ),
        )
        AlarmSoundSelectionIndicator(
            selected = selected,
            color = if (selected) AlarmSoundOrange else colors.unselectedIndicator,
        )
    }
}

@Composable
private fun AlarmSoundSelectionIndicator(
    selected: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(24.dp)) {
        val strokeWidth = 2.dp.toPx()
        if (selected) {
            val checkPath = Path().apply {
                moveTo(size.width * 4.0008f / 24f, size.height * 11.9996f / 24f)
                lineTo(size.width * 9.0003f / 24f, size.height * 16.9992f / 24f)
                lineTo(size.width * 19.9992f / 24f, size.height * 6f / 24f)
            }
            drawPath(
                path = checkPath,
                color = color,
                style = Stroke(
                    width = strokeWidth,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
        } else {
            drawCircle(
                color = color,
                radius = size.minDimension * 10.0008f / 24f,
                style = Stroke(
                    width = strokeWidth,
                    cap = StrokeCap.Round,
                ),
            )
        }
    }
}

@Composable
internal fun AlarmSoundSaveButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(500.dp))
            .background(AlarmSoundOrange)
            .clickable(
                role = Role.Button,
                onClickLabel = "알람음 저장",
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "저장",
            color = Color.White,
            maxLines = 1,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 18.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
    }
}
