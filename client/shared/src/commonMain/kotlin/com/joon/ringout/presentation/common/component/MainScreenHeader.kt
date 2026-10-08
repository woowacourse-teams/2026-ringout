package com.joon.ringout.presentation.common.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import com.joon.ringout.ringoutColors

@Composable
internal fun MainScreenHeader(
    title: String,
    description: String,
    titleColor: Color,
    descriptionColor: Color,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = 65.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontSize = 28.sp,
                    lineHeight = 34.sp,
                    fontWeight = FontWeight.Black,
                ),
            )
            // 빈 화면에서도 설명 한 줄의 높이를 유지해 제목 기준선이 움직이지 않게 한다.
            Text(
                text = description,
                color = descriptionColor,
                minLines = 1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 18.sp,
                    lineHeight = 21.6.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
        }
        action?.invoke()
    }
}

@Preview(name = "다크 · 헤더", widthDp = 360)
@Preview(name = "다크 · 큰 글꼴 헤더", widthDp = 360, fontScale = 2f)
@Composable
private fun MainScreenHeaderPreview() {
    MainScreenHeaderPreviewContent(ThemeMode.Dark)
}

@Preview(name = "라이트 · 헤더", widthDp = 360)
@Preview(name = "라이트 · 큰 글꼴 헤더", widthDp = 360, fontScale = 2f)
@Composable
private fun MainScreenHeaderLightPreview() {
    MainScreenHeaderPreviewContent(ThemeMode.Light)
}

@Composable
private fun MainScreenHeaderPreviewContent(themeMode: ThemeMode) {
    RingoutTheme(themeMode) {
        Surface(color = MaterialTheme.ringoutColors.mainScreenBackground) {
            Column(Modifier.padding(20.dp)) {
                listOf(
                    "알람" to "7시간 20분 후 알람이 울려요.",
                    "알람" to "",
                    "기록" to "내 알람 활동을 확인해요",
                    "모임" to "모임에 참여하거나 직접 만들어보세요",
                ).forEach { (title, description) ->
                    MainScreenHeader(
                        title = title,
                        description = description,
                        titleColor = MaterialTheme.colorScheme.onBackground,
                        descriptionColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
