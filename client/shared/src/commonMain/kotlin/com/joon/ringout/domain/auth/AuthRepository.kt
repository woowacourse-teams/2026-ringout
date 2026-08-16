package com.joon.ringout.domain.auth

sealed interface SocialLoginOutcome {
    data object Authenticated : SocialLoginOutcome

    data class SignupRequired(
        val signupToken: String,
    ) : SocialLoginOutcome
}

interface AuthRepository {
    suspend fun loginWithGoogle(idToken: String): SocialLoginOutcome

    suspend fun signup(
        signupToken: String,
        agreedTerms: Set<AuthTerm>,
        agreedAt: String,
    )
}

enum class AuthTerm {
    SERVICE,
    PRIVACY,
}
