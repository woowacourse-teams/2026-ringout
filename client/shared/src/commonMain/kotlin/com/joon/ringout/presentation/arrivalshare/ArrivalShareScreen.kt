package com.joon.ringout.presentation.arrivalshare

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ringoutColors
import com.joon.ringout.presentation.arrivalshare.component.*
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun ArrivalShareScreen(state: ArrivalShareUiState, onBack: () -> Unit, onTime: (Boolean) -> Unit, onDate: (Boolean) -> Unit, onRetry: () -> Unit, modifier: Modifier = Modifier, photo: androidx.compose.ui.graphics.ImageBitmap? = null, cardModifier: Modifier = Modifier, onCamera: () -> Unit = {}, onGallery: () -> Unit = {}, onSave: () -> Unit = {}, onShare: () -> Unit = {}, mediaReady: Boolean = true, onToggleTextColor: () -> Unit = {}) {
    Column(modifier.fillMaxSize().background(MaterialTheme.ringoutColors.mainScreenBackground).safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(painterResource(EditorBackIcon), "뒤로가기", Modifier.size(44.dp), tint = MaterialTheme.colorScheme.onSurface) }
            Text("공유하기", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            when {
                state.isLoading -> CircularProgressIndicator(Modifier.padding(32.dp))
                state.error != null -> { Text(state.error, Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurface); TextButton(onClick = onRetry) { Text("다시 시도") } }
                else -> {
                    ArrivalShareCard(state, Modifier.fillMaxWidth().then(cardModifier), photo, onToggleTextColor)
                    ArrivalShareControls(state, onTime, onDate, Modifier.fillMaxWidth(), onCamera, onGallery)
                }
            }
        }
        state.message?.let {
            Text(it, Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
        if (state.busy) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally).padding(12.dp))
        Row(Modifier.align(Alignment.CenterHorizontally).padding(20.dp).width(166.dp).height(58.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50)), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onSave, enabled = mediaReady && !state.busy && !state.isLoading && state.error == null) { Icon(painterResource(EditorDownloadIcon), "이미지 저장", Modifier.size(22.dp), tint = MaterialTheme.ringoutColors.primaryActionContent) }
            VerticalDivider(Modifier.height(22.dp), color = MaterialTheme.ringoutColors.primaryActionContent)
            IconButton(onClick = onShare, enabled = mediaReady && !state.busy && !state.isLoading && state.error == null) { Icon(painterResource(EditorShareIcon), "이미지 공유", Modifier.size(22.dp), tint = MaterialTheme.ringoutColors.primaryActionContent) }
        }
    }
}
@Preview @Composable private fun ArrivalShareScreenPreview() { RingoutTheme { ArrivalShareScreen(ArrivalShareUiState(false, date = "2026.11.03", time = "06:30"), {}, {}, {}, {}) } }
@Preview @Composable private fun ArrivalShareLoadingScreenPreview() { RingoutTheme { ArrivalShareScreen(ArrivalShareUiState(), {}, {}, {}, {}) } }
@Preview @Composable private fun ArrivalShareErrorScreenPreview() { RingoutTheme { ArrivalShareScreen(ArrivalShareUiState(false, error = "도착 기록을 불러오지 못했어요."), {}, {}, {}, {}) } }
