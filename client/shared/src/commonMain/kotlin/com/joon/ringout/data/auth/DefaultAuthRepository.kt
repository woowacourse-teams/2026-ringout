package com.joon.ringout.data.auth

import com.joon.ringout.data.auth.remote.AuthApi
import com.joon.ringout.data.auth.remote.AuthProvider
import com.joon.ringout.data.auth.remote.model.LoginRequest
import com.joon.ringout.data.auth.remote.model.SignupRequest
import com.joon.ringout.data.auth.remote.model.TermsType
import com.joon.ringout.domain.auth.AuthRepository
import com.joon.ringout.domain.auth.AuthSession
import com.joon.ringout.domain.auth.AuthTerm
import com.joon.ringout.domain.auth.AuthTokens
import com.joon.ringout.domain.auth.SecureTokenStorage
import com.joon.ringout.domain.auth.SocialLoginOutcome

class DefaultAuthRepository(
    private val authApi: AuthApi,
    private val tokenStorage: SecureTokenStorage,
    private val authSession: AuthSession,
) : AuthRepository {
    override suspend fun loginWithGoogle(idToken: String): SocialLoginOutcome {
        require(idToken.isNotBlank()) { "Google ID token must not be blank." }

        val response = authApi.login(
            provider = AuthProvider.Google,
            request = LoginRequest(socialAccessToken = idToken),
        )
        check(response.isSuccess) { response.message }
        val login = checkNotNull(response.result) { "로그인 응답이 비어 있어요." }

        return if (login.isNewUser) {
            SocialLoginOutcome.SignupRequired(
                signupToken = requireNotNull(login.signupToken?.takeIf(String::isNotBlank)) {
                    "가입 토큰이 비어 있어요."
                },
            )
        } else {
            tokenStorage.save(
                AuthTokens(
                    accessToken = requireNotNull(login.accessToken?.takeIf(String::isNotBlank)) {
                        "액세스 토큰이 비어 있어요."
                    },
                    refreshToken = requireNotNull(login.refreshToken?.takeIf(String::isNotBlank)) {
                        "리프레시 토큰이 비어 있어요."
                    },
                ),
            )
            authSession.markAuthenticated()
            SocialLoginOutcome.Authenticated
        }
    }

    override suspend fun signup(
        signupToken: String,
        agreedTerms: Set<AuthTerm>,
        agreedAt: String,
    ) {
        require(signupToken.isNotBlank()) { "Signup token must not be blank." }
        require(agreedTerms.isNotEmpty()) { "Agreed terms must not be empty." }
        require(agreedAt.isNotBlank()) { "Agreement date must not be blank." }

        val response = authApi.signup(
            signupToken = signupToken,
            request = SignupRequest(
                termsTypes = agreedTerms.map { term -> TermsType.valueOf(term.name) },
                agreedAt = agreedAt,
            ),
        )
        check(response.isSuccess) { response.message }
        val signup = checkNotNull(response.result) { "회원가입 응답이 비어 있어요." }
        tokenStorage.save(
            AuthTokens(
                accessToken = signup.accessToken,
                refreshToken = signup.refreshToken,
            ),
        )
        authSession.markAuthenticated()
    }
}
