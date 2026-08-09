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
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(MaterialTheme.ringoutColors.elevatedSurface, RoundedCornerShape(18.dp))
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "테마",
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        )
        ThemeModeSwitch(themeMode, onThemeModeChange)
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
) {
    val darkSelected = themeMode == ThemeMode.Dark
    Box(
        modifier = Modifier
            .size(width = 76.dp, height = 34.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
            .semantics {
                contentDescription = "테마 전환"
                stateDescription = if (darkSelected) "다크 모드" else "라이트 모드"
            }
            .toggleable(
                value = darkSelected,
                role = Role.Switch,
                onValueChange = {
                    onThemeModeChange(if (it) ThemeMode.Dark else ThemeMode.Light)
                },
            ),
    ) {
        ThemeIcon(
            resource = MyPageThemeLightIconResource,
            selected = !darkSelected,
            modifier = Modifier.align(Alignment.CenterStart),
        )
        ThemeIcon(
            resource = MyPageThemeDarkIconResource,
            selected = darkSelected,
            modifier = Modifier.align(Alignment.CenterEnd),
        )
    }
}

@Composable
private fun ThemeIcon(
    resource: DrawableResource,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.then(
            if (selected) {
                Modifier
                    .size(34.dp)
                    .shadow(5.dp, CircleShape)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
            } else {
                Modifier.size(34.dp).offset(y = 0.dp)
            },
        ),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(resource),
            contentDescription = null,
            modifier = Modifier.size(if (selected) 25.dp else 17.dp),
            colorFilter = ColorFilter.tint(
                if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )
    }
}
