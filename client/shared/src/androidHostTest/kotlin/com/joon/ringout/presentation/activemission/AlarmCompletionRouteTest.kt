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
        RestrictedAlarmTestActivity.stoppedIds.clear()
        RestrictedAlarmTestActivity.backgroundCalls = 0
    }

    @Test
    fun `종료하기를 네 번 누를 때까지 요청하지 않고 다섯 번째 입력에서 한 번 종료한다`() {
        RestrictedAlarmTestActivity.mission.value = mission("mission")
        val controller = Robolectric.buildActivity(RestrictedAlarmTestActivity::class.java).setup()
        try {
            compose.onNodeWithText("강제 종료").performClick()
            val titles = listOf("종료하실 건가요?", "진짜요?", "정말??", "진짜 정말로??", "그래요...")
            compose.onNodeWithText(titles.first()).assertIsDisplayed()

            titles.drop(1).forEach { title ->
                compose.onNodeWithText("종료하기").performClick()
                compose.onNodeWithText(title).assertIsDisplayed()
                assertEquals(0, RestrictedAlarmTestActivity.stopCalls)
            }

            compose.onNodeWithText("종료하기").performClick()
            compose.onNodeWithText("미션을 종료하고 있어요").assertIsDisplayed()
            compose.onNodeWithText(titles.last()).assertDoesNotExist()
            assertEquals(1, RestrictedAlarmTestActivity.stopCalls)
            assertEquals(listOf("mission"), RestrictedAlarmTestActivity.stoppedIds)

            compose.runOnIdle {
                RestrictedAlarmTestActivity.result.complete(ForceEndMissionResult.Completed)
            }
            compose.onNodeWithText("알람이 강제 종료되었습니다.").assertIsDisplayed()
        } finally {
            if (!RestrictedAlarmTestActivity.result.isCompleted) {
                RestrictedAlarmTestActivity.result.complete(ForceEndMissionResult.Failed)
            }
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun `미션하기와 시스템 뒤로가기는 확인을 닫고 다음 진입을 첫 문구로 연다`() {
        RestrictedAlarmTestActivity.mission.value = mission("mission")
        val controller = Robolectric.buildActivity(RestrictedAlarmTestActivity::class.java).setup()
        try {
            compose.onNodeWithText("강제 종료").performClick()
            compose.onNodeWithText("종료하기").performClick()
            compose.onNodeWithText("진짜요?").assertIsDisplayed()
            compose.onNodeWithText("미션하기").performClick()
            compose.onNodeWithText("진짜요?").assertDoesNotExist()
            assertEquals(0, RestrictedAlarmTestActivity.stopCalls)

            compose.onNodeWithText("강제 종료").performClick()
            compose.onNodeWithText("종료하실 건가요?").assertIsDisplayed()
            compose.onNodeWithText("종료하기").performClick()
            compose.runOnIdle { controller.get().onBackPressedDispatcher.onBackPressed() }
            compose.onNodeWithText("진짜요?").assertDoesNotExist()
            assertEquals(0, RestrictedAlarmTestActivity.stopCalls)
            assertEquals(0, RestrictedAlarmTestActivity.backgroundCalls)

            compose.onNodeWithText("강제 종료").performClick()
            compose.onNodeWithText("종료하실 건가요?").assertIsDisplayed()
            controller.recreate()
            compose.onNodeWithText("종료하실 건가요?").assertDoesNotExist()
            compose.onNodeWithText("강제 종료").performClick()
            compose.onNodeWithText("종료하실 건가요?").assertIsDisplayed()
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun `미션 교체는 이전 확인 단계를 버리고 새 미션의 다섯 번째 입력만 종료한다`() {
        RestrictedAlarmTestActivity.mission.value = mission("mission")
        val controller = Robolectric.buildActivity(RestrictedAlarmTestActivity::class.java).setup()
        try {
            compose.onNodeWithText("강제 종료").performClick()
            compose.onNodeWithText("종료하기").performClick()
            compose.onNodeWithText("진짜요?").assertIsDisplayed()

            compose.runOnIdle {
                RestrictedAlarmTestActivity.mission.value = mission("mission-2")
            }
            compose.onNodeWithText("진짜요?").assertDoesNotExist()
            compose.onNodeWithText("강제 종료").performClick()
            compose.onNodeWithText("종료하실 건가요?").assertIsDisplayed()
            assertEquals(0, RestrictedAlarmTestActivity.stopCalls)

            repeat(4) {
                compose.onNodeWithText("종료하기").performClick()
                assertEquals(0, RestrictedAlarmTestActivity.stopCalls)
            }
            compose.onNodeWithText("종료하기").performClick()
            compose.onNodeWithText("미션을 종료하고 있어요").assertIsDisplayed()
            assertEquals(listOf("mission-2"), RestrictedAlarmTestActivity.stoppedIds)
        } finally {
            if (!RestrictedAlarmTestActivity.result.isCompleted) {
                RestrictedAlarmTestActivity.result.complete(ForceEndMissionResult.Failed)
            }
            controller.pause().stop().destroy()
        }
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
        val stoppedIds = mutableListOf<String>()
        var backgroundCalls = 0

        suspend fun stop(id: String): ForceEndMissionResult {
            check(id in setOf("mission", "mission-2"))
            stopCalls++
            stoppedIds += id
            return result.await()
        }
    }
}

private fun mission(occurrenceId: String) = ActiveAlarmMission(
    alarmId = "alarm",
    occurrenceId = occurrenceId,
    destinationName = "학교",
    limitMinutes = 12,
    expiresAtEpochMillis = System.currentTimeMillis() + 720_000,
)
