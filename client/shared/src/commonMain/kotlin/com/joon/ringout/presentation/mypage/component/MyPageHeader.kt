package com.joon.ringout.presentation.mypage.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joon.ringout.RingoutTheme
import org.jetbrains.compose.resources.painterResource

@Composable
fun MyPageHeader(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = myPageColors()

    Box(
        modifier = modifier.fillMaxWidth().height(48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(48.dp)
                .offset(x = (-12).dp)
                .clickable(
                    role = Role.Button,
                    onClickLabel = "이전 화면으로 이동",
                    onClick = onBackClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(MyPageArrowLeftIconResource),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                colorFilter = ColorFilter.tint(colors.primaryText),
            )
        }
        Text(
            text = "마이페이지",
            color = colors.primaryText,
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 18.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
    }
}

@Preview
@Composable
private fun MyPageHeaderPreview() {
    RingoutTheme { MyPageHeader(onBackClick = {}) }
}
