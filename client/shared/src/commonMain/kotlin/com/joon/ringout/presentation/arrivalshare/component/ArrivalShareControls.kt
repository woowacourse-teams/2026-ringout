package com.joon.ringout.presentation.arrivalshare.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.presentation.arrivalshare.ArrivalShareUiState
import com.joon.ringout.ringoutColors
import org.jetbrains.compose.resources.painterResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ArrivalShareControls(
    state: ArrivalShareUiState,
    onTime: (Boolean) -> Unit,
    onDate: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onCamera: () -> Unit = {},
    onGallery: () -> Unit = {},
) {
    CompositionLocalProvider(LocalRippleConfiguration provides null) {
        Column(
            modifier = modifier.padding(horizontal = 20.dp, vertical = 15.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("카메라" to EditorCameraIcon, "갤러리" to EditorGalleryIcon).forEach { (label, icon) ->
                    FilledTonalButton(
                        onClick = if (label == "카메라") onCamera else onGallery,
                        enabled = !state.busy,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.ringoutColors.elevatedSurface,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    ) {
                        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }
                }
            }
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val tileWidth = minOf(114.dp, (maxWidth - 10.dp) / 2)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(
                        Triple("기상 시간", EditorTimeIcon, state.showTime),
                        Triple("날짜", EditorDateIcon, state.showDate),
                    ).forEachIndexed { index, (label, icon, checked) ->
                        val enabled = !state.busy && (index == 1 || state.time != null || state.arrivalTime != null)
                        Button(
                            onClick = { if (index == 0) onTime(!checked) else onDate(!checked) },
                            enabled = enabled,
                            modifier = Modifier.width(tileWidth).heightIn(min = 87.dp).semantics {
                                role = Role.Checkbox
                                toggleableState = if (checked) ToggleableState.On else ToggleableState.Off
                            },
                            shape = RoundedCornerShape(16.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.ringoutColors.elevatedSurface,
                                contentColor = if (checked) MaterialTheme.ringoutColors.primaryActionContent else MaterialTheme.colorScheme.onSurface,
                            ),
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.height(8.dp))
                                Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 2)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ArrivalShareControlsPreview() {
    RingoutTheme {
        ArrivalShareControls(ArrivalShareUiState(time = "06:30", arrivalTime = "06:53"), {}, {})
    }
}
