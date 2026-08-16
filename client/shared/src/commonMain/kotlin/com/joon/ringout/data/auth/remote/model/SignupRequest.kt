package com.joon.ringout.data.auth.remote.model

import kotlinx.serialization.Serializable

@Serializable
data class SignupRequest(
    val termsTypes: List<TermsType>,
    val agreedAt: String,
)

@Serializable
enum class TermsType {
    SERVICE,
    PRIVACY,
}
