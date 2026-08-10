package com.joon.ringout.presentation.mypage

import kotlin.jvm.JvmInline

@JvmInline
value class PolicyId(val value: String) {
    init {
        require(value.isNotBlank()) { "Policy id must not be blank." }
    }
}

enum class PolicyIcon {
    PRIVACY,
    DOCUMENT,
}

data class PolicyInfo(
    val id: PolicyId,
    val title: String,
    val icon: PolicyIcon,
) {
    init {
        require(title.isNotBlank()) { "Policy title must not be blank." }
    }
}

private val PrivacyPolicyId = PolicyId("privacy")
private val TermsPolicyId = PolicyId("terms")

private const val PrivacyPolicyUrl =
    "https://app.notion.com/p/3b858b8d8e6c80d2be23f7e4c0ff23bd?source=copy_link"
private const val TermsPolicyUrl =
    "https://sheer-mimosa-20f.notion.site/3b858b8d8e6c80de9724e0cd32000093?source=copy_link"

val DefaultMyPagePolicies: List<PolicyInfo> = requireUniquePolicyIds(
    listOf(
        PolicyInfo(PrivacyPolicyId, "개인정보처리방침", PolicyIcon.PRIVACY),
        PolicyInfo(TermsPolicyId, "이용약관", PolicyIcon.DOCUMENT),
    ),
)

internal fun findPolicyUrl(policyId: PolicyId): String? = when (policyId) {
    PrivacyPolicyId -> PrivacyPolicyUrl
    TermsPolicyId -> TermsPolicyUrl
    else -> null
}

fun requireUniquePolicyIds(policies: List<PolicyInfo>): List<PolicyInfo> =
    policies.also {
        require(it.map(PolicyInfo::id).distinct().size == it.size) {
            "Policy ids must be unique."
        }
    }
