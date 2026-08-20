package com.joon.ringout.presentation.mypage.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode

@Composable
internal fun MyPageAccountLoadError(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = myPageColors()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 62.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "회원 정보를 불러오지 못했어요.",
            modifier = Modifier.weight(1f),
            color = colors.secondaryText,
            style = MaterialTheme.typography.bodyMedium,
        )
        TextButton(onClick = onRetry) {
            Text("다시 시도")
        }
    }
}

@Preview(name = "My Page Account Load Error", widthDp = 402)
@Composable
private fun MyPageAccountLoadErrorPreview() {
    RingoutTheme(ThemeMode.Light) {
        MyPageAccountLoadError(onRetry = {})
    }
}
