package com.joon.ringout.presentation.mypage.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joon.ringout.presentation.mypage.PolicyIcon
import com.joon.ringout.presentation.mypage.PolicyId
import com.joon.ringout.presentation.mypage.PolicyInfo
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

@Composable
fun MyPagePolicySection(
    policies: List<PolicyInfo>,
    onPolicyClick: (PolicyId) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (policies.isEmpty()) return
    val colors = myPageColors()

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "약관 및 정책",
            modifier = Modifier.semantics { heading() },
            color = colors.primaryText,
            style = MaterialTheme.typography.titleSmall.copy(
                fontSize = 13.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
        Spacer(Modifier.height(8.dp))
        policies.forEach { policy ->
            PolicyRow(policy = policy, onClick = { onPolicyClick(policy.id) })
        }
    }
}

@Preview(widthDp = 402)
@Composable
private fun MyPagePolicySectionPreview() {
    RingoutTheme(ThemeMode.Dark) {
        MyPagePolicySection(
            policies = listOf(
                PolicyInfo(PolicyId("privacy"), "개인정보처리방침", PolicyIcon.PRIVACY),
                PolicyInfo(PolicyId("terms"), "이용약관", PolicyIcon.DOCUMENT),
            ),
            onPolicyClick = {},
            modifier = Modifier.padding(20.dp),
        )
    }
}

@Composable
private fun PolicyRow(
    policy: PolicyInfo,
    onClick: () -> Unit,
) {
    val colors = myPageColors()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clickable(role = Role.Button, onClickLabel = policy.title, onClick = onClick)
            .padding(start = 10.dp, end = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PolicyGlyph(policy.icon)
        Spacer(Modifier.width(11.dp))
        Text(
            text = policy.title,
            modifier = Modifier.weight(1f),
            color = colors.primaryText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 13.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
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

@Composable
private fun PolicyGlyph(icon: PolicyIcon) {
    val resource: DrawableResource = when (icon) {
        PolicyIcon.PRIVACY -> MyPagePolicyPrivacyIconResource
        PolicyIcon.DOCUMENT -> MyPagePolicyDocsIconResource
    }
    Image(
        painter = painterResource(resource),
        contentDescription = null,
        modifier = Modifier.size(24.dp),
    )
}
