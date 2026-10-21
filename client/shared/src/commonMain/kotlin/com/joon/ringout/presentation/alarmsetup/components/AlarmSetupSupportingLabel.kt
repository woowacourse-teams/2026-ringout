package com.joon.ringout.presentation.alarmsetup.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import com.joon.ringout.RingoutTheme

@Composable
internal fun AlarmSetupSupportingLabel(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        color = color,
        modifier = modifier,
        style = MaterialTheme.typography.bodySmall.copy(
            fontSize = 12.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.Normal,
        ),
    )
}

@Preview
@Preview(name = "큰 글자", widthDp = 280, fontScale = 2f)
@Composable
private fun AlarmSetupSupportingLabelPreview() {
    RingoutTheme {
        AlarmSetupSupportingLabel("알람을 끄고 이동할 곳을 선택해주세요", alarmSetupColors().primaryText)
    }
}
