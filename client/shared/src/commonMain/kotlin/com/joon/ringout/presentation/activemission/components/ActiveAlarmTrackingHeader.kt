package com.joon.ringout.presentation.activemission.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun ActiveAlarmTrackingHeader(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = activeAlarmTrackingColors()
    val shape = RoundedCornerShape(15.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(62.dp)
            .clip(shape)
            .background(colors.headerBackground)
            .border(1.dp, colors.headerBorder, shape)
            .padding(start = 20.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clickable(
                    role = Role.Button,
                    onClickLabel = "알람 목록으로 돌아가기",
                    onClick = onBackClick,
                )
                .semantics { contentDescription = "뒤로가기" },
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(ActiveAlarmTrackingBackIconResource),
                contentDescription = null,
                modifier = Modifier.size(44.dp),
            )
        }
    }
}

@Preview(widthDp = 402, heightDp = 98)
@Composable
private fun ActiveAlarmTrackingHeaderPreview() {
    RingoutTheme(themeMode = ThemeMode.Dark) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(start = 10.dp, end = 11.dp, top = 18.dp),
        ) {
            ActiveAlarmTrackingHeader(onBackClick = {})
        }
    }
}
