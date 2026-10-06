package com.joon.ringout.presentation.roomedit.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import com.joon.ringout.ringoutColors

@Composable
internal fun RoomEditSaveButton(
    enabled: Boolean,
    isSaving: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .semantics { contentDescription = "저장" },
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.ringoutColors.primaryActionContent,
            disabledContainerColor = MaterialTheme.ringoutColors.elevatedSurface,
            disabledContentColor = MaterialTheme.ringoutColors.navigationInactiveContent,
        ),
    ) {
        Text(
            text = if (isSaving) "저장 중" else "저장",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        )
    }
}

@Preview(name = "모임 수정 저장 · 비활성 · 라이트")
@Composable
private fun RoomEditSaveButtonDisabledPreview() {
    RingoutTheme(ThemeMode.Light) {
        RoomEditSaveButton(enabled = false, onClick = {})
    }
}

@Preview(name = "모임 수정 저장 · 활성 · 다크")
@Composable
private fun RoomEditSaveButtonEnabledPreview() {
    RingoutTheme(ThemeMode.Dark) {
        RoomEditSaveButton(enabled = true, onClick = {})
    }
}
