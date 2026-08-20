package com.joon.ringout.presentation.mypage.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import com.joon.ringout.ringoutColors
import org.jetbrains.compose.resources.painterResource
import ringout.shared.generated.resources.Res
import ringout.shared.generated.resources.mypage_logged_out_profile

@Composable
fun MyPageAccountStatus(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = myPageColors()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(AccountStatusHeight)
            .clickable(
                role = Role.Button,
                onClickLabel = "로그인 화면으로 이동",
                onClick = onClick,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(ProfileImageSize)
                .clip(CircleShape)
                .background(MaterialTheme.ringoutColors.profileIconLoggedOutBackground),
        ) {
            Image(
                painter = painterResource(Res.drawable.mypage_logged_out_profile),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.width(ProfileTextSpacing))
        Column(
            modifier = Modifier
                .weight(1f)
                .height(AccountTextHeight),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "비로그인 상태입니다.",
                color = colors.primaryText,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 18.sp,
                    lineHeight = 21.sp,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
            )
            Spacer(Modifier.height(TextLineSpacing))
            Text(
                text = "계정 연동하기",
                color = colors.secondaryText,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.Normal,
                ),
                maxLines = 1,
            )
        }
    }
}

private val AccountStatusHeight = 62.dp
private val ProfileImageSize = 47.dp
private val ProfileTextSpacing = 12.dp
private val AccountTextHeight = 39.dp
private val TextLineSpacing = 3.dp

@Preview(widthDp = 402)
@Composable
private fun MyPageAccountStatusPreview() {
    RingoutTheme(ThemeMode.Dark) {
        MyPageAccountStatus(onClick = {})
    }
}
