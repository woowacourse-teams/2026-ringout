package com.joon.ringout.presentation.mypage.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import org.jetbrains.compose.resources.painterResource
import ringout.shared.generated.resources.Res
import ringout.shared.generated.resources.mypage_mission_stamp

@Composable
internal fun MissionResultIndicator(
    day: Int,
    modifier: Modifier = Modifier,
) {
    Image(
        painter = painterResource(Res.drawable.mypage_mission_stamp),
        contentDescription = "${day}일 미션 성공",
        modifier = modifier.size(21.dp),
    )
}

@Preview
@Composable
private fun MissionResultIndicatorPreview() {
    RingoutTheme(ThemeMode.Light) {
        MissionResultIndicator(
            day = 3,
            modifier = Modifier.padding(20.dp),
        )
    }
}
