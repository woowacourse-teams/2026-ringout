package com.joon.ringout.presentation.destination

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.joon.ringout.RingoutTheme
import com.joon.ringout.analytics.AnalyticsEventName
import com.joon.ringout.analytics.AnalyticsTracker
import com.joon.ringout.analytics.DefaultProductAnalyticsRecorder
import com.joon.ringout.analytics.ProductAnalyticsUsageStore
import com.joon.ringout.domain.auth.AuthSessionState
import com.joon.ringout.domain.destination.DestinationRepository
import com.joon.ringout.domain.destination.SavedDestination
import com.joon.ringout.presentation.alarmsetup.AlarmSetupViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w402dp-h947dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DestinationMapDisplayNameTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test
    fun `목적지를 삭제한 뒤 지도에 재진입하면 알람 원본은 유지하고 주소를 표시한다`() = withMap { fixture ->
        compose.onNodeWithText(Favorite.name).assertIsDisplayed()
        compose.onNodeWithContentDescription("뒤로가기").performClick()
        val originalRequest = requireNotNull(fixture.editor.createScheduleRequest())
        compose.runOnIdle {
            assertTrue(fixture.destinations.beginManagement(Favorite.id))
            fixture.destinations.deleteManagedDestination()
        }
        compose.waitUntil { fixture.destinations.uiState.destinations.isEmpty() }
        compose.runOnIdle {
            fixture.requestId.value++
            fixture.open.value = true
        }
        compose.onNodeWithText(DisplayAddress).assertIsDisplayed()
        compose.onNodeWithText(Favorite.name).assertDoesNotExist()
        compose.runOnIdle { assertEquals(originalRequest, fixture.editor.createScheduleRequest()) }
    }

    @Test
    fun `지도 진입 후 목록 조회가 완료되어도 삭제된 별명 대신 주소를 표시한다`() =
        withMap(initialFavorites = null) { fixture ->
            compose.onNodeWithText(Favorite.name).assertIsDisplayed()
            compose.runOnIdle { fixture.repository.items.value = emptyList() }
            compose.onNodeWithText(DisplayAddress).assertIsDisplayed()
            compose.onNodeWithText(Favorite.name).assertDoesNotExist()
        }

    @Test
    fun `목록 갱신으로 표시 이름이 바뀌어도 열려 있는 검색창과 입력은 유지한다`() = withMap { fixture ->
        compose.onNodeWithContentDescription("검색").performClick()
        compose.onNodeWithContentDescription("장소 또는 주소 검색").performTextInput("강남")
        compose.runOnIdle { fixture.repository.items.value = emptyList() }
        compose.onNodeWithContentDescription("장소 또는 주소 검색").assertTextContains("강남")
        compose.onNodeWithContentDescription("뒤로가기").performClick()
        compose.onNodeWithText(DisplayAddress).assertIsDisplayed()
        compose.onNodeWithText(Favorite.name).assertDoesNotExist()
    }

    @Test
    fun `설정된 알람 목적지가 없으면 기본 지도 위치의 이름을 그대로 표시한다`() =
        withMap(initialFavorites = emptyList(), hasAlarmDestination = false) {
            compose.onNodeWithText(DefaultDestinationSelection.name).assertIsDisplayed()
        }

    @Test
    fun `현재 위치로 진입하면 삭제된 알람 목적지의 표시 규칙을 적용하지 않는다`() =
        withMap(initialFavorites = emptyList(), hasCurrentLocation = true) {
            compose.onNodeWithText(SelectedDestinationFallbackName).assertIsDisplayed()
            compose.onNodeWithText(Favorite.name).assertDoesNotExist()
            compose.onNodeWithText(DisplayAddress).assertDoesNotExist()
        }

    private fun withMap(
        initialFavorites: List<SavedDestination>? = listOf(Favorite),
        hasAlarmDestination: Boolean = true,
        hasCurrentLocation: Boolean = false,
        verify: (Fixture) -> Unit,
    ) {
        val fixture = Fixture(initialFavorites, hasAlarmDestination, hasCurrentLocation)
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        try {
            activity.get().setContent {
                // 실제 지도와 위치 권한 요청만 생략하고 Route의 상태 연결을 검증한다.
                CompositionLocalProvider(LocalInspectionMode provides true) {
                    RingoutTheme {
                        if (fixture.open.value) {
                            DestinationRoute(
                                viewModel = fixture.destinations,
                                initialSelection = fixture.editor.uiState.destination,
                                requestId = fixture.requestId.value,
                                authSessionState = AuthSessionState.Unauthenticated,
                                productAnalyticsRecorder = fixture.analytics,
                                isActive = true,
                                onBackClick = { fixture.open.value = false },
                                onSavedDestinationConfirmClick = {},
                                onConfirmWithoutSave = {},
                            )
                        }
                    }
                }
            }
            verify(fixture)
        } finally {
            activity.pause().stop().destroy()
            fixture.scope.cancel()
        }
    }

    private class Fixture(
        items: List<SavedDestination>?,
        hasAlarmDestination: Boolean,
        hasCurrentLocation: Boolean,
    ) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val repository = MapDestinationRepository(items)
        val analytics = DefaultProductAnalyticsRecorder(
            AnalyticsTracker {},
            object : ProductAnalyticsUsageStore {
                override fun claimOnboardingEvent(eventName: AnalyticsEventName) = false
                override fun claimDestinationCreation(destinationKey: String): Long? = null
            },
        )
        val editor = AlarmSetupViewModel().apply {
            startCreating("07:00")
            if (hasAlarmDestination) {
                updateDestination(Favorite.toDestinationSelection())
                startEditing(requireNotNull(createScheduleRequest()).copy(id = "existing-alarm"))
            }
        }
        val destinations = DestinationViewModel(repository, analytics, scope).apply {
            if (hasCurrentLocation) onCurrentLocationResolved(37.57, 126.98)
        }
        val open = mutableStateOf(true)
        val requestId = mutableStateOf(1L)
    }
}

private class MapDestinationRepository(initial: List<SavedDestination>?) : DestinationRepository {
    val items = MutableStateFlow(initial)
    override fun observeAll() = items.filterNotNull()
    override suspend fun fetchAll() = observeAll().first()
    override suspend fun sync() = fetchAll()
    override suspend fun delete(id: Long): Boolean {
        val current = items.value ?: return false
        items.value = current.filterNot { it.id == id }
        return current.any { it.id == id }
    }
    override suspend fun save(destination: SavedDestination): SavedDestination = error("저장 호출은 예상하지 않음")
    override suspend fun updateName(id: Long, name: String): Boolean = error("별명 수정 호출은 예상하지 않음")
}

private const val DisplayAddress = "서울 서초구 반포동 748-10"
private val Favorite = SavedDestination(1, "헬스장", "대한민국 $DisplayAddress", 37.5, 127.0)
