package com.joon.ringout.presentation.arrivalshare.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ringoutColors
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun ArrivalShareActions(
    enabled: Boolean,
    onSave: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentColor = MaterialTheme.ringoutColors.primaryActionContent
    val buttonColors = IconButtonDefaults.iconButtonColors(
        contentColor = contentColor,
        disabledContentColor = contentColor.copy(alpha = 0.38f),
    )
    Row(
        modifier = modifier.width(166.dp).height(58.dp)
            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50)),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onSave, enabled = enabled, colors = buttonColors) {
            Icon(painterResource(EditorDownloadIcon), "이미지 저장", Modifier.size(22.dp))
        }
        VerticalDivider(Modifier.height(22.dp), color = contentColor.copy(alpha = if (enabled) 1f else 0.38f))
        IconButton(onClick = onShare, enabled = enabled, colors = buttonColors) {
            Icon(painterResource(EditorShareIcon), "이미지 공유", Modifier.size(22.dp))
        }
    }
}

@Preview
@Composable
private fun ArrivalShareActionsPreview() {
    RingoutTheme { ArrivalShareActions(enabled = true, onSave = {}, onShare = {}) }
}
