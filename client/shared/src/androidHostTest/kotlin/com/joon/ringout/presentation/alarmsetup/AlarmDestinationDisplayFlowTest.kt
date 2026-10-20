package com.joon.ringout.presentation.alarmsetup

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import com.joon.ringout.domain.destination.SavedDestination
import com.joon.ringout.presentation.home.HomeScreen
import com.joon.ringout.presentation.home.HomeViewModel
import kotlinx.coroutines.flow.MutableStateFlow
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
    fun `편집 중 즐겨찾기를 삭제하면 주소로 바뀌고 저장 없이 홈으로 돌아와도 주소를 표시한다`() {
        val favorite = SavedDestination(1, "헬스장", "서울 서초구 반포동 748-10", 37.5, 127.0)
        val request = AlarmScheduleRequest(
            id = "alarm-1", time = "07:00", selectedDays = listOf("월"), repeatEnabled = true,
            limitMinutes = 5, destinationName = favorite.name, destinationAddress = favorite.address,
            destinationLatitude = favorite.latitude, destinationLongitude = favorite.longitude,
            alarmSoundName = "기본 알람음", alarmSoundUri = null,
        )
        val favorites = MutableStateFlow(listOf(favorite))
        val home = HomeViewModel(favorites)
        val editor = AlarmSetupViewModel()
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        try {
            activity.get().setContent {
                var editing by remember { mutableStateOf(false) }
                val saved by favorites.collectAsState()
                LaunchedEffect(Unit) { home.observeAlarms(flowOf(listOf(SavedAlarmSchedule(request, true)))) }
                RingoutTheme {
                    if (editing) {
                        AlarmSetupRoute(
                            viewModel = editor, isActive = true, onBackClick = { editing = false },
                            onDestinationClick = {}, onAlarmSoundClick = {}, savedDestinations = saved,
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
                home.uiState.alarms.firstOrNull()?.destinationDisplayName == favorite.name
            }
            compose.onNodeWithText("헬스장으로 이동").assertIsDisplayed().performClick()
            compose.onNodeWithText("헬스장").assertIsDisplayed()
            compose.runOnIdle { favorites.value = emptyList() }
            compose.waitUntil(5_000) {
                home.uiState.alarms.firstOrNull()?.destinationDisplayName == favorite.address
            }
            compose.onNodeWithText(favorite.address).assertIsDisplayed()
            compose.onNodeWithText("헬스장").assertDoesNotExist()
            compose.onNodeWithContentDescription("뒤로").performClick()
            compose.onNodeWithText("${favorite.address}으로 이동").assertIsDisplayed()
            compose.onNodeWithText("헬스장", substring = true).assertDoesNotExist()
            compose.runOnIdle {
                assertEquals(request, home.alarmScheduleRequest(request.id))
                assertEquals(request, editor.createScheduleRequest())
            }
            // 재진입도 원본 별명 대신 현재 즐겨찾기 상태를 기준으로 표시한다.
            compose.onNodeWithText("${favorite.address}으로 이동").performClick()
            compose.onNodeWithText(favorite.address).assertIsDisplayed()
        } finally {
            activity.pause().stop().destroy()
        }
    }
}
