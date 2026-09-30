package com.joon.ringout.presentation.roomhome.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.presentation.roomhome.roomHomeColors
import com.joon.ringout.presentation.roomlist.component.RoomImage
import com.joon.ringout.presentation.roomlist.model.RoomUiModel
import com.joon.ringout.presentation.roomlist.roomdetail.component.RoomDetailPreviewRoom
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun RoomHomeHero(
    room: RoomUiModel,
    onBackClick: () -> Unit,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = roomHomeColors()

    Box(modifier = modifier.fillMaxWidth().aspectRatio(RoomHomeHeroAspectRatio)) {
        RoomImage(room = room, modifier = Modifier.fillMaxSize(), shape = RectangleShape)
        Box(Modifier.fillMaxSize().background(colors.heroOverlay))
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .height(61.dp)
                .padding(start = 3.dp, end = 12.5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(
                modifier = Modifier.size(48.dp).clickable(role = Role.Button, onClick = onBackClick),
                contentAlignment = Alignment.CenterStart,
            ) {
                Icon(
                    painter = painterResource(RoomHomeBackIconResource),
                    contentDescription = "뒤로",
                    modifier = Modifier.size(44.dp),
                    tint = colors.heroContent,
                )
            }
            Box(
                modifier = Modifier.size(48.dp).clickable(role = Role.Button, onClick = onMenuClick),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(RoomHomeMoreIconResource),
                    contentDescription = "모임 메뉴",
                    modifier = Modifier.size(33.dp),
                    tint = colors.heroContent,
                )
            }
        }
    }
}

@Preview(name = "모임 홈 대표 이미지", widthDp = 402, heightDp = 188)
@Composable
private fun RoomHomeHeroPreview() {
    RingoutTheme {
        RoomHomeHero(room = RoomDetailPreviewRoom, onBackClick = {}, onMenuClick = {})
    }
}

private const val RoomHomeHeroAspectRatio = 402f / 188f
