package com.joon.ringout.presentation.records

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.domain.missionhistory.AlarmRecordShareData
import com.joon.ringout.domain.missionhistory.AlarmUsageRecord
import com.joon.ringout.domain.missionhistory.AlarmUsageRecordGroup
import com.joon.ringout.domain.missionhistory.MissionDate
import com.joon.ringout.domain.missionhistory.MissionResult
import com.joon.ringout.domain.missionhistory.groupByAlarm
import com.joon.ringout.presentation.arrivalshare.component.EditorBackIcon
import com.joon.ringout.presentation.records.component.RecordDetailGroup
import com.joon.ringout.presentation.records.component.RecordsStateContent
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun RecordsDetailScreen(
    date: MissionDate,
    records: List<AlarmUsageRecordGroup>,
    isLoading: Boolean,
    errorMessage: String?,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onShare: (AlarmRecordShareData) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = recordsColors()
    Column(modifier.fillMaxSize().background(colors.background).statusBarsPadding().navigationBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 61.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(EditorBackIcon),
                contentDescription = "뒤로가기",
                modifier = Modifier.size(48.dp).clickable(
                    interactionSource = remember { MutableInteractionSource() }, indication = null,
                    role = Role.Button, onClick = onBack,
                ),
                tint = colors.text,
            )
            Text(
                text = date.iso8601.replace("-", ". "),
                modifier = Modifier.padding(start = 6.dp),
                color = colors.text,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        if (isLoading || errorMessage != null || records.isEmpty()) {
            RecordsStateContent(isLoading, errorMessage, onRetry)
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 30.dp, vertical = 20.dp),
            ) {
                items(records, key = { it.key }) { record ->
                    RecordDetailGroup(record, onShare)
                }
            }
        }
    }
}

@Preview(widthDp = 402, heightDp = 941)
@Composable
private fun RecordsDetailScreenPreview() {
    val date = MissionDate.of(2026, 8, 17)
    RingoutTheme {
        RecordsDetailScreen(
            date = date,
            records = listOf(
                AlarmUsageRecord("preview-first", date, alarmId = "alarm", ringingStartedAtEpochMillis = 1786944600000),
                AlarmUsageRecord("preview-success", date, result = MissionResult.SUCCESS, alarmId = "alarm",
                    ringingStartedAtEpochMillis = 1786944900000, missionCompletedAtEpochMillis = 1786945980000),
            ).groupByAlarm(),
            isLoading = false, errorMessage = null, onRetry = {}, onBack = {}, onShare = {},
        )
    }
}

@Preview
@Composable
private fun RecordsDetailLoadingScreenPreview() {
    RingoutTheme {
        RecordsDetailScreen(MissionDate.of(2026, 8, 17), emptyList(), true, null, {}, {}, {})
    }
}
