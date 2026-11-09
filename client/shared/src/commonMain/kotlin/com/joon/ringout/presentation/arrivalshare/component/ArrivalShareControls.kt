package com.joon.ringout.presentation.arrivalshare.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ringoutColors
import com.joon.ringout.presentation.arrivalshare.ArrivalShareUiState
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun ArrivalShareControls(state: ArrivalShareUiState, onTime: (Boolean) -> Unit, onDate: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf("카메라" to EditorCameraIcon, "갤러리" to EditorGalleryIcon).forEach { (label, icon) ->
                FilledTonalButton(onClick = {}, enabled = false, modifier = Modifier.weight(1f).heightIn(min = 48.dp), shape = RoundedCornerShape(12.dp)) {
                    Icon(painterResource(icon), null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text(label)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(Triple("기상 시간", EditorTimeIcon, state.showTime && state.time != null), Triple("날짜", EditorDateIcon, state.showDate)).forEachIndexed { index, (label, icon, checked) ->
                Button(onClick = { if (index == 0) onTime(!checked) else onDate(!checked) }, enabled = index == 1 || state.time != null,
                    modifier = Modifier.widthIn(min = 114.dp).heightIn(min = 87.dp).semantics { role = Role.Checkbox; toggleableState = if (checked) androidx.compose.ui.state.ToggleableState.On else androidx.compose.ui.state.ToggleableState.Off },
                    shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(
                        containerColor = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.ringoutColors.elevatedSurface,
                        contentColor = if (checked) MaterialTheme.ringoutColors.primaryActionContent else MaterialTheme.colorScheme.onSurface)) {
                    Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(painterResource(icon), null, Modifier.size(20.dp)); Text(label)
                    }
                }
            }
        }
        if (state.time == null) Text("알람을 끈 시각이 기록되지 않아 기상 시간을 표시할 수 없어요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
    }
}
@Preview @Composable private fun ArrivalShareControlsPreview() { RingoutTheme { ArrivalShareControls(ArrivalShareUiState(time = "06:30"), {}, {}) } }
