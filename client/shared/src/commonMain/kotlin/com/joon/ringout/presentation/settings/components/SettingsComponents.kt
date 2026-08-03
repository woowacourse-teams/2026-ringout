package com.joon.ringout.presentation.settings.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joon.ringout.LocalRingoutThemeMode
import com.joon.ringout.ThemeMode
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import ringout.shared.generated.resources.Res
import ringout.shared.generated.resources.alarm_setup_back_dark
import ringout.shared.generated.resources.alarm_setup_back_light

private val RingoutOrange = Color(0xFFFF6D2E)

internal data class SettingsColors(
    val background: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val cardBackground: Color,
    val toggleTrack: Color,
    val inactiveToggleIcon: Color,
    val backIcon: DrawableResource,
)

@Composable
internal fun settingsColors(): SettingsColors =
    if (LocalRingoutThemeMode.current == ThemeMode.Dark) {
        SettingsColors(
            background = Color.Black,
            primaryText = Color.White,
            secondaryText = Color(0xFFA1A1A1),
            cardBackground = Color(0xFF1A1A1A),
            toggleTrack = Color(0xFF8C8C8C),
            inactiveToggleIcon = Color(0xFF6B7280),
            backIcon = Res.drawable.alarm_setup_back_dark,
        )
    } else {
        SettingsColors(
            background = Color.White,
            primaryText = Color(0xFF111827),
            secondaryText = Color(0xFF6B7280),
            cardBackground = Color(0xFFF5F5F5),
            toggleTrack = Color(0xFFE5E7EB),
            inactiveToggleIcon = Color(0xFF6B7280),
            backIcon = Res.drawable.alarm_setup_back_light,
        )
    }

@Composable
internal fun SettingsHeader(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = settingsColors()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(65.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .offset(x = (-7).dp)
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
            text = "설정",
            modifier = Modifier.padding(start = 37.dp),
            color = colors.primaryText,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 20.sp,
                lineHeight = 19.2.sp,
                fontWeight = FontWeight.Normal,
            ),
        )
    }
}

@Composable
internal fun ThemeSettingCard(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = settingsColors()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(51.dp)
            .background(
                color = colors.cardBackground,
                shape = RoundedCornerShape(16.dp),
            )
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "테마",
            color = colors.primaryText,
            maxLines = 1,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 16.sp,
                lineHeight = 19.2.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
        ThemeModeSwitch(
            themeMode = themeMode,
            onThemeModeChange = onThemeModeChange,
            trackColor = colors.toggleTrack,
            inactiveIconColor = colors.inactiveToggleIcon,
        )
    }
}

@Composable
private fun ThemeModeSwitch(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    trackColor: Color,
    inactiveIconColor: Color,
    modifier: Modifier = Modifier,
) {
    val darkModeSelected = themeMode == ThemeMode.Dark

    Box(
        modifier = modifier
            .size(width = 73.dp, height = 32.dp)
            .background(trackColor, CircleShape)
            .semantics {
                contentDescription = "테마 전환"
                stateDescription = if (darkModeSelected) "다크 모드" else "라이트 모드"
            }
            .toggleable(
                value = darkModeSelected,
                interactionSource = null,
                indication = null,
                role = Role.Switch,
                onValueChange = { selectDarkMode ->
                    onThemeModeChange(
                        if (selectDarkMode) ThemeMode.Dark else ThemeMode.Light,
                    )
                },
            ),
    ) {
        ThemeModeIcon(
            icon = SettingsThemeLightIconResource,
            selected = !darkModeSelected,
            inactiveColor = inactiveIconColor,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .then(
                    if (darkModeSelected) {
                        Modifier.offset(x = 7.5.dp)
                    } else {
                        Modifier
                    },
                ),
        )
        ThemeModeIcon(
            icon = SettingsThemeDarkIconResource,
            selected = darkModeSelected,
            inactiveColor = inactiveIconColor,
            modifier = Modifier.align(Alignment.CenterEnd),
        )
    }
}

@Composable
private fun ThemeModeIcon(
    icon: DrawableResource,
    selected: Boolean,
    inactiveColor: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .then(
                if (selected) {
                    Modifier
                        .size(32.dp)
                        .shadow(
                            elevation = 4.dp,
                            shape = CircleShape,
                            clip = false,
                        )
                        .background(RingoutOrange, CircleShape)
                } else {
                    Modifier.size(32.dp)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(if (selected) 24.dp else 16.dp),
            colorFilter = ColorFilter.tint(
                if (selected) Color.White else inactiveColor,
            ),
        )
    }
}

@Composable
internal fun AppInfoCard(
    appVersion: String,
    modifier: Modifier = Modifier,
) {
    val colors = settingsColors()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(77.dp)
            .background(
                color = colors.cardBackground,
                shape = RoundedCornerShape(16.dp),
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "앱 정보",
            color = colors.primaryText,
            maxLines = 1,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 16.sp,
                lineHeight = 19.2.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
        Text(
            text = "버전 $appVersion",
            color = colors.secondaryText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 14.sp,
                lineHeight = 16.8.sp,
                fontWeight = FontWeight.Normal,
            ),
        )
    }
}
