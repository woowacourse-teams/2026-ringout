package com.joon.ringout.presentation.roomhome.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.joon.ringout.LocalRingoutThemeMode
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import com.joon.ringout.presentation.roomhome.RoomHomeMemberUiModel
import com.joon.ringout.presentation.roomhome.roomHomeColors
import org.jetbrains.compose.resources.painterResource
import ringout.shared.generated.resources.Res
import ringout.shared.generated.resources.room_default_avartar_dark
import ringout.shared.generated.resources.room_default_avartar_light

@Composable
internal fun RoomHomeMembers(
    members: List<RoomHomeMemberUiModel>,
    modifier: Modifier = Modifier,
    isLoaded: Boolean = true,
) {
    val colors = roomHomeColors()
    val defaultAvatarResource = when (LocalRingoutThemeMode.current) {
        ThemeMode.Dark -> Res.drawable.room_default_avartar_dark
        ThemeMode.Light -> Res.drawable.room_default_avartar_light
    }
    val defaultAvatar = painterResource(defaultAvatarResource)

    Column(modifier = modifier.fillMaxWidth().padding(10.dp)) {
        Text(
            text = "회원",
            modifier = Modifier.semantics { heading() },
            color = colors.content,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 16.sp,
                lineHeight = 20.sp,
            ),
        )
        Spacer(Modifier.height(18.dp))
        if (!isLoaded) {
            Text(
                text = "회원 정보는 아직 조회되지 않았어요.",
                modifier = Modifier.padding(10.dp),
                color = colors.secondary,
                style = MaterialTheme.typography.bodyMedium,
            )
        } else if (members.isEmpty()) {
            Text(
                text = "아직 가입한 회원이 없어요.",
                modifier = Modifier.padding(10.dp),
                color = colors.secondary,
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                members.forEach { member ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (member.profileImageUrl.isNullOrBlank()) {
                            Image(
                                painter = defaultAvatar,
                                contentDescription = null,
                                modifier = Modifier.size(38.dp).clip(CircleShape),
                                contentScale = ContentScale.Crop,
                            )
                        } else {
                            AsyncImage(
                                model = member.profileImageUrl,
                                contentDescription = null,
                                modifier = Modifier.size(38.dp).clip(CircleShape),
                                contentScale = ContentScale.Crop,
                                placeholder = defaultAvatar,
                                error = defaultAvatar,
                            )
                        }
                        Text(
                            text = member.nickname,
                            modifier = Modifier.weight(1f),
                            color = colors.content,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 18.sp,
                                lineHeight = 22.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                    }
                }
            }
        }
    }
}

private val PreviewMembers = listOf(
    RoomHomeMemberUiModel(id = "preview-member-1", nickname = "볼링뜨실분다이겨드림"),
    RoomHomeMemberUiModel(id = "preview-member-2", nickname = "누누와윌럼프"),
    RoomHomeMemberUiModel(id = "preview-member-3", nickname = "북어서여남여동여"),
    RoomHomeMemberUiModel(id = "preview-member-4", nickname = "아아아티스트"),
)

@Preview(name = "회원 · 다크", widthDp = 402)
@Composable
private fun RoomHomeMembersPreview() {
    RingoutTheme(ThemeMode.Dark) {
        RoomHomeMembers(
            members = PreviewMembers,
            modifier = Modifier.background(roomHomeColors().background).padding(horizontal = 20.dp),
        )
    }
}

@Preview(name = "회원 · 라이트", widthDp = 402)
@Composable
private fun RoomHomeMembersLightPreview() {
    RingoutTheme(ThemeMode.Light) {
        RoomHomeMembers(
            members = PreviewMembers,
            modifier = Modifier.background(roomHomeColors().background).padding(horizontal = 20.dp),
        )
    }
}

@Preview(name = "긴 닉네임 · 작은 화면", widthDp = 320, fontScale = 1.5f)
@Composable
private fun RoomHomeMembersLongNicknamePreview() {
    RingoutTheme {
        RoomHomeMembers(
            members = PreviewMembers.map { it.copy(nickname = "아주긴닉네임으로모임에참여한회원입니다") },
            modifier = Modifier.background(roomHomeColors().background).padding(horizontal = 20.dp),
        )
    }
}

@Preview(name = "회원 없음", widthDp = 402)
@Composable
private fun RoomHomeMembersEmptyPreview() {
    RingoutTheme {
        RoomHomeMembers(
            members = emptyList(),
            modifier = Modifier.background(roomHomeColors().background).padding(horizontal = 20.dp),
        )
    }
}
