package com.joon.ringout.presentation.mypage.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
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
fun MyPageDataSection(
    onSaveClick: () -> Unit,
    onLoadClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = myPageColors()
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "데이터",
            modifier = Modifier.semantics { heading() },
            color = colors.primaryText,
            style = MaterialTheme.typography.titleSmall.copy(
                fontSize = 13.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
        Spacer(Modifier.height(8.dp))
        Column(modifier = Modifier.clip(RoundedCornerShape(16.dp))) {
            MyPageDataRow(
                title = "데이터 저장하기",
                icon = MyPagePolicyPrivacyIconResource,
                onClick = onSaveClick,
                enabled = enabled,
            )
            MyPageDataRow(
                title = "데이터 불러오기",
                icon = MyPagePolicyDocsIconResource,
                onClick = onLoadClick,
                enabled = enabled,
            )
        }
    }
}

@Composable
private fun MyPageDataRow(
    title: String,
    icon: DrawableResource,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = myPageColors()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = title, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp)
            .alpha(if (enabled) 1f else 0.5f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(11.dp))
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            color = colors.primaryText,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 13.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
        Spacer(Modifier.width(11.dp))
        Text(
            text = ">",
            modifier = Modifier.width(9.dp),
            color = colors.primaryText,
            fontSize = 13.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
}

@Preview(name = "Data - Dark", widthDp = 401)
@Composable
private fun MyPageDataSectionPreview() {
    RingoutTheme(ThemeMode.Dark) {
        MyPageDataSection(
            onSaveClick = {},
            onLoadClick = {},
            modifier = Modifier.background(myPageColors().background).padding(20.dp),
        )
    }
}

@Preview(name = "Data - Light", widthDp = 360)
@Composable
private fun MyPageDataSectionLightPreview() {
    RingoutTheme(ThemeMode.Light) {
        MyPageDataSection(
            onSaveClick = {},
            onLoadClick = {},
            modifier = Modifier.background(myPageColors().background).padding(20.dp),
        )
    }
}

@Preview(name = "Data row - Disabled", widthDp = 360)
@Composable
private fun MyPageDataRowPreview() {
    RingoutTheme(ThemeMode.Dark) {
        MyPageDataRow(
            title = "데이터 저장하기",
            icon = MyPagePolicyPrivacyIconResource,
            onClick = {},
            enabled = false,
            modifier = Modifier.background(myPageColors().background),
        )
    }
}
