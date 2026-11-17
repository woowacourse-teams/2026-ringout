package com.joon.ringout.presentation.records

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import com.joon.ringout.domain.missionhistory.AlarmRecordShareData
import com.joon.ringout.domain.missionhistory.AlarmUsageRecord
import com.joon.ringout.domain.missionhistory.MissionDate
import com.joon.ringout.domain.missionhistory.MissionResult
import com.joon.ringout.domain.missionhistory.groupByAlarm
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
class RecordsDetailScreenTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    @Test
    fun `공유 아이콘은 투명한 배경 위에 표시된다`() {
        val date = MissionDate.of(2026, 9, 28)
        val records = listOf(
            AlarmUsageRecord("success", date, "success", MissionResult.SUCCESS,
                ringingStartedAtEpochMillis = 1000, missionCompletedAtEpochMillis = 2000),
        ).groupByAlarm()
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        try {
            activity.get().setContent {
                RingoutTheme(ThemeMode.Dark) {
                    RecordsDetailScreen(date, records, false, null, {}, {}, {})
                }
            }
            val bounds = compose.onNodeWithContentDescription("알람 기록 공유하기").fetchSemanticsNode().boundsInWindow
            compose.waitUntil(timeoutMillis = 10_000) {
                compose.runOnIdle {
                    val view = activity.get().findViewById<View>(android.R.id.content)
                    val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                    try {
                        view.draw(Canvas(bitmap))
                        var lightPixels = 0
                        var darkPixels = 0
                        val left = (bounds.left + bounds.width * .35f).toInt()
                        val right = (bounds.left + bounds.width * .65f).toInt()
                        val top = (bounds.top + bounds.height * .35f).toInt()
                        val bottom = (bounds.top + bounds.height * .65f).toInt()
                        for (y in top until bottom) for (x in left until right) {
                            val pixel = bitmap.getPixel(x, y)
                            if (Color.red(pixel) > 220 && Color.green(pixel) > 220 && Color.blue(pixel) > 220) lightPixels++
                            if (Color.red(pixel) < 40 && Color.green(pixel) < 40 && Color.blue(pixel) < 40) darkPixels++
                        }
                        lightPixels > 0 && darkPixels > 0
                    } finally {
                        bitmap.recycle()
                    }
                }
            }
        } finally {
            activity.pause().stop().destroy()
        }
    }

    @Test
    fun `기록 카드 전체를 누르면 선택한 날짜의 상세 화면을 요청한다`() {
        val date = MissionDate.of(2026, 9, 28)
        val record = AlarmUsageRecord("first", date, ringingStartedAtEpochMillis = 1790546400000)
        var selected: MissionDate? = null
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        try {
            activity.get().setContent {
                RingoutTheme {
                    RecordsScreen(
                        uiState = RecordsUiState(today = date, selectedDate = date, records = listOf(record).groupByAlarm()),
                        onRecordClick = { selected = it },
                    )
                }
            }
            compose.onNodeWithText(record.recordTimes().title).performClick()
            assertEquals(date, selected)
        } finally {
            activity.pause().stop().destroy()
        }
    }

    @Test
    fun `두 시각이 있는 성공 그룹만 공유 버튼을 표시하고 선택한 기록을 전달한다`() {
        val date = MissionDate.of(2026, 9, 28)
        val groups = listOf(
            AlarmUsageRecord("success", date, "success", MissionResult.SUCCESS, alarmId = "one",
                ringingStartedAtEpochMillis = 1000, missionCompletedAtEpochMillis = 2000),
            AlarmUsageRecord("missing", date, "missing", MissionResult.SUCCESS, alarmId = "two",
                ringingStartedAtEpochMillis = 3000),
            AlarmUsageRecord("failure", date, "failure", MissionResult.FAILURE, alarmId = "three",
                ringingStartedAtEpochMillis = 4000, missionCompletedAtEpochMillis = 5000),
        ).groupByAlarm()
        var selected: AlarmRecordShareData? = null
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        try {
            activity.get().setContent {
                RingoutTheme {
                    RecordsDetailScreen(date, groups, false, null, {}, {}, { selected = it })
                }
            }
            compose.onAllNodesWithContentDescription("알람 기록 공유하기").assertCountEquals(1)
            compose.onNodeWithContentDescription("알람 기록 공유하기").performClick()
            assertEquals("success", selected?.successRecordKey)
            assertEquals(1000L, selected?.wakeAtEpochMillis)
            assertEquals(2000L, selected?.arrivedAtEpochMillis)
        } finally {
            activity.pause().stop().destroy()
        }
    }

    @Test
    fun `상세 화면의 뒤로 가기 버튼은 콜백을 호출한다`() {
        var backCount = 0
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        try {
            activity.get().setContent {
                RingoutTheme {
                    RecordsDetailScreen(MissionDate.of(2026, 9, 28), emptyList(), false, null, {}, { backCount++ }, {})
                }
            }
            compose.onNodeWithContentDescription("뒤로가기").performClick()
            assertEquals(1, backCount)
        } finally {
            activity.pause().stop().destroy()
        }
    }
}
