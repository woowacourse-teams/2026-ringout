package com.joon.ringout.presentation.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joon.ringout.RingoutTheme

@Composable
internal fun AlarmDestinationInfo(
    destination: String,
    intervalMinutes: Int,
    modifier: Modifier = Modifier,
) {
    val emphasis = SpanStyle(
        color = MaterialTheme.colorScheme.primary,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
    )
    val descriptionStyle = MaterialTheme.typography.bodySmall.copy(
        fontSize = 12.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.Medium,
    )

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = buildAnnotatedString {
                withStyle(emphasis) { append(destination) }
                append("으로 이동")
            },
            color = homeAlarmColors().cardSecondaryText,
            style = descriptionStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = buildAnnotatedString {
                withStyle(emphasis) { append("${intervalMinutes}분") }
                append(" 간격으로 알람이 울려요")
            },
            color = homeAlarmColors().cardSecondaryText,
            style = descriptionStyle.copy(lineHeight = 18.sp),
        )
    }
}

@Preview
@Composable
private fun AlarmDestinationInfoPreview() {
    RingoutTheme {
        AlarmDestinationInfo(destination = "헬스장", intervalMinutes = 12)
    }
}
