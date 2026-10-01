package com.joon.ringout.presentation.roomhome.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import com.joon.ringout.domain.room.RoomMembershipRole
import com.joon.ringout.presentation.roomhome.RoomHomeMenuItem
import com.joon.ringout.presentation.roomhome.roomHomeColors
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun RoomHomeDropdownMenu(
    expanded: Boolean,
    membershipRole: RoomMembershipRole?,
    onDismissRequest: () -> Unit,
    onItemSelected: (RoomHomeMenuItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = roomHomeColors()
    val items = when (membershipRole) {
        RoomMembershipRole.OWNER -> listOf(
            RoomHomeMenuItem.Edit to "모임 수정",
            RoomHomeMenuItem.Delete to "모임 삭제",
            RoomHomeMenuItem.ManageMembers to "회원 관리",
        )
        RoomMembershipRole.MEMBER -> listOf(RoomHomeMenuItem.Leave to "모임 탈퇴하기")
        null -> emptyList()
    }
    if (items.isEmpty()) return

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier.widthIn(min = 136.dp),
        shape = RoundedCornerShape(10.dp),
        containerColor = colors.dropdownSurface,
        tonalElevation = 0.dp,
        shadowElevation = 8.dp,
    ) {
        items.forEachIndexed { index, (item, label) ->
            if (index > 0 && membershipRole == RoomMembershipRole.OWNER) {
                HorizontalDivider(color = colors.dropdownDivider, thickness = 1.dp)
            }
            DropdownMenuItem(
                text = {
                    Text(
                        text = label,
                        color = if (item == RoomHomeMenuItem.Leave) {
                            colors.actionDialogPrimary
                        } else {
                            colors.dropdownContent
                        },
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 15.sp,
                            lineHeight = 20.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                    )
                },
                onClick = { onItemSelected(item) },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .semantics { contentDescription = label },
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

@Preview(name = "방장 메뉴 · 다크", widthDp = 360)
@Composable
private fun RoomHomeOwnerMenuPreview() {
    RingoutTheme(ThemeMode.Dark) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopEnd) {
            Icon(painterResource(RoomHomeMoreIconResource), contentDescription = "모임 메뉴", modifier = Modifier.size(48.dp))
            RoomHomeDropdownMenu(
                expanded = true,
                membershipRole = RoomMembershipRole.OWNER,
                onDismissRequest = {},
                onItemSelected = {},
            )
        }
    }
}

@Preview(name = "참여자 메뉴 · 라이트", widthDp = 360)
@Composable
private fun RoomHomeMemberMenuPreview() {
    RingoutTheme(ThemeMode.Light) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopEnd) {
            RoomHomeDropdownMenu(
                expanded = true,
                membershipRole = RoomMembershipRole.MEMBER,
                onDismissRequest = {},
                onItemSelected = {},
            )
        }
    }
}
