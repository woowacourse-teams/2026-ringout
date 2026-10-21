package com.joon.ringout.presentation.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.joon.ringout.alarm.ActiveAlarmMission
import com.joon.ringout.presentation.assertTextFits
import com.joon.ringout.presentation.launchWithFontScale
import com.joon.ringout.presentation.home.components.ActiveAlarmMissionRow
import com.joon.ringout.presentation.home.components.AlarmListHeader
import com.joon.ringout.presentation.home.components.AlarmRow
import com.joon.ringout.presentation.home.model.HomeAlarm
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w320dp-h480dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeLargeTextTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test
    fun `큰 글자에서 알람 정보가 잘리지 않고 토글과 삭제가 각각 동작한다`() {
        val alarm = HomeAlarm(
            id = "1", time = "23:59", days = "월, 화, 수, 목, 금, 토, 일",
            destination = "서울특별시 서초구 반포대로 58", timeLimitMinutes = 30, isEnabled = true,
        )
        var toggled = false
        var deleted = false
        var edited = false
        launchWithFontScale(2f) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp)) {
                AlarmRow(alarm, onClick = { edited = true }, onEnabledChange = { toggled = true }, onDelete = { deleted = true })
            }
        }.use {
            listOf(alarm.days, "오후", "11:59", "${alarm.destination}으로 이동", "30분 간격으로 알람이 울려요").forEach {
                compose.onNodeWithText(it, useUnmergedTree = true).assertTextFits()
            }
            val toggle = compose.onNodeWithContentDescription("11:59 알람")
            toggle.performScrollTo().assertIsDisplayed().performClick()
            compose.onNodeWithContentDescription("알람 삭제").performScrollTo().assertIsDisplayed().performClick()
            compose.runOnIdle {
                assertTrue(toggled)
                assertTrue(deleted)
                assertEquals(false, edited)
            }
        }
    }

    @Test
    fun `큰 글자에서 다음 알람 안내와 진행 중인 알람의 목적지 및 남은 시간을 전부 읽는다`() {
        val description = "7시간 20분 후 알람이 울려요."
        val destination = "서울특별시 서초구 반포대로 58"
        launchWithFontScale(2f) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp)) {
                AlarmListHeader(description)
                ActiveAlarmMissionRow(ActiveAlarmMission("1", destination, 30, 0L), 29 * 60 + 59)
            }
        }.use {
            listOf("알람", description, "목적지", destination, "다음 알람까지", "29:59").forEach {
                compose.onNodeWithText(it, useUnmergedTree = true).assertTextFits()
            }
            compose.onNodeWithText("29:59", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun `기본 글자에서는 알람 시각과 토글의 가로 배치를 유지한다`() {
        launchWithFontScale(1f) {
            Column(Modifier.padding(20.dp)) {
                AlarmRow(HomeAlarm("1", "06:20", "주말", "헬스장", 12, true), {}, {}, {})
            }
        }.use {
            val time = compose.onNodeWithText("06:20", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val toggle = compose.onNodeWithContentDescription("06:20 알람").fetchSemanticsNode().boundsInRoot
            assertTrue(toggle.left >= time.right)
            assertTrue(toggle.top < time.bottom && toggle.bottom > time.top)
        }
    }
}
