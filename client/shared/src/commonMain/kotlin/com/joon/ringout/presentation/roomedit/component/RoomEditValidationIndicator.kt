package com.joon.ringout.presentation.roomedit.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode

@Composable
internal fun RoomEditValidationIndicator(
    label: String,
    isSatisfied: Boolean,
    hasInput: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = roomEditColors()
    val status = when {
        isSatisfied -> "충족"
        hasInput -> "확인 필요"
        else -> "입력 전"
    }
    val color = if (isSatisfied) colors.success else colors.statusError

    Row(
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = "$label, $status"
        },
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (isSatisfied) "✓" else "·",
            color = color,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
        )
        Text(
            text = label,
            color = color,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
        )
    }
}

@Preview(name = "수정 조건 · 미입력")
@Composable
private fun RoomEditValidationIndicatorEmptyPreview() {
    RingoutTheme(ThemeMode.Dark) {
        RoomEditValidationIndicator(label = "2~20자 이내", isSatisfied = false, hasInput = false)
    }
}

@Preview(name = "수정 조건 · 충족")
@Composable
private fun RoomEditValidationIndicatorValidPreview() {
    RingoutTheme(ThemeMode.Light) {
        RoomEditValidationIndicator(label = "최대 300글자", isSatisfied = true, hasInput = true)
    }
}
