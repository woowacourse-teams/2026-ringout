package com.joon.ringout.presentation.mypage.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

@Composable
fun MyPageAccountManagementSection(
    onLogoutClick: () -> Unit,
    onWithdrawClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val colors = myPageColors()

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "계정 관리",
            modifier = Modifier.semantics { heading() },
            color = colors.primaryText,
            style = MaterialTheme.typography.titleSmall.copy(
                fontSize = 13.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
        Spacer(Modifier.height(AccountManagementTitleSpacing))
        MyPageAccountManagementRow(
            title = "로그아웃",
            iconResource = MyPageLogoutIconResource,
            enabled = enabled,
            onClick = onLogoutClick,
        )
        MyPageAccountManagementRow(
            title = "회원탈퇴",
            iconResource = MyPageDeleteAccountIconResource,
            enabled = enabled,
            onClick = onWithdrawClick,
        )
    }
}

@Composable
private fun MyPageAccountManagementRow(
    title: String,
    iconResource: DrawableResource,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = myPageColors()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(AccountManagementRowHeight)
            .alpha(if (enabled) EnabledAlpha else DisabledAlpha)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClickLabel = title,
                onClick = onClick,
            )
            .padding(start = AccountManagementRowStartPadding, end = AccountManagementRowEndPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(iconResource),
            contentDescription = null,
            modifier = Modifier.size(AccountManagementIconSize),
        )
        Spacer(Modifier.width(AccountManagementIconSpacing))
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            color = colors.primaryText,
            maxLines = 1,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 13.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
        Text(
            text = ">",
            modifier = Modifier.width(AccountManagementChevronWidth),
            color = colors.primaryText,
            fontSize = 13.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
}

private val AccountManagementTitleSpacing = 8.dp
private val AccountManagementRowHeight = 54.dp
private val AccountManagementRowStartPadding = 10.dp
private val AccountManagementRowEndPadding = 18.dp
private val AccountManagementIconSize = 24.dp
private val AccountManagementIconSpacing = 11.dp
private val AccountManagementChevronWidth = 9.dp
private const val EnabledAlpha = 1f
private const val DisabledAlpha = 0.5f

@Preview(name = "Account management - Dark", widthDp = 402)
@Composable
private fun MyPageAccountManagementSectionDarkPreview() {
    RingoutTheme(ThemeMode.Dark) {
        MyPageAccountManagementSection(
            onLogoutClick = {},
            onWithdrawClick = {},
            modifier = Modifier.padding(horizontal = 20.dp),
        )
    }
}

@Preview(name = "Account management - Light", widthDp = 402)
@Composable
private fun MyPageAccountManagementSectionLightPreview() {
    RingoutTheme(ThemeMode.Light) {
        MyPageAccountManagementSection(
            onLogoutClick = {},
            onWithdrawClick = {},
            modifier = Modifier.padding(horizontal = 20.dp),
        )
    }
}
