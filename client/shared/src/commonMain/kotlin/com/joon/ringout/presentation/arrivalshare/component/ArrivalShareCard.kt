package com.joon.ringout.presentation.arrivalshare.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ringoutColors
import com.joon.ringout.presentation.arrivalshare.ArrivalShareUiState
import org.jetbrains.compose.resources.painterResource
import ringout.shared.generated.resources.*

@Composable
internal fun ArrivalShareCard(state: ArrivalShareUiState, modifier: Modifier = Modifier, photo: androidx.compose.ui.graphics.ImageBitmap? = null) {
    Box(modifier.aspectRatio(1f)) {
        if (photo != null) Image(photo, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Image(painterResource(Res.drawable.arrival_editor_photo), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Text("RINGOUT", Modifier.align(Alignment.TopStart).padding(22.dp), color = MaterialTheme.ringoutColors.primaryActionContent,
            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
        Column(Modifier.align(Alignment.BottomStart).padding(18.dp)) {
            if (state.showDate) Text(state.date, color = MaterialTheme.ringoutColors.primaryActionContent,
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (state.showTime && state.time != null) Text(state.time, color = MaterialTheme.ringoutColors.primaryActionContent,
                style = MaterialTheme.typography.headlineLarge.copy(fontSize = 36.sp, lineHeight = 48.sp), fontWeight = FontWeight.Bold)
        }
    }
}
@Preview @Composable private fun ArrivalShareCardPreview() { RingoutTheme {
    ArrivalShareCard(ArrivalShareUiState(false, date = "2026.11.03", time = "06:30"), Modifier.width(402.dp))
} }
