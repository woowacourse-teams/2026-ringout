package com.joon.ringout.presentation.records.component

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.presentation.common.component.MainScreenHeader
import com.joon.ringout.presentation.records.recordsColors
import org.jetbrains.compose.resources.painterResource
import ringout.shared.generated.resources.Res
import ringout.shared.generated.resources.records_calendar

@Composable
internal fun RecordsHeader(onOpenCalendar: () -> Unit, modifier: Modifier = Modifier) {
    val colors = recordsColors()
    MainScreenHeader(
        title = "기록",
        description = "내 알람 활동을 확인해요",
        titleColor = colors.text,
        descriptionColor = colors.secondaryText,
        modifier = modifier,
    ) {
        IconButton(onClick = onOpenCalendar) {
            Icon(
                painterResource(Res.drawable.records_calendar),
                contentDescription = "월별 달력 열기",
                tint = colors.text,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Preview
@Composable
private fun RecordsHeaderPreview() {
    RingoutTheme { RecordsHeader({}) }
}
