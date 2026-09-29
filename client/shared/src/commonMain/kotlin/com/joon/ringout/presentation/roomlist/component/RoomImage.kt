package com.joon.ringout.presentation.roomlist.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.joon.ringout.RingoutTheme
import com.joon.ringout.presentation.roomlist.model.RoomUiModel
import org.jetbrains.compose.resources.painterResource
import ringout.shared.generated.resources.Res
import ringout.shared.generated.resources.social_room_default

/** 대표 이미지가 있으면 불러오고, 없거나 표시할 수 없으면 기본 이미지를 사용한다. */
@Composable
internal fun RoomImage(
    room: RoomUiModel,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
) {
    val defaultPainter = painterResource(Res.drawable.social_room_default)
    val representativeImage = room.representativeImage

    if (representativeImage == null) {
        Image(
            painter = defaultPainter,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.clip(shape),
        )
    } else {
        AsyncImage(
            model = representativeImage,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            placeholder = defaultPainter,
            error = defaultPainter,
            modifier = modifier.clip(shape),
        )
    }
}

@Preview(name = "기본 모임 이미지", widthDp = 92, heightDp = 92, showBackground = true)
@Composable
private fun RoomImageDefaultPreview() {
    RingoutTheme {
        Box(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
        ) {
            RoomImage(
                room = RoomUiModel(
                    id = "preview-room",
                    representativeImage = null,
                    name = "미리보기 모임",
                    description = "모임 소개 문구 미리보기입니다.",
                    createdAt = "2026-09-15T09:00:00",
                    activityDays = listOf("월", "화", "수", "목", "금", "토", "일"),
                    activityTimeText = "오전 8:00",
                    participantCount = 1,
                    isJoined = false,
                ),
                modifier = Modifier.size(60.dp),
            )
        }
    }
}
