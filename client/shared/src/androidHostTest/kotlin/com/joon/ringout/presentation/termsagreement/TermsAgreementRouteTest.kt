package com.joon.ringout.presentation.termsagreement

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.joon.ringout.RingoutTheme
import com.joon.ringout.analytics.AnalyticsAuthProvider
import com.joon.ringout.analytics.AnalyticsTracker
import com.joon.ringout.analytics.DefaultProductAnalyticsRecorder
import com.joon.ringout.analytics.InMemoryProductAnalyticsUsageStore
import com.joon.ringout.domain.auth.AuthRepository
import com.joon.ringout.domain.auth.AuthTerm
import com.joon.ringout.domain.auth.SocialLoginOutcome
import com.joon.ringout.domain.destination.DestinationRepository
import com.joon.ringout.domain.destination.SavedDestination
import com.joon.ringout.presentation.signup.SignupViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w402dp-h947dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TermsAgreementRouteTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test
    fun `가입을 취소한 뒤 퇴장 중인 약관은 내용을 유지하고 입력과 화면 이동을 실행하지 않는다`() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val authRepository = ExitAuthRepository()
        val signup = SignupViewModel(
            authRepository = authRepository,
            destinationRepository = ExitDestinationRepository(),
            productAnalyticsRecorder = DefaultProductAnalyticsRecorder(
                tracker = AnalyticsTracker {},
                usageStore = InMemoryProductAnalyticsUsageStore(),
            ),
        )
        signup.startSignup("cancelled-token", AnalyticsAuthProvider.Apple)
        var active by mutableStateOf(true)
        var missingSignup = 0
        var completedSignup = 0
        try {
            activity.get().setContent {
                RingoutTheme {
                    TermsAgreementRoute(
                        signupViewModel = signup,
                        isActive = active,
                        onMissingSignup = { missingSignup++ },
                        onSignupCompleted = { completedSignup++; true },
                    )
                }
            }
            compose.onNodeWithText("전체 동의").performClick().assertIsOn()
            compose.runOnIdle {
                signup.resetSignup()
                active = false
            }

            compose.onNodeWithText("서비스 이용을 위해 약관에 동의해주세요").assertIsDisplayed()
            compose.onNodeWithText("전체 동의").assertIsOn().performClick().assertIsOn()
            compose.onNodeWithText("시작하기").performClick()
            compose.runOnIdle {
                assertFalse(signup.uiState.hasPendingSignup)
                assertEquals(0, authRepository.signupRequests)
                assertEquals(0, missingSignup)
                assertEquals(0, completedSignup)
            }
        } finally {
            activity.pause().stop().destroy()
        }
    }
}

private class ExitAuthRepository : AuthRepository {
    var signupRequests = 0

    override suspend fun restoreSession() = Unit
    override suspend fun loginWithApple(idToken: String) = SocialLoginOutcome.Authenticated
    override suspend fun loginWithGoogle(accessToken: String) = SocialLoginOutcome.Authenticated
    override suspend fun loginWithKakao(accessToken: String) = SocialLoginOutcome.Authenticated
    override suspend fun signup(signupToken: String, agreedTerms: Set<AuthTerm>, agreedAt: String) {
        signupRequests++
    }
    override suspend fun logout() = Unit
}

private class ExitDestinationRepository : DestinationRepository {
    override fun observeAll(): Flow<List<SavedDestination>> = flowOf(emptyList())
    override suspend fun fetchAll(): List<SavedDestination> = emptyList()
    override suspend fun sync(): List<SavedDestination> = emptyList()
    override suspend fun save(destination: SavedDestination): SavedDestination = destination
    override suspend fun delete(id: Long): Boolean = false
}
