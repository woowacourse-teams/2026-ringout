package com.joon.ringout.presentation.destination.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.joon.ringout.RingoutTheme

@Composable
internal fun DestinationDeleteDialog(
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    isDeleting: Boolean = false,
) {
    Dialog(
        onDismissRequest = { if (!isDeleting) onDismissRequest() },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = !isDeleting, dismissOnClickOutside = !isDeleting),
    ) {
        DestinationDeleteDialogContent(onDismissRequest, onConfirm, modifier.padding(horizontal = 24.dp), isDeleting)
    }
}

@Composable
internal fun DestinationDeleteDialogContent(
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    isDeleting: Boolean = false,
) {
    val colors = DestinationManagementPalette
    Column(
        modifier.widthIn(max = 376.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.surface).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("목적지 삭제", color = colors.confirmTitle,
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold))
            Text("저장된 목적지 목록에서 삭제할까요?\n이미 알람에 설정한 목적지는 유지됩니다.", color = colors.confirmBody,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 19.2.sp, fontWeight = FontWeight.Medium))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf("취소" to onDismissRequest, (if (isDeleting) "삭제 중…" else "삭제") to onConfirm).forEachIndexed { index, (label, action) ->
                Box(Modifier.weight(if (index == 0) 99f else 162f).height(40.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (index == 0) colors.cancelAction else colors.primaryAction)
                    .clickable(enabled = !isDeleting, role = Role.Button, onClick = action), contentAlignment = Alignment.Center) {
                    Text(label, color = colors.surface,
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 16.sp, fontWeight = FontWeight.Medium))
                }
            }
        }
    }
}

@Preview
@Composable
private fun DestinationDeleteDialogPreview() {
    RingoutTheme { DestinationDeleteDialogContent({}, {}) }
}
