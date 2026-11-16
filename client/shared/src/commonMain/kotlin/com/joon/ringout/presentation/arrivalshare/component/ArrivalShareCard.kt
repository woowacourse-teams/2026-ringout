package com.joon.ringout.presentation.arrivalshare.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ringoutColors
import com.joon.ringout.presentation.arrivalshare.ArrivalShareUiState
import org.jetbrains.compose.resources.painterResource
import ringout.shared.generated.resources.Res
import ringout.shared.generated.resources.arrival_editor_photo

@Composable
internal fun ArrivalShareCard(state: ArrivalShareUiState, modifier: Modifier = Modifier, photo: androidx.compose.ui.graphics.ImageBitmap? = null, onToggleTextColor: () -> Unit = {}) {
    val textColor = if (state.useBlackText) MaterialTheme.ringoutColors.shareCardBlackText else MaterialTheme.ringoutColors.primaryActionContent
    Box(modifier.aspectRatio(1f).testTag("arrival-share-card")
        .semantics { stateDescription = if (state.useBlackText) "검정색 글자" else "흰색 글자" }
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null,
            enabled = !state.busy, role = Role.Button,
            onClickLabel = if (state.useBlackText) "글자를 흰색으로 변경" else "글자를 검정색으로 변경", onClick = onToggleTextColor)) {
        if (photo != null) Image(photo, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Image(painterResource(Res.drawable.arrival_editor_photo), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Text("RINGOUT", Modifier.align(Alignment.TopStart).padding(22.dp), color = textColor,
            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
        Column(Modifier.align(Alignment.BottomStart).padding(18.dp)) {
            if (state.showDate) Text(state.date, color = textColor,
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (state.showTime) {
                Text(state.time ?: "기록 없음", color = textColor, style = MaterialTheme.typography.headlineLarge.copy(fontSize = if (state.time == null) 20.sp else 36.sp, lineHeight = 48.sp), fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
    }
}
@Preview @Composable private fun ArrivalShareCardPreview() { RingoutTheme {
    ArrivalShareCard(ArrivalShareUiState(false, date = "2026.11.03", time = "06:30", arrivalTime = "06:53"), Modifier.width(402.dp))
} }
