package com.joon.ringout.presentation.roomedit.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joon.ringout.LocalRingoutThemeMode
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import com.joon.ringout.presentation.roomlist.component.RoomImage
import com.joon.ringout.presentation.roomlist.model.RoomUiModel
import org.jetbrains.compose.resources.painterResource
import ringout.shared.generated.resources.Res
import ringout.shared.generated.resources.room_edit_image_edit_dark
import ringout.shared.generated.resources.room_edit_image_edit_light

@Composable
internal fun RoomEditImageSection(
    room: RoomUiModel,
    selectedImage: ImageBitmap?,
    onChangeClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(20.dp)
    val editIconResource = when (LocalRingoutThemeMode.current) {
        ThemeMode.Dark -> Res.drawable.room_edit_image_edit_dark
        ThemeMode.Light -> Res.drawable.room_edit_image_edit_light
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(RoomEditImageAspectRatio)
            .clip(shape),
    ) {
        if (selectedImage != null) {
            Image(
                bitmap = selectedImage,
                contentDescription = "선택한 모임 대표 이미지 미리보기",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            RoomImage(
                room = room,
                modifier = Modifier.fillMaxSize(),
                shape = shape,
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(ImageChangeTouchSize)
                .clip(RoundedCornerShape(topEnd = 20.dp, bottomStart = 8.dp))
                .semantics { contentDescription = "모임 대표 이미지 변경" }
                .clickable(
                    enabled = enabled,
                    role = Role.Button,
                    onClickLabel = "모임 대표 이미지 변경",
                    onClick = onChangeClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(editIconResource),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

private const val RoomEditImageAspectRatio = 342f / 252f
private val ImageChangeTouchSize = 48.dp

@Preview(name = "모임 대표 이미지 수정 · 라이트", widthDp = 354, heightDp = 260)
@Composable
private fun RoomEditImageSectionLightPreview() {
    RingoutTheme(ThemeMode.Light) {
        RoomEditImageSection(
            room = RoomEditImagePreviewRoom,
            selectedImage = null,
            onChangeClick = {},
        )
    }
}

@Preview(name = "모임 대표 이미지 수정 · 다크", widthDp = 354, heightDp = 260)
@Composable
private fun RoomEditImageSectionDarkPreview() {
    RingoutTheme(ThemeMode.Dark) {
        RoomEditImageSection(
            room = RoomEditImagePreviewRoom,
            selectedImage = null,
            onChangeClick = {},
        )
    }
}

private val RoomEditImagePreviewRoom = RoomUiModel(
    id = "preview-room",
    representativeImage = null,
    name = "아침러닝",
    description = "함께 달리며 건강한 습관을 만들어요.",
    createdAt = "2026-09-15T09:00:00",
    activityDays = listOf("월", "수", "금"),
    activityTimeText = "오전 8:00",
    participantCount = 3,
    isJoined = true,
)
