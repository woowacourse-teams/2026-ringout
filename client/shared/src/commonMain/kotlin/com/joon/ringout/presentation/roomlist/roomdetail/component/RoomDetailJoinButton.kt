package com.joon.ringout.presentation.roomlist.roomdetail.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ringoutColors

@Composable
internal fun RoomDetailJoinButton(
    isJoined: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    isJoining: Boolean = false,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(53.dp),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.ringoutColors.primaryActionContent,
            disabledContainerColor = MaterialTheme.ringoutColors.elevatedSurface,
            disabledContentColor = MaterialTheme.ringoutColors.navigationInactiveContent,
        ),
    ) {
        Text(
            text = when {
                isJoined -> "이미 가입한 모임이에요."
                isJoining -> "가입 중..."
                else -> "가입하기"
            },
            style = MaterialTheme.typography.titleSmall.copy(
                fontSize = 16.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
    }
}

@Preview(name = "미가입 · 가입 가능", showBackground = true)
@Composable
private fun RoomDetailJoinButtonJoinablePreview() {
    RingoutTheme {
        RoomDetailJoinButton(
            isJoined = false,
            enabled = true,
            onClick = {},
        )
    }
}

@Preview(name = "이미 가입", showBackground = true)
@Composable
private fun RoomDetailJoinButtonAlreadyJoinedPreview() {
    RingoutTheme {
        RoomDetailJoinButton(
            isJoined = true,
            enabled = false,
            onClick = {},
        )
    }
}

@Preview(name = "세션 확인 중 · 비활성", showBackground = true)
@Composable
private fun RoomDetailJoinButtonSessionRestoringPreview() {
    RingoutTheme {
        RoomDetailJoinButton(
            isJoined = false,
            enabled = false,
            onClick = {},
        )
    }
}
