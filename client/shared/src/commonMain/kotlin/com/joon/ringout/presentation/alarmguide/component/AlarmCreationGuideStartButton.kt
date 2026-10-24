package com.joon.ringout.presentation.alarmguide.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import com.joon.ringout.ringoutColors

/** The overlay owns input and accessibility; this surface covers the real save button. */
@Composable
internal fun AlarmCreationGuideStartButton(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(500.dp))
            .background(MaterialTheme.colorScheme.primary)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "시작하기",
            color = MaterialTheme.ringoutColors.primaryActionContent,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 18.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
    }
}

@Preview(name = "다크", widthDp = 252, heightDp = 44)
@Preview(name = "다크 · 큰 글자", widthDp = 252, heightDp = 110, fontScale = 2f)
@Composable
private fun AlarmCreationGuideStartButtonPreview() {
    RingoutTheme(ThemeMode.Dark) {
        AlarmCreationGuideStartButton()
    }
}

@Preview(name = "라이트", widthDp = 252, heightDp = 44)
@Composable
private fun AlarmCreationGuideStartButtonLightPreview() {
    RingoutTheme(ThemeMode.Light) {
        AlarmCreationGuideStartButton()
    }
}
