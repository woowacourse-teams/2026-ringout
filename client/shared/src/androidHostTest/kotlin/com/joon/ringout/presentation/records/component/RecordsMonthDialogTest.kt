package com.joon.ringout.presentation.records.component

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import com.joon.ringout.RingoutTheme
import com.joon.ringout.domain.missionhistory.MissionDate
import com.joon.ringout.domain.missionhistory.MissionResult
import com.joon.ringout.domain.missionhistory.MissionYearMonth
import com.joon.ringout.domain.missionhistory.calendarDates
import com.joon.ringout.presentation.records.RecordsDayUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RecordsMonthDialogTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    @Test
    fun `4주 5주 6주 월과 로딩 성공 오류를 전환해도 다이얼로그와 월 버튼 위치가 유지된다`() {
        verifyStableBounds(fontScale = 1f)
    }

    @Test
    fun `큰 글자에서도 기록 결과와 월 변경에 따른 다이얼로그 위치 이동이 없다`() {
        verifyStableBounds(fontScale = 2f)
    }

    @Test
    @Config(qualifiers = "w320dp-h480dp")
    fun `작은 화면과 큰 글자에서도 마지막 날짜와 오류 재시도에 접근할 수 있다`() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        var selected: MissionDate? = null
        var retries = 0
        try {
            activity.get().setContent {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                    RingoutTheme {
                        RecordsMonthDialog(
                            month = MissionYearMonth(2026, 5), days = days(5, MissionResult.SUCCESS),
                            isLoading = false, errorMessage = "기록을 불러오지 못했어요. ".repeat(10),
                            onDateSelected = { selected = it }, onPreviousMonth = {}, onNextMonth = {},
                            onDismiss = {}, onRetry = { retries++ },
                        )
                    }
                }
            }
            compose.onNodeWithContentDescription("2026년 5월 31일, 기록 1개, 최근 기록 목적지 도착")
                .performScrollTo().assertIsDisplayed().performClick()
            assertEquals(MissionDate.of(2026, 5, 31), selected)
            compose.onNodeWithText("다시 시도").performScrollTo().assertIsDisplayed().performClick()
            assertEquals(1, retries)
        } finally {
            activity.pause().stop().destroy()
        }
    }

    private fun verifyStableBounds(fontScale: Float) {
        val month = mutableStateOf(2)
        val loading = mutableStateOf(false)
        val error = mutableStateOf<String?>(null)
        val result = mutableStateOf<MissionResult?>(null)
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        try {
            activity.get().setContent {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                    RingoutTheme {
                        RecordsMonthDialog(
                            month = MissionYearMonth(2026, month.value), days = days(month.value, result.value),
                            isLoading = loading.value, errorMessage = error.value,
                            onDateSelected = {}, onPreviousMonth = {}, onNextMonth = {}, onDismiss = {}, onRetry = {},
                            modifier = Modifier.testTag("calendar"),
                        )
                    }
                }
            }
            val dialogBounds = compose.onNodeWithTag("calendar").fetchSemanticsNode().boundsInWindow
            val buttonBounds = compose.onNodeWithContentDescription("다음 달").fetchSemanticsNode().boundsInWindow
            for (nextMonth in listOf(9, 5, 2, 12, 1, 2)) {
                for (state in 0..3) {
                    compose.runOnIdle {
                        month.value = nextMonth
                        loading.value = state == 0
                        error.value = if (state == 3) "기록을 불러오지 못했어요. ".repeat(10) else null
                        result.value = when (state) {
                            1 -> MissionResult.SUCCESS
                            2 -> MissionResult.FAILURE
                            else -> null
                        }
                    }
                    assertEquals(dialogBounds, compose.onNodeWithTag("calendar").fetchSemanticsNode().boundsInWindow)
                    assertEquals(buttonBounds, compose.onNodeWithContentDescription("다음 달").fetchSemanticsNode().boundsInWindow)
                }
            }
        } finally {
            activity.pause().stop().destroy()
        }
    }

    private fun days(month: Int, result: MissionResult?) = MissionYearMonth(2026, month).calendarDates().map { date ->
        date?.let { RecordsDayUiState(it, false, false, false, result, if (result == null) 0 else 1) }
    }
}
