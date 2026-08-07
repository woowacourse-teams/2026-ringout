package com.joon.ringout.presentation.alarmsetup.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import org.jetbrains.compose.resources.painterResource

@Composable
fun SetupBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = alarmSetupColors()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(41.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .requiredSize(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable(
                    role = Role.Button,
                    onClickLabel = "이전 화면으로 이동",
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(colors.backIcon),
                contentDescription = "뒤로",
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Preview(widthDp = 402)
@Composable
private fun SetupBackButtonPreview() {
    RingoutTheme(themeMode = ThemeMode.Dark) {
        AlarmSetupPreviewSurface {
            SetupBackButton(onClick = {})
        }
    }
}
