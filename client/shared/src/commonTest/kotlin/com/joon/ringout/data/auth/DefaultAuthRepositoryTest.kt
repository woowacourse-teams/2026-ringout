package com.joon.ringout.data.auth

import com.joon.ringout.data.auth.remote.AuthApi
import com.joon.ringout.data.auth.remote.AuthProvider
import com.joon.ringout.data.auth.remote.model.LoginRequest
import com.joon.ringout.data.auth.remote.model.LoginResponse
import com.joon.ringout.data.auth.remote.model.ReissueRequest
import com.joon.ringout.data.auth.remote.model.ReissueResponse
import com.joon.ringout.data.auth.remote.model.SignupRequest
import com.joon.ringout.data.auth.remote.model.SignupResponse
import com.joon.ringout.data.network.ApiResponse
import com.joon.ringout.domain.auth.AuthTerm
import com.joon.ringout.domain.auth.AuthSession
import com.joon.ringout.domain.auth.AuthSessionState
import com.joon.ringout.domain.auth.AuthTokens
import com.joon.ringout.domain.auth.SecureTokenStorage
import com.joon.ringout.domain.auth.SocialLoginOutcome
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class DefaultAuthRepositoryTest {
    @Test
    fun restoreSessionAuthenticatesWhenStoredTokensExist() = runTest {
        val storage = FakeTokenStorage().apply {
            tokens = AuthTokens("stored-access", "stored-refresh")
        }
        val session = AuthSession()
        val repository = DefaultAuthRepository(FakeAuthApi(), storage, session)

        repository.restoreSession()

        assertEquals(AuthSessionState.Authenticated, session.state.value)
    }

    @Test
    fun restoreSessionClearsSessionWhenStoredTokensDoNotExist() = runTest {
        val session = AuthSession()
        val repository = DefaultAuthRepository(FakeAuthApi(), FakeTokenStorage(), session)

        repository.restoreSession()

        assertEquals(AuthSessionState.Unauthenticated, session.state.value)
    }

    @Test
    fun restoreSessionKeepsReauthenticationRequiredWhenExpiredTokensWereDeleted() = runTest {
        val session = AuthSession().apply { requireReauthentication() }
        val repository = DefaultAuthRepository(FakeAuthApi(), FakeTokenStorage(), session)

        repository.restoreSession()

        assertEquals(AuthSessionState.ReauthenticationRequired, session.state.value)
    }

    @Test
    fun restoreSessionRestoresPersistedReauthenticationRequiredState() = runTest {
        val persistedState = FakePersistedAuthState()
        FakeTokenStorage(persistedState).expire()
        val recreatedStorage = FakeTokenStorage(persistedState)
        val restartedSession = AuthSession()
        val repository = DefaultAuthRepository(FakeAuthApi(), recreatedStorage, restartedSession)

        repository.restoreSession()

        assertEquals(AuthSessionState.ReauthenticationRequired, restartedSession.state.value)
    }

    @Test
    fun logoutDeletesStoredTokensAndClearsSession() = runTest {
        val storage = FakeTokenStorage().apply {
            tokens = AuthTokens("stored-access", "stored-refresh")
        }
        val session = AuthSession().apply { markAuthenticated() }
        val repository = DefaultAuthRepository(FakeAuthApi(), storage, session)

        repository.logout()

        assertNull(storage.tokens)
        assertEquals(AuthSessionState.Unauthenticated, session.state.value)
    }

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
        val session = AuthSession()
        val repository = DefaultAuthRepository(api, storage, session)

        val outcome = repository.loginWithGoogle("google-access-token")

        assertIs<SocialLoginOutcome.Authenticated>(outcome)
        assertEquals(AuthProvider.Google, api.loginProvider)
        assertEquals("google-access-token", api.loginRequest?.socialAccessToken)
        assertEquals(AuthTokens("ringout-access", "ringout-refresh"), storage.tokens)
        assertEquals(AuthSessionState.Authenticated, session.state.value)
    }

    @Test
    fun loginCancellationDuringTokenSaveStillUpdatesTokensAndSessionTogether() = runTest {
        val saveStarted = CompletableDeferred<Unit>()
        val allowSaveCompletion = CompletableDeferred<Unit>()
        val api = FakeAuthApi(
            loginResponse = LoginResponse(
                accessToken = "ringout-access",
                refreshToken = "ringout-refresh",
                isNewUser = false,
            ),
        )
        val storage = FakeTokenStorage(
            onSave = {
                saveStarted.complete(Unit)
                allowSaveCompletion.await()
            },
        )
        val session = AuthSession()
        val repository = DefaultAuthRepository(api, storage, session)

        val login = async { repository.loginWithGoogle("google-access-token") }
        saveStarted.await()
        login.cancel()
        allowSaveCompletion.complete(Unit)
        login.join()

        assertEquals(AuthTokens("ringout-access", "ringout-refresh"), storage.tokens)
        assertEquals(false, storage.isReauthenticationRequired())
        assertEquals(AuthSessionState.Authenticated, session.state.value)
    }

    @Test
    fun newGoogleUserKeepsSignupTokenOutOfSecureStorage() = runTest {
        val storage = FakeTokenStorage()
        val session = AuthSession()
        val repository = DefaultAuthRepository(
            authApi = FakeAuthApi(
                loginResponse = LoginResponse(
                    signupToken = "signup-token",
                    isNewUser = true,
                ),
            ),
            tokenStorage = storage,
            authSession = session,
        )

        val outcome = repository.loginWithGoogle("google-access-token")

        assertEquals(
            SocialLoginOutcome.SignupRequired("signup-token"),
            outcome,
        )
        assertNull(storage.tokens)
        assertEquals(AuthSessionState.Unauthenticated, session.state.value)
    }

    @Test
    fun existingKakaoUserSendsKakaoAccessTokenAndStoresRingoutTokens() = runTest {
        val api = FakeAuthApi(
            loginResponse = LoginResponse(
                accessToken = "ringout-access",
                refreshToken = "ringout-refresh",
                isNewUser = false,
            ),
        )
        val storage = FakeTokenStorage()
        val session = AuthSession()
        val repository = DefaultAuthRepository(api, storage, session)

        val outcome = repository.loginWithKakao("kakao-access-token")

        assertIs<SocialLoginOutcome.Authenticated>(outcome)
        assertEquals(AuthProvider.Kakao, api.loginProvider)
        assertEquals("kakao-access-token", api.loginRequest?.socialAccessToken)
        assertEquals(AuthTokens("ringout-access", "ringout-refresh"), storage.tokens)
        assertEquals(AuthSessionState.Authenticated, session.state.value)
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
        val session = AuthSession()
        val repository = DefaultAuthRepository(api, storage, session)

        repository.signup(
            signupToken = "signup-token",
            agreedTerms = setOf(AuthTerm.SERVICE, AuthTerm.PRIVACY),
            agreedAt = "2026-08-16",
        )

        assertEquals("signup-token", api.signupToken)
        assertEquals("2026-08-16", api.signupRequest?.agreedAt)
        assertEquals(AuthTokens("ringout-access", "ringout-refresh"), storage.tokens)
        assertEquals(AuthSessionState.Authenticated, session.state.value)
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

    override suspend fun reissue(
        request: ReissueRequest,
    ): ApiResponse<ReissueResponse> = error("재발급 API는 이 테스트에서 호출되지 않습니다.")
}

private class FakeTokenStorage(
    private val persistedState: FakePersistedAuthState = FakePersistedAuthState(),
    private val onSave: suspend () -> Unit = {},
) : SecureTokenStorage {
    var tokens: AuthTokens?
        get() = persistedState.tokens
        set(value) {
            persistedState.tokens = value
        }

    override suspend fun save(tokens: AuthTokens) {
        this.tokens = tokens
        onSave()
        persistedState.reauthenticationRequired = false
    }

    override suspend fun read(): AuthTokens? = tokens

    override suspend fun clear() {
        tokens = null
        persistedState.reauthenticationRequired = false
    }

    override suspend fun expire() {
        tokens = null
        persistedState.reauthenticationRequired = true
    }

    override suspend fun isReauthenticationRequired(): Boolean =
        persistedState.reauthenticationRequired
}

private data class FakePersistedAuthState(
    var tokens: AuthTokens? = null,
    var reauthenticationRequired: Boolean = false,
)
