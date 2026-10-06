package com.joon.ringout.presentation.roomlist.roomdetail.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.presentation.roomlist.component.RoomImage
import com.joon.ringout.presentation.roomlist.model.RoomUiModel

@Composable
internal fun RoomDetailHero(
    room: RoomUiModel,
    onBackClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(RoomHeroAspectRatio),
    ) {
        RoomImage(
            room = room,
            modifier = Modifier.fillMaxSize(),
            shape = RectangleShape,
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(112.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.28f), Color.Transparent),
                    ),
                ),
        )
        RoomDetailBackButton(
            onClick = onBackClick,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(start = 4.dp, top = 2.dp),
        )
    }
}

@Preview(widthDp = 402, heightDp = 332)
@Composable
private fun RoomDetailHeroPreview() {
    RingoutTheme {
        RoomDetailHero(room = RoomDetailPreviewRoom, onBackClick = {})
    }
}

private const val RoomHeroAspectRatio = 402f / 332f
