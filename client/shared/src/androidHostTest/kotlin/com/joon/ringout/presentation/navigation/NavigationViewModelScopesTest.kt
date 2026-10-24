package com.joon.ringout.presentation.navigation

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.viewModelScope
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import com.joon.ringout.RingoutTheme
import com.joon.ringout.alarm.AlarmController
import com.joon.ringout.alarm.SavedAlarmSchedule
import com.joon.ringout.analytics.ProductAnalyticsRecorder
import com.joon.ringout.di.AppContainer
import com.joon.ringout.domain.alarmactivity.AlarmActivityRepository
import com.joon.ringout.domain.auth.AuthRepository
import com.joon.ringout.domain.auth.AuthSession
import com.joon.ringout.domain.connectivity.NetworkMonitor
import com.joon.ringout.domain.destination.DestinationRepository
import com.joon.ringout.domain.destination.SavedDestination
import com.joon.ringout.domain.member.MemberRepository
import com.joon.ringout.domain.missionhistory.MissionHistoryRepository
import com.joon.ringout.domain.preferences.AppPreferencesRepository
import com.joon.ringout.domain.preferences.SystemThemeModeReader
import com.joon.ringout.domain.push.PushTokenRegistrar
import com.joon.ringout.domain.push.PushTokenRegistrationReadiness
import com.joon.ringout.domain.push.PushTokenSource
import com.joon.ringout.domain.room.RoomRepository
import com.joon.ringout.domain.terms.TermsRepository
import com.joon.ringout.presentation.alarmsetup.AlarmSetupViewModel
import com.joon.ringout.presentation.destination.DestinationCurrentLocationCache
import com.joon.ringout.presentation.home.HomeAlarmBinding
import com.joon.ringout.presentation.home.HomeRoute
import com.joon.ringout.presentation.home.HomeViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w402dp-h941dp")
class NavigationViewModelScopesTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test
    fun `인텐트 부가정보가 있어도 탭과 알람 편집 화면을 왕복하면 홈 모델과 조회 구독이 유지된다`() {
        verifyNavigationRoundTrips(withExtras = true)
    }

    @Test
    fun `인텐트 부가정보가 없어도 탭과 알람 편집 화면을 왕복하면 홈 모델과 조회 구독이 유지된다`() {
        verifyNavigationRoundTrips(withExtras = false)
    }

    @Test
    fun `인텐트 부가정보가 있는 앱을 백그라운드에서 복귀해도 완료된 홈 로딩과 구독이 유지된다`() {
        val fixture = Fixture()
        withActivity(fixture, withExtras = true) { controller ->
            val original = fixture.home
            repeat(3) {
                compose.runOnIdle { controller.pause().stop() }
                compose.runOnIdle { controller.start().resume().visible() }
                advanceFrames()

                assertHomeReady(fixture, original)
            }
        }
        assertEquals(1, fixture.alarmCompletions)
    }

    @Test
    fun `알람 편집 경로를 제거하면 해당 모델만 정리하고 재진입할 때 새 모델을 만든다`() {
        val fixture = Fixture()
        lateinit var homeJob: Job
        withActivity(fixture, withExtras = true) {
            val originalHome = fixture.home
            homeJob = checkNotNull(originalHome.viewModelScope.coroutineContext[Job])
            compose.runOnIdle { fixture.navigation.navigate(AppRoute.AddAlarm) }
            advanceFrames()
            val originalEditor = checkNotNull(fixture.editor)
            val editorJob = checkNotNull(originalEditor.viewModelScope.coroutineContext[Job])

            compose.runOnIdle { fixture.navigation.popBackStack() }
            advanceFrames()

            assertHomeReady(fixture, originalHome)
            assertTrue(editorJob.isCancelled)
            assertFalse(homeJob.isCancelled)

            compose.runOnIdle { fixture.navigation.navigate(AppRoute.AddAlarm) }
            advanceFrames()

            assertNotSame(originalEditor, fixture.editor)
            assertSame(originalHome, fixture.home)
        }
        assertTrue(homeJob.isCancelled)
        assertEquals(1, fixture.alarmCompletions)
    }

    private fun verifyNavigationRoundTrips(withExtras: Boolean) {
        val fixture = Fixture()
        withActivity(fixture, withExtras) {
            val original = fixture.home
            repeat(3) {
                compose.onNodeWithContentDescription("소셜").performClick()
                advanceFrames()
                compose.onNodeWithTag("social").assertTextEquals("social")
                compose.onNodeWithContentDescription("홈").performClick()
                advanceFrames()
                assertHomeReady(fixture, original)

                compose.runOnIdle { fixture.navigation.navigate(AppRoute.AddAlarm) }
                advanceFrames()
                compose.onNodeWithTag("editor").assertTextEquals("editor")
                compose.runOnIdle { fixture.navigation.popBackStack() }
                advanceFrames()
                assertHomeReady(fixture, original)
            }
        }
        assertEquals(1, fixture.alarmCompletions)
    }

    private fun assertHomeReady(fixture: Fixture, original: HomeViewModel) {
        compose.onNodeWithTag("home-loading").assertTextEquals("loading=false")
        compose.runOnIdle {
            assertSame(original, fixture.home)
            assertSame(original, fixture.displayedHome)
            assertFalse(original.uiState.isLoading)
            assertEquals(1, fixture.container.destinationRepository.observations)
            assertEquals(1, fixture.alarmSubscriptions)
            assertEquals(0, fixture.alarmCompletions)
        }
    }

    private fun withActivity(
        fixture: Fixture,
        withExtras: Boolean,
        block: (ActivityController<ComponentActivity>) -> Unit,
    ) {
        // 결함이 재발해 매 프레임 모델을 재생성하더라도 자동 idle 대기 대신 유한 프레임 후 실패한다.
        compose.mainClock.autoAdvance = false
        val controller = Robolectric.buildActivity(ComponentActivity::class.java)
        controller.get().setTheme(android.R.style.Theme_Material_NoActionBar)
        if (withExtras) controller.get().intent = Intent().putExtra("navigation-test", "present")
        controller.setup()
        controller.get().setContent {
            RingoutTheme {
                CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides fixture) {
                    DisposableEffect(fixture) {
                        onDispose { fixture.navigationEventDispatcher.dispose() }
                    }
                    NavigationContent(fixture)
                }
            }
        }
        try {
            advanceFrames()
            assertHomeReady(fixture, fixture.home)
            block(controller)
        } finally {
            controller.pause().stop().destroy()
            compose.waitForIdle()
            compose.mainClock.autoAdvance = true
        }
    }

    private fun advanceFrames() {
        // NavDisplay의 전환과 snapshot 알림은 프레임 사이 UI dispatcher에서 이어진다.
        repeat(32) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
    }

    @Composable
    private fun NavigationContent(fixture: Fixture) {
        val navigation = fixture.navigation
        val route = navigation.requestedRoute
        val scopes = rememberNavigationViewModelScopes(
            appContainer = fixture.container,
            retainedRoutes = navigation.retainedRoutes(route),
            destinationCurrentLocationCache = fixture.locationCache,
        )
        val home = scopes.get(AppRoute.Home, HomeViewModel::class)
        // 실제 App처럼 스코프를 얻는 부모 composition에서 홈 상태를 읽는다.
        val loading = home.uiState.isLoading
        SideEffect { fixture.home = home }
        HomeAlarmBinding(fixture.alarmController, home)
        Column {
            Text("loading=$loading", Modifier.testTag("home-loading"))
            RingoutNavHost(
                navigationState = navigation,
                displayedRoute = route,
                viewModelStoreProvider = scopes.storeProvider,
                graph = {
                    entry<AppRoute.Home>(clazzContentKey = AppRoute::viewModelStoreKey) {
                        SideEffect { fixture.displayedHome = home }
                        HomeRoute(
                            viewModel = home,
                            alarmController = fixture.alarmController,
                            activeAlarmMission = null,
                            onAddAlarm = { navigation.navigate(AppRoute.AddAlarm) },
                            onEditAlarm = {},
                            onActiveAlarmMissionClick = {},
                            onActiveAlarmMissionExpired = {},
                        )
                    }
                    entry<AppRoute.Social>(clazzContentKey = AppRoute::viewModelStoreKey) {
                        Text("social", Modifier.testTag("social"))
                    }
                    entry<AppRoute.AddAlarm>(clazzContentKey = AppRoute::viewModelStoreKey) {
                        val editor = scopes.get(AppRoute.AddAlarm, AlarmSetupViewModel::class)
                        SideEffect { fixture.editor = editor }
                        Text("editor", Modifier.testTag("editor"))
                    }
                },
            )
        }
    }

    private class Fixture : NavigationEventDispatcherOwner {
        override val navigationEventDispatcher = NavigationEventDispatcher()
        val navigation = AppNavigationState()
        val container = HomeTestContainer()
        val locationCache = DestinationCurrentLocationCache()
        lateinit var home: HomeViewModel
        var displayedHome: HomeViewModel? = null
        var editor: AlarmSetupViewModel? = null
        var alarmSubscriptions = 0
        var alarmCompletions = 0
        val alarmController = AlarmController(
            schedule = { _, _ -> },
            setEnabled = { _, _ -> },
            deleteAlarm = {},
            savedAlarms = MutableStateFlow<List<SavedAlarmSchedule>>(emptyList())
                .onStart { alarmSubscriptions++ }
                .onCompletion { alarmCompletions++ },
        )
    }
}

private class HomeTestContainer : AppContainer {
    override val destinationRepository = HomeTestDestinationRepository()
    override val termsRepository: TermsRepository get() = error("Unused dependency")
    override val networkMonitor: NetworkMonitor get() = error("Unused dependency")
    override val appPreferencesRepository: AppPreferencesRepository get() = error("Unused dependency")
    override val systemThemeModeReader: SystemThemeModeReader get() = error("Unused dependency")
    override val authSession: AuthSession get() = error("Unused dependency")
    override val pushTokenSource: PushTokenSource get() = error("Unused dependency")
    override val pushTokenRegistrationReadiness: PushTokenRegistrationReadiness get() = error("Unused dependency")
    override val pushTokenRegistrar: PushTokenRegistrar get() = error("Unused dependency")
    override val authRepository: AuthRepository get() = error("Unused dependency")
    override val memberRepository: MemberRepository get() = error("Unused dependency")
    override val roomRepository: RoomRepository get() = error("Unused dependency")
    override val alarmActivityRepository: AlarmActivityRepository get() = error("Unused dependency")
    override val missionHistoryRepository: MissionHistoryRepository get() = error("Unused dependency")
    override val productAnalyticsRecorder: ProductAnalyticsRecorder get() = error("Unused dependency")
}

private class HomeTestDestinationRepository : DestinationRepository {
    var observations = 0
        private set

    override fun observeAll(): Flow<List<SavedDestination>> {
        observations++
        return MutableStateFlow(emptyList())
    }

    override suspend fun fetchAll(): List<SavedDestination> = error("Unused dependency")
    override suspend fun sync(): List<SavedDestination> = error("Unused dependency")
    override suspend fun save(destination: SavedDestination): SavedDestination = error("Unused dependency")
    override suspend fun updateName(id: Long, name: String): Boolean = error("Unused dependency")
    override suspend fun delete(id: Long): Boolean = error("Unused dependency")
}
