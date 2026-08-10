package com.joon.ringout.presentation.mypage.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joon.ringout.presentation.mypage.PolicyIcon
import com.joon.ringout.presentation.mypage.PolicyId
import com.joon.ringout.presentation.mypage.PolicyInfo
import com.joon.ringout.RingoutTheme
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import ringout.shared.generated.resources.Res
import ringout.shared.generated.resources.mypage_policy_privacy
import ringout.shared.generated.resources.mypage_policy_docs

@Composable
fun MyPagePolicySection(
    policies: List<PolicyInfo>,
    onPolicyClick: (PolicyId) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (policies.isEmpty()) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = "약관 및 정책",
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
        )
        policies.forEach { policy ->
            PolicyRow(policy = policy, onClick = { onPolicyClick(policy.id) })
        }
    }
}

@Preview(widthDp = 402)
@Composable
private fun MyPagePolicySectionPreview() {
    RingoutTheme {
        MyPagePolicySection(
            policies = listOf(
                PolicyInfo(PolicyId("privacy"), "개인정보처리방침", PolicyIcon.PRIVACY),
                PolicyInfo(PolicyId("terms"), "이용약관", PolicyIcon.DOCUMENT),
                PolicyInfo(PolicyId("location"), "위치정보 이용약관", PolicyIcon.DOCUMENT),
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(62.dp)
            .clickable(role = Role.Button, onClickLabel = policy.title, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PolicyGlyph(policy.icon)
        Spacer(Modifier.width(16.dp))
        Text(
            text = policy.title,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
        )
        Text(
            text = "›",
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 24.sp,
        )
    }
}

@Composable
private fun PolicyGlyph(icon: PolicyIcon) {
    val resource: DrawableResource = when (icon) {
        PolicyIcon.PRIVACY -> Res.drawable.mypage_policy_privacy
        PolicyIcon.DOCUMENT -> Res.drawable.mypage_policy_docs
    }
    Image(
        painter = painterResource(resource),
        contentDescription = null,
        modifier = Modifier.size(28.dp),
    )
}
