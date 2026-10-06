package com.joon.ringout.presentation.roommembermanagement.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.joon.ringout.RingoutTheme
import com.joon.ringout.presentation.roommembermanagement.RoomMemberUiModel
import com.joon.ringout.presentation.roommembermanagement.roomMemberManagementColors
import org.jetbrains.compose.resources.painterResource
import ringout.shared.generated.resources.Res
import ringout.shared.generated.resources.room_member_default_avatar

@Composable
internal fun RoomMemberRow(
    member: RoomMemberUiModel,
    canRemove: Boolean,
    onRemoveClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 79.dp)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val defaultProfile = painterResource(Res.drawable.room_member_default_avatar)
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (member.profileImageUrl.isNullOrBlank()) {
                Image(
                    painter = defaultProfile,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                AsyncImage(
                    model = member.profileImageUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    placeholder = defaultProfile,
                    error = defaultProfile,
                )
            }
        }
        Spacer(Modifier.width(24.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = member.nickname,
                color = roomMemberManagementColors().nickname,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 18.sp,
                    lineHeight = 18.sp,
                    letterSpacing = 0.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            member.joinedDate?.let { joinedDate ->
                Text(
                    text = "가입: ${joinedDate.replace('-', '.')}",
                    color = roomMemberManagementColors().joinedDate,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.sp,
                    ),
                )
            }
        }
        if (canRemove && !member.isOwner) {
            Spacer(Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .clickable(
                        role = Role.Button,
                        onClickLabel = "${member.nickname}님 추방 확인",
                        onClick = onRemoveClick,
                    )
                    .semantics { contentDescription = "${member.nickname}님 추방" },
                contentAlignment = Alignment.CenterEnd,
            ) {
                Text(
                    text = "추방",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 16.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.sp,
                    ),
                )
            }
        }
    }
}

@Preview(widthDp = 360)
@Composable
private fun RoomMemberRowPreview() {
    RingoutTheme {
        RoomMemberRow(
            member = RoomMemberManagementPreviewMembers.first(),
            canRemove = true,
            onRemoveClick = {},
            modifier = Modifier.padding(horizontal = 20.dp),
        )
    }
}

@Preview(name = "방장은 추방할 수 없음", widthDp = 360)
@Composable
private fun RoomMemberRowOwnerPreview() {
    RingoutTheme {
        RoomMemberRow(
            member = RoomMemberManagementPreviewMembers.first().copy(isOwner = true),
            canRemove = true,
            onRemoveClick = {},
            modifier = Modifier.padding(horizontal = 20.dp),
        )
    }
}
