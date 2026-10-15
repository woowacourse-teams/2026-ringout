package com.joon.ringout.presentation.activemission

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.joon.ringout.RingoutTheme
import com.joon.ringout.alarm.ForceEndMissionResult
import com.joon.ringout.presentation.activemission.component.ForceEndMissionStatus
import com.joon.ringout.presentation.navigation.ActiveMissionNavigationEffect
import com.joon.ringout.presentation.navigation.rememberAppNavigationState
import com.joon.ringout.presentation.navigation.AppRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.CompletableDeferred
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ForceEndMissionRecreationTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test
    fun `처리 중과 완료 안내 중 액티비티를 재생성해도 종료는 한 번만 실행된다`() {
        ForceEndTestActivity.calls = 0
        ForceEndTestActivity.result = CompletableDeferred()
        ForceEndTestActivity.activeMission.value = "mission"
        val controller = Robolectric.buildActivity(ForceEndTestActivity::class.java).setup()
        try {
            compose.runOnIdle { controller.get().model.request("mission", ForceEndTestActivity::stop) }
            compose.onNodeWithText("미션을 종료하고 있어요").assertIsDisplayed()
            controller.recreate()
            compose.onNodeWithText("미션을 종료하고 있어요").assertIsDisplayed()
            assertEquals(1, ForceEndTestActivity.calls)
            compose.runOnIdle { ForceEndTestActivity.result.complete(ForceEndMissionResult.Completed) }
            compose.onNodeWithText("알람이 강제 종료되었습니다.").assertIsDisplayed()
            compose.onNodeWithText("홈 화면").assertExists()
            controller.recreate()
            compose.onNodeWithText("알람이 강제 종료되었습니다.").assertIsDisplayed()
            compose.onNodeWithText("확인").performClick()
            compose.onNodeWithText("알람이 강제 종료되었습니다.").assertDoesNotExist()
            assertEquals(1, ForceEndTestActivity.calls)
            assertEquals(ForceEndPhase.Idle, controller.get().model.uiState.value.phase)
        } finally {
            controller.pause().stop().destroy()
        }
    }
}

class ForceEndTestActivity : ComponentActivity() {
    internal lateinit var model: ForceEndMissionViewModel
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val vm = viewModel { ForceEndMissionViewModel(createSavedStateHandle()) }
            model = vm
            val state by vm.uiState.collectAsState()
            val activeId by activeMission.collectAsState()
            val navigation = rememberAppNavigationState()
            ActiveMissionNavigationEffect(activeId, navigation)
            LaunchedEffect(vm) { vm.resumePending(::stop) }
            RingoutTheme {
                Text(if (navigation.requestedRoute == AppRoute.Home) "홈 화면" else "미션 화면")
                ForceEndMissionStatus(state, vm::dismiss, { vm.retry(::stop) }, vm::dismiss)
            }
        }
    }
    companion object {
        var calls = 0
        val activeMission = MutableStateFlow<String?>("mission")
        lateinit var result: CompletableDeferred<ForceEndMissionResult>
        suspend fun stop(id: String): ForceEndMissionResult {
            check(id == "mission")
            calls++
            return result.await().also {
                if (it == ForceEndMissionResult.Completed) activeMission.value = null
            }
        }
    }
}
