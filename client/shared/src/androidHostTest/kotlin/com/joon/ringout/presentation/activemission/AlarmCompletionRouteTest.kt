package com.joon.ringout.presentation.activemission

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.joon.ringout.RingoutTheme
import com.joon.ringout.alarm.ActiveAlarmMission
import com.joon.ringout.alarm.ForceEndMissionResult
import kotlinx.coroutines.CompletableDeferred
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AlarmCompletionRouteTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Before
    fun reset() {
        RestrictedAlarmTestActivity.mission.value = null
        RestrictedAlarmTestActivity.pending.value = false
        RestrictedAlarmTestActivity.result = CompletableDeferred()
        RestrictedAlarmTestActivity.stopCalls = 0
        RestrictedAlarmTestActivity.backgroundCalls = 0
    }

    @Test
    fun `추적 미션은 완료까지 사용할 수 있고 뒤로가기로 일반 화면을 열 수 없다`() {
        RestrictedAlarmTestActivity.mission.value = ActiveAlarmMission(
            alarmId = "alarm", occurrenceId = "mission", destinationName = "학교",
            limitMinutes = 12, expiresAtEpochMillis = System.currentTimeMillis() + 720_000,
        )
        RestrictedAlarmTestActivity.pending.value = true
        val controller = Robolectric.buildActivity(RestrictedAlarmTestActivity::class.java).setup()
        try {
            compose.onNodeWithText("업데이트 차단").assertDoesNotExist()
            compose.runOnIdle { controller.get().onBackPressedDispatcher.onBackPressed() }
            assertEquals(1, RestrictedAlarmTestActivity.backgroundCalls)
            compose.onNodeWithText("업데이트 차단").assertDoesNotExist()

            compose.runOnIdle {
                RestrictedAlarmTestActivity.mission.value = null
                RestrictedAlarmTestActivity.pending.value = false
            }
            compose.onNodeWithText("업데이트 차단").assertIsDisplayed()
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun `재울림 준비 중에는 차단 안내를 미루고 울림이 끝나면 차단한다`() {
        RestrictedAlarmTestActivity.pending.value = true
        val controller = Robolectric.buildActivity(RestrictedAlarmTestActivity::class.java).setup()
        try {
            compose.onNodeWithText("업데이트 차단").assertDoesNotExist()
            compose.onNodeWithText("진행 중인 알람과 미션을 마친 뒤\n앱을 업데이트해 주세요.").assertIsDisplayed()
            compose.runOnIdle { RestrictedAlarmTestActivity.pending.value = false }
            compose.onNodeWithText("업데이트 차단").assertIsDisplayed()
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun `종료 요청과 완료 확인은 미션 삭제와 화면 재생성 이후에도 차단 안내보다 우선한다`() {
        RestrictedAlarmTestActivity.pending.value = true
        val controller = Robolectric.buildActivity(RestrictedAlarmTestActivity::class.java).setup()
        try {
            compose.runOnIdle {
                controller.get().model.request("mission", RestrictedAlarmTestActivity::stop)
                RestrictedAlarmTestActivity.pending.value = false
            }
            compose.onNodeWithText("미션을 종료하고 있어요").assertIsDisplayed()
            compose.onNodeWithText("업데이트 차단").assertDoesNotExist()
            controller.recreate()
            compose.onNodeWithText("미션을 종료하고 있어요").assertIsDisplayed()
            compose.runOnIdle { RestrictedAlarmTestActivity.result.complete(ForceEndMissionResult.Completed) }
            compose.onNodeWithText("알람이 강제 종료되었습니다.").assertIsDisplayed()
            compose.onNodeWithText("업데이트 차단").assertDoesNotExist()
            controller.recreate()
            compose.onNodeWithText("확인").performClick()
            compose.onNodeWithText("업데이트 차단").assertIsDisplayed()
            assertEquals(1, RestrictedAlarmTestActivity.stopCalls)
        } finally {
            controller.pause().stop().destroy()
        }
    }
}

class RestrictedAlarmTestActivity : ComponentActivity() {
    internal lateinit var model: ForceEndMissionViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            model = viewModel { ForceEndMissionViewModel(createSavedStateHandle()) }
            val activeMission by mission.collectAsState()
            val hasPending by pending.collectAsState()
            RingoutTheme {
                AlarmCompletionRoute(
                    activeMission = activeMission,
                    currentLocation = null,
                    hasPendingAlarm = hasPending,
                    onBackground = { backgroundCalls++ },
                    onExpired = {},
                    onForceEnd = ::stop,
                    onForceEndHoldStarted = {},
                    onForceEndHoldCancelled = { _, _ -> },
                    onForceEndHoldCompleted = { _, _ -> },
                    onReturnToRinging = null,
                ) { Text("업데이트 차단") }
            }
        }
    }

    companion object {
        val mission = MutableStateFlow<ActiveAlarmMission?>(null)
        val pending = MutableStateFlow(false)
        lateinit var result: CompletableDeferred<ForceEndMissionResult>
        var stopCalls = 0
        var backgroundCalls = 0

        suspend fun stop(id: String): ForceEndMissionResult {
            check(id == "mission")
            stopCalls++
            return result.await()
        }
    }
}
