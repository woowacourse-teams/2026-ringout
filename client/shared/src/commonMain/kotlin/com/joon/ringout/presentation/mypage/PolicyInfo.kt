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

val DefaultMyPagePolicies: List<PolicyInfo> = requireUniquePolicyIds(
    listOf(
        PolicyInfo(PolicyId("privacy"), "개인정보처리방침", PolicyIcon.PRIVACY),
        PolicyInfo(PolicyId("terms"), "이용약관", PolicyIcon.DOCUMENT),
    ),
)

fun requireUniquePolicyIds(policies: List<PolicyInfo>): List<PolicyInfo> =
    policies.also {
        require(it.map(PolicyInfo::id).distinct().size == it.size) {
            "Policy ids must be unique."
        }
    }
