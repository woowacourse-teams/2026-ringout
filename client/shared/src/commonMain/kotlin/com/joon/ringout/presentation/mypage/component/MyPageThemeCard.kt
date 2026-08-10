package com.joon.ringout.presentation.mypage.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joon.ringout.ThemeMode
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ringoutColors
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

@Composable
fun MyPageThemeCard(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = myPageColors()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(51.dp)
            .background(colors.sectionSurface, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "테마",
            modifier = Modifier.weight(1f),
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
            inactiveIconColor = colors.toggleInactiveContent,
        )
    }
}

@Preview(widthDp = 402)
@Composable
private fun MyPageThemeCardPreview() {
    RingoutTheme(ThemeMode.Dark) {
        MyPageThemeCard(
            themeMode = ThemeMode.Dark,
            onThemeModeChange = {},
            modifier = Modifier.padding(20.dp),
        )
    }
}

@Composable
private fun ThemeModeSwitch(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    trackColor: androidx.compose.ui.graphics.Color,
    inactiveIconColor: androidx.compose.ui.graphics.Color,
) {
    val darkSelected = themeMode == ThemeMode.Dark
    Box(
        modifier = Modifier
            .size(width = 73.dp, height = 48.dp)
            .semantics {
                contentDescription = "테마 전환"
                stateDescription = if (darkSelected) "다크 모드" else "라이트 모드"
            }
            .toggleable(
                value = darkSelected,
                interactionSource = null,
                indication = null,
                role = Role.Switch,
                onValueChange = {
                    onThemeModeChange(if (it) ThemeMode.Dark else ThemeMode.Light)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = 73.dp, height = 32.dp)
                .background(trackColor, CircleShape),
        ) {
            ThemeIcon(
                resource = MyPageThemeLightIconResource,
                selected = !darkSelected,
                inactiveColor = inactiveIconColor,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .then(if (darkSelected) Modifier.offset(x = 7.5.dp) else Modifier),
            )
            ThemeIcon(
                resource = MyPageThemeDarkIconResource,
                selected = darkSelected,
                inactiveColor = inactiveIconColor,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
    }
}

@Composable
private fun ThemeIcon(
    resource: DrawableResource,
    selected: Boolean,
    inactiveColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.then(
            if (selected) {
                Modifier
                    .size(32.dp)
                    .shadow(4.dp, CircleShape)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
            } else {
                Modifier.size(32.dp)
            },
        ),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(resource),
            contentDescription = null,
            modifier = Modifier.size(if (selected) 24.dp else 16.dp),
            colorFilter = ColorFilter.tint(
                if (selected) MaterialTheme.ringoutColors.primaryActionContent else inactiveColor,
            ),
        )
    }
}
