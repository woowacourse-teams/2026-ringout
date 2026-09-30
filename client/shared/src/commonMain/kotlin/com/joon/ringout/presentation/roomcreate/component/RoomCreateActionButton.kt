package com.joon.ringout.presentation.roomcreate.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import com.joon.ringout.ringoutColors

@Composable
internal fun RoomCreateActionButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .semantics { contentDescription = label },
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.ringoutColors.primaryActionContent,
            disabledContainerColor = MaterialTheme.ringoutColors.elevatedSurface,
            disabledContentColor = MaterialTheme.ringoutColors.navigationInactiveContent,
        ),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        )
    }
}

@Preview(name = "다음으로 · 활성")
@Composable
private fun RoomCreateActionButtonEnabledPreview() {
    RingoutTheme(ThemeMode.Dark) {
        RoomCreateActionButton(label = "다음으로", enabled = true, onClick = {})
    }
}

@Preview(name = "모임 생성 · 비활성")
@Composable
private fun RoomCreateActionButtonDisabledPreview() {
    RingoutTheme(ThemeMode.Light) {
        RoomCreateActionButton(label = "모임 생성하기", enabled = false, onClick = {})
    }
}
