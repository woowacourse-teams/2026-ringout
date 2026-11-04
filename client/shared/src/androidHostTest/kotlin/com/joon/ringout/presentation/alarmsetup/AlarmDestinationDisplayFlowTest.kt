package com.joon.ringout.presentation.alarmsetup

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.joon.ringout.RingoutTheme
import com.joon.ringout.alarm.AlarmScheduleRequest
import com.joon.ringout.alarm.SavedAlarmSchedule
import com.joon.ringout.presentation.home.HomeScreen
import com.joon.ringout.presentation.home.HomeViewModel
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w402dp-h1400dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AlarmDestinationDisplayFlowTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test
    fun `저장 목적지 목록이 없어도 알람에 복사한 라벨을 홈과 편집 화면에서 표시한다`() {
        val request = AlarmScheduleRequest(
            id = "alarm-1", time = "07:00", selectedDays = listOf("월"), repeatEnabled = true,
            limitMinutes = 5, destinationLabel = "헬스장",
            destinationLatitude = 37.5, destinationLongitude = 127.0,
            alarmSoundName = "기본 알람음", alarmSoundUri = null,
        )
        val home = HomeViewModel()
        val editor = AlarmSetupViewModel()
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        try {
            activity.get().setContent {
                var editing by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { home.observeAlarms(flowOf(listOf(SavedAlarmSchedule(request, true)))) }
                RingoutTheme {
                    if (editing) {
                        AlarmSetupRoute(
                            viewModel = editor, isActive = true, onBackClick = { editing = false },
                            onDestinationClick = {}, onAlarmSoundClick = {},
                        )
                    } else {
                        HomeScreen(
                            uiState = home.uiState, onAddAlarm = {},
                            onAlarmClick = { id ->
                                editor.startEditing(requireNotNull(home.alarmScheduleRequest(id)))
                                editing = true
                            },
                            onAlarmEnabledChange = { _, _ -> }, onAlarmDelete = {},
                        )
                    }
                }
            }
            compose.waitUntil(5_000) {
                home.uiState.alarms.firstOrNull()?.destinationDisplayName == request.destinationLabel
            }
            compose.onNodeWithText("헬스장으로 이동").assertIsDisplayed().performClick()
            compose.onNodeWithText("헬스장").assertIsDisplayed()
            compose.onNodeWithContentDescription("뒤로").performClick()
            compose.onNodeWithText("헬스장으로 이동").assertIsDisplayed()
            compose.runOnIdle {
                assertEquals(request, home.alarmScheduleRequest(request.id))
                assertEquals(request, editor.createScheduleRequest())
            }
            compose.onNodeWithText("헬스장으로 이동").performClick()
            compose.onNodeWithText("헬스장").assertIsDisplayed()
        } finally {
            activity.pause().stop().destroy()
        }
    }
}
