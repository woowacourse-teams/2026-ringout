package com.joon.ringout.presentation.records.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.joon.ringout.RingoutTheme
import com.joon.ringout.domain.missionhistory.MissionDate
import com.joon.ringout.domain.missionhistory.MissionResult
import com.joon.ringout.domain.missionhistory.MissionYearMonth
import com.joon.ringout.domain.missionhistory.calendarDates
import com.joon.ringout.presentation.common.component.DialogWithoutRipple
import com.joon.ringout.presentation.records.RecordsDayUiState
import com.joon.ringout.presentation.records.recordsColors
import org.jetbrains.compose.resources.painterResource
import ringout.shared.generated.resources.Res
import ringout.shared.generated.resources.records_chevron

@Composable
internal fun RecordsMonthDialog(
    month: MissionYearMonth,
    days: List<RecordsDayUiState?>,
    isLoading: Boolean,
    errorMessage: String?,
    onDateSelected: (MissionDate) -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = recordsColors()
    val headerHeight = with(LocalDensity.current) { (24.sp.toDp() * 2).coerceAtLeast(48.dp) }
    DialogWithoutRipple {
        Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(
                modifier = modifier.padding(horizontal = 24.dp).widthIn(max = 348.dp).fillMaxWidth(),
                shape = RoundedCornerShape(16.dp), color = colors.calendarSurface,
                border = BorderStroke(1.dp, colors.calendarBorder),
            ) {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(modifier = Modifier.height(headerHeight), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onPreviousMonth) {
                            Icon(painterResource(Res.drawable.records_chevron), "이전 달", Modifier.size(20.dp).rotate(180f), tint = colors.text)
                        }
                        Text(
                            "${month.year}년 ${month.month}월", modifier = Modifier.weight(1f), fontSize = 20.sp,
                            fontWeight = FontWeight.Bold, color = colors.text, textAlign = TextAlign.Center,
                            lineHeight = 24.sp, maxLines = 2,
                        )
                        IconButton(onClick = onNextMonth) {
                            Icon(painterResource(Res.drawable.records_chevron), "다음 달", Modifier.size(20.dp), tint = colors.text)
                        }
                    }
                    Row {
                        listOf("일", "월", "화", "수", "목", "금", "토").forEach { label ->
                            Text(label, Modifier.weight(1f), color = colors.secondaryText, fontSize = 12.sp, textAlign = TextAlign.Center)
                        }
                    }
                    Box {
                        RecordsMonthGrid(days = days, onDateSelected = onDateSelected)
                        Box(
                            modifier = Modifier.matchParentSize().padding(16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            RecordsMonthStatus(isLoading = isLoading, errorMessage = errorMessage, onRetry = onRetry)
                        }
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun RecordsMonthDialogPreview() {
    RingoutTheme {
        RecordsMonthDialog(
            MissionYearMonth(2026, 9),
            previewDays(MissionYearMonth(2026, 9)),
            false, null, {}, {}, {}, {}, {},
        )
    }
}

@Preview
@Composable
private fun RecordsMonthDialogLoadingPreview() {
    RingoutTheme {
        RecordsMonthDialog(
            MissionYearMonth(2026, 2), previewDays(MissionYearMonth(2026, 2)),
            true, null, {}, {}, {}, {}, {},
        )
    }
}

@Preview
@Composable
private fun RecordsMonthDialogErrorPreview() {
    RingoutTheme {
        RecordsMonthDialog(
            MissionYearMonth(2026, 5), previewDays(MissionYearMonth(2026, 5)),
            false, "기록을 불러오지 못했어요", {}, {}, {}, {}, {},
        )
    }
}

@Preview(widthDp = 320, heightDp = 480, fontScale = 2f)
@Composable
private fun RecordsMonthDialogLargeFontPreview() {
    RingoutTheme {
        RecordsMonthDialog(
            MissionYearMonth(2026, 5), previewDays(MissionYearMonth(2026, 5)),
            false, "기록을 불러오지 못했어요", {}, {}, {}, {}, {},
        )
    }
}

private fun previewDays(month: MissionYearMonth): List<RecordsDayUiState?> = month.calendarDates().map { date ->
    date?.let {
        RecordsDayUiState(
            date = it,
            isSelected = it.day == 15,
            isToday = it.day == 15,
            isFuture = it.day > 15,
            result = if (it.day < 15) MissionResult.SUCCESS else null,
            recordCount = if (it.day < 15) 1 else 0,
        )
    }
}
