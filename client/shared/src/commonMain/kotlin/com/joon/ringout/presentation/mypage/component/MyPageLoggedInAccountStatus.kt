package com.joon.ringout.presentation.mypage.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import org.jetbrains.compose.resources.painterResource
import ringout.shared.generated.resources.Res
import ringout.shared.generated.resources.home_empty_logo

@Composable
fun MyPageLoggedInAccountStatus(
    nickname: String,
    email: String,
    onEditClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = myPageColors()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(LoggedInAccountStatusHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(Res.drawable.home_empty_logo),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(LoggedInProfileImageSize)
                .clip(CircleShape),
        )
        Spacer(Modifier.width(LoggedInProfileTextSpacing))
        Column(
            modifier = Modifier
                .weight(1f)
                .height(LoggedInAccountTextHeight),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = nickname,
                color = colors.primaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 18.sp,
                    lineHeight = 21.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Spacer(Modifier.height(LoggedInTextLineSpacing))
            Text(
                text = email,
                color = colors.secondaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.Normal,
                ),
            )
        }
        Box(
            modifier = Modifier
                .size(LoggedInEditTouchTargetSize)
                .offset(x = LoggedInEditTouchTargetOffset)
                .clickable(
                    role = Role.Button,
                    onClickLabel = "프로필 수정",
                    onClick = onEditClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(MyPageEditProfileIconResource),
                contentDescription = null,
                modifier = Modifier.size(LoggedInEditIconSize),
                colorFilter = ColorFilter.tint(colors.primaryText),
            )
        }
    }
}

private val LoggedInAccountStatusHeight = 62.dp
private val LoggedInProfileImageSize = 47.dp
private val LoggedInProfileTextSpacing = 12.dp
private val LoggedInAccountTextHeight = 39.dp
private val LoggedInTextLineSpacing = 3.dp
private val LoggedInEditTouchTargetSize = 48.dp
private val LoggedInEditTouchTargetOffset = 12.dp
private val LoggedInEditIconSize = 24.dp

@Preview(name = "Logged in account - Dark", widthDp = 402)
@Composable
private fun MyPageLoggedInAccountStatusDarkPreview() {
    RingoutTheme(ThemeMode.Dark) {
        MyPageLoggedInAccountStatus(
            nickname = "닉네임닉네임12312313",
            email = "dsakdfsa@gmail.com",
            onEditClick = {},
        )
    }
}

@Preview(name = "Logged in account - Light", widthDp = 402)
@Composable
private fun MyPageLoggedInAccountStatusLightPreview() {
    RingoutTheme(ThemeMode.Light) {
        MyPageLoggedInAccountStatus(
            nickname = "닉네임닉네임12312313",
            email = "dsakdfsa@gmail.com",
            onEditClick = {},
        )
    }
}
