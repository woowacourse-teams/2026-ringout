package com.joon.ringout.presentation.navigation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.viewModelScope
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import com.joon.ringout.AppStateHost
import com.joon.ringout.RingoutTheme
import com.joon.ringout.presentation.alarmsetup.AlarmSetupViewModel
import com.joon.ringout.presentation.destination.DestinationCurrentLocationCache
import com.joon.ringout.presentation.home.HomeViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppStateHostTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Before
    fun reset() {
        RetainedAppStateTestActivity.isAppAllowed.value = true
        RetainedAppStateTestActivity.container = HomeTestContainer()
    }

    @Test
    fun `실시간 차단 후 복귀하면 편집 내용을 유지하고 실제 뒤로가기를 한 경우에만 모델을 정리한다`() {
        val controller = Robolectric.buildActivity(RetainedAppStateTestActivity::class.java).setup()
        try {
            compose.runOnIdle { controller.get().navigation.navigate(AppRoute.AddAlarm) }
            compose.onNodeWithText("편집").assertIsDisplayed()
            val editor = controller.get().editor
            val editorJob = checkNotNull(editor.viewModelScope.coroutineContext[Job])
            compose.runOnIdle { editor.startCreating("07:25") }
            compose.onNodeWithText("07:25").assertIsDisplayed()

            compose.runOnIdle { RetainedAppStateTestActivity.isAppAllowed.value = false }
            compose.onNodeWithText("업데이트 차단").assertIsDisplayed()
            compose.onNodeWithText("편집").assertDoesNotExist()
            assertFalse(editorJob.isCancelled)

            compose.runOnIdle { RetainedAppStateTestActivity.isAppAllowed.value = true }
            compose.onNodeWithText("07:25").assertIsDisplayed()
            assertSame(editor, controller.get().editor)

            compose.runOnIdle { controller.get().navigation.popBackStack() }
            compose.onNodeWithText("테스트 홈").assertIsDisplayed()
            assertTrue(editorJob.isCancelled)
            compose.runOnIdle { controller.get().navigation.navigate(AppRoute.AddAlarm) }
            compose.onNodeWithText("편집").assertIsDisplayed()
            assertNotSame(editor, controller.get().editor)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun `차단 중 액티비티를 재생성해도 백스택과 편집 모델을 유지한다`() {
        val controller = Robolectric.buildActivity(RetainedAppStateTestActivity::class.java).setup()
        lateinit var editorJob: Job
        try {
            compose.runOnIdle { controller.get().navigation.navigate(AppRoute.AddAlarm) }
            compose.onNodeWithText("편집").assertIsDisplayed()
            val editor = controller.get().editor
            editorJob = checkNotNull(editor.viewModelScope.coroutineContext[Job])
            compose.runOnIdle {
                editor.startCreating("08:42")
                RetainedAppStateTestActivity.isAppAllowed.value = false
            }
            compose.onNodeWithText("업데이트 차단").assertIsDisplayed()

            controller.recreate()

            compose.onNodeWithText("업데이트 차단").assertIsDisplayed()
            assertFalse(editorJob.isCancelled)
            compose.runOnIdle { RetainedAppStateTestActivity.isAppAllowed.value = true }
            compose.onNodeWithText("08:42").assertIsDisplayed()
            assertSame(editor, controller.get().editor)
            assertEquals(AppRoute.AddAlarm, controller.get().navigation.requestedRoute)
        } finally {
            controller.pause().stop().destroy()
        }
        assertTrue(editorJob.isCancelled)
    }

    @Test
    fun `처음부터 차단된 앱에서는 일반 화면과 모델을 만들지 않는다`() {
        RetainedAppStateTestActivity.isAppAllowed.value = false
        val controller = Robolectric.buildActivity(RetainedAppStateTestActivity::class.java).setup()
        try {
            compose.onNodeWithText("업데이트 차단").assertIsDisplayed()
            assertEquals(0, RetainedAppStateTestActivity.container.destinationRepository.observations)
            compose.onNodeWithText("테스트 홈").assertDoesNotExist()
            compose.runOnIdle { RetainedAppStateTestActivity.isAppAllowed.value = true }
            compose.onNodeWithText("테스트 홈").assertIsDisplayed()
            assertEquals(1, RetainedAppStateTestActivity.container.destinationRepository.observations)
        } finally {
            controller.pause().stop().destroy()
        }
    }
}

class RetainedAppStateTestActivity : ComponentActivity() {
    internal lateinit var navigation: AppNavigationState
    internal lateinit var editor: AlarmSetupViewModel
    private val locationCache = DestinationCurrentLocationCache()
    private val dispatcherOwner = object : NavigationEventDispatcherOwner {
        override val navigationEventDispatcher = NavigationEventDispatcher()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val allowed by isAppAllowed.collectAsState()
            val stateHolder = rememberSaveableStateHolder()
            RingoutTheme {
                CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides dispatcherOwner) {
                    AppStateHost {
                        if (allowed) {
                            stateHolder.SaveableStateProvider("app") { NavigationContent() }
                        } else {
                            Text("업데이트 차단")
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun NavigationContent() {
        val state = rememberAppNavigationState()
        SideEffect { navigation = state }
        val scopes = rememberNavigationViewModelScopes(
            appContainer = container,
            retainedRoutes = state.retainedRoutes(state.requestedRoute),
            destinationCurrentLocationCache = locationCache,
        )
        RingoutNavHost(
            navigationState = state,
            displayedRoute = state.requestedRoute,
            viewModelStoreProvider = scopes.storeProvider,
            graph = {
                entry<AppRoute.Home>(clazzContentKey = AppRoute::viewModelStoreKey) {
                    scopes.get(AppRoute.Home, HomeViewModel::class)
                    Text("테스트 홈")
                }
                entry<AppRoute.AddAlarm>(clazzContentKey = AppRoute::viewModelStoreKey) {
                    val model = scopes.get(AppRoute.AddAlarm, AlarmSetupViewModel::class)
                    SideEffect { editor = model }
                    Column {
                        Text("편집")
                        Text(model.uiState.time)
                    }
                }
            },
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        dispatcherOwner.navigationEventDispatcher.dispose()
    }

    companion object {
        internal val isAppAllowed = MutableStateFlow(true)
        internal lateinit var container: HomeTestContainer
    }
}
