package com.joon.ringout.data.auth

import com.joon.ringout.data.auth.remote.AuthApi
import com.joon.ringout.data.auth.remote.AuthProvider
import com.joon.ringout.data.auth.remote.model.LoginRequest
import com.joon.ringout.data.auth.remote.model.LoginResponse
import com.joon.ringout.data.auth.remote.model.SignupRequest
import com.joon.ringout.data.auth.remote.model.SignupResponse
import com.joon.ringout.data.network.ApiResponse
import com.joon.ringout.domain.auth.AuthTerm
import com.joon.ringout.domain.auth.AuthTokens
import com.joon.ringout.domain.auth.SecureTokenStorage
import com.joon.ringout.domain.auth.SocialLoginOutcome
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class DefaultAuthRepositoryTest {
    @Test
    fun existingGoogleUserStoresRingoutTokens() = runTest {
        val api = FakeAuthApi(
            loginResponse = LoginResponse(
                accessToken = "ringout-access",
                refreshToken = "ringout-refresh",
                isNewUser = false,
            ),
        )
        val storage = FakeTokenStorage()
        val repository = DefaultAuthRepository(api, storage)

        val outcome = repository.loginWithGoogle("google-id-token")

        assertIs<SocialLoginOutcome.Authenticated>(outcome)
        assertEquals(AuthProvider.Google, api.loginProvider)
        assertEquals("google-id-token", api.loginRequest?.socialAccessToken)
        assertEquals(AuthTokens("ringout-access", "ringout-refresh"), storage.tokens)
    }

    @Test
    fun newGoogleUserKeepsSignupTokenOutOfSecureStorage() = runTest {
        val storage = FakeTokenStorage()
        val repository = DefaultAuthRepository(
            authApi = FakeAuthApi(
                loginResponse = LoginResponse(
                    signupToken = "signup-token",
                    isNewUser = true,
                ),
            ),
            tokenStorage = storage,
        )

        val outcome = repository.loginWithGoogle("google-id-token")

        assertEquals(
            SocialLoginOutcome.SignupRequired("signup-token"),
            outcome,
        )
        assertNull(storage.tokens)
    }

    @Test
    fun signupStoresRingoutTokensAfterTermsAgreement() = runTest {
        val api = FakeAuthApi(
            signupResponse = SignupResponse(
                accessToken = "ringout-access",
                refreshToken = "ringout-refresh",
            ),
        )
        val storage = FakeTokenStorage()
        val repository = DefaultAuthRepository(api, storage)

        repository.signup(
            signupToken = "signup-token",
            agreedTerms = setOf(AuthTerm.SERVICE, AuthTerm.PRIVACY),
            agreedAt = "2026-08-16",
        )

        assertEquals("signup-token", api.signupToken)
        assertEquals("2026-08-16", api.signupRequest?.agreedAt)
        assertEquals(AuthTokens("ringout-access", "ringout-refresh"), storage.tokens)
    }
}

private class FakeAuthApi(
    private val loginResponse: LoginResponse = LoginResponse(isNewUser = true),
    private val signupResponse: SignupResponse = SignupResponse("access", "refresh"),
) : AuthApi {
    var loginProvider: AuthProvider? = null
    var loginRequest: LoginRequest? = null
    var signupToken: String? = null
    var signupRequest: SignupRequest? = null

    override suspend fun login(
        provider: AuthProvider,
        request: LoginRequest,
    ): ApiResponse<LoginResponse> {
        loginProvider = provider
        loginRequest = request
        return ApiResponse(
            isSuccess = true,
            code = "AUTH200",
            message = "성공",
            result = loginResponse,
        )
    }

    override suspend fun signup(
        signupToken: String,
        request: SignupRequest,
    ): ApiResponse<SignupResponse> {
        this.signupToken = signupToken
        signupRequest = request
        return ApiResponse(
            isSuccess = true,
            code = "AUTH201",
            message = "성공",
            result = signupResponse,
        )
    }
}

private class FakeTokenStorage : SecureTokenStorage {
    var tokens: AuthTokens? = null

    override suspend fun save(tokens: AuthTokens) {
        this.tokens = tokens
    }

    override suspend fun read(): AuthTokens? = tokens

    override suspend fun clear() {
        tokens = null
    }
}
