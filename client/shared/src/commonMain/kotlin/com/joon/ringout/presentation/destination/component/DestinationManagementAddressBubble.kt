package com.joon.ringout.presentation.destination.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joon.ringout.RingoutTheme

@Composable
internal fun DestinationManagementAddressBubble(
    title: String,
    address: String,
    modifier: Modifier = Modifier,
) {
    val colors = DestinationManagementPalette
    Column(
        modifier = modifier.padding(horizontal = 32.dp).widthIn(min = 299.dp, max = 340.dp)
            .shadow(8.dp, RoundedCornerShape(15.dp))
            .background(colors.surface, RoundedCornerShape(15.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(
            text = title,
            color = colors.title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold),
        )
        Text(
            text = address.removePrefix("대한민국 ").trimStart(),
            color = colors.addressText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
        )
    }
}

@Preview
@Composable
private fun DestinationManagementAddressBubblePreview() {
    RingoutTheme { DestinationManagementAddressBubble("헬스장", "서울 서초구 반포동 748-10") }
}
