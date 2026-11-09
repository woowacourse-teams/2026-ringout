package com.joon.ringout.presentation.arrival

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.joon.ringout.domain.arrival.ArrivalGrowth
import com.joon.ringout.domain.arrival.ArrivalResult
import com.joon.ringout.domain.arrival.ArrivalResultRepository
import com.joon.ringout.domain.missionhistory.MissionDate
import com.joon.ringout.presentation.launchWithFontScale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w402dp-h941dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RestrictedArrivalHostTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test
    fun `업데이트 제한 중 새 도착 결과를 확인하면 기존 제한 화면으로 돌아간다`() {
        val repository = RestrictedArrivalRepository()
        launchWithFontScale(1f) {
            RestrictedArrivalHost(repository, hasActiveRuntime = false) { Text("업데이트가 필요해요") }
        }.use {
            compose.onNodeWithText("업데이트가 필요해요").assertIsDisplayed()
            compose.runOnIdle { repository.publish(4) }
            compose.onNodeWithText("4번째").assertIsDisplayed()
            compose.onNodeWithText("업데이트가 필요해요").assertDoesNotExist()
            compose.onNodeWithText("공유하기").assertDoesNotExist()
            compose.onNodeWithText("오늘 하루 시작하기!").performClick()
            compose.onNodeWithText("업데이트가 필요해요").assertIsDisplayed()
            assertEquals(listOf("arrival-4"), repository.confirmed)
        }
    }

    @Test
    fun `새 알람이 시작되면 도착 화면을 미루고 알람 종료 후 미확인 결과를 다시 표시한다`() {
        val repository = RestrictedArrivalRepository().apply { publish(4) }
        var activeRuntime by mutableStateOf(true)
        var enabled by mutableStateOf(true)
        launchWithFontScale(1f) {
            RestrictedArrivalHost(repository, hasActiveRuntime = activeRuntime, isEnabled = enabled) {
                Text(if (activeRuntime) "진행 중인 알람" else "기존 제한 화면")
            }
        }.use {
            compose.onNodeWithText("진행 중인 알람").assertIsDisplayed()
            compose.onNodeWithText("4번째").assertDoesNotExist()
            compose.runOnIdle { activeRuntime = false }
            compose.onNodeWithText("4번째").assertIsDisplayed()
            compose.runOnIdle { activeRuntime = true }
            compose.onNodeWithText("진행 중인 알람").assertIsDisplayed()
            compose.runOnIdle { activeRuntime = false }
            compose.onNodeWithText("4번째").assertIsDisplayed()

            compose.runOnIdle { enabled = false }
            compose.onNodeWithText("기존 제한 화면").assertIsDisplayed()
            compose.runOnIdle { enabled = true }
            compose.onNodeWithText("4번째").assertIsDisplayed()
            assertTrue(repository.confirmed.isEmpty())
        }
    }

    @Test
    fun `더 최신 결과가 도착하면 회차를 교체하고 최신 회차 확인 후 로그인 제한으로 돌아간다`() {
        val repository = RestrictedArrivalRepository().apply { publish(4) }
        launchWithFontScale(1f) {
            RestrictedArrivalHost(repository, hasActiveRuntime = false) { Text("로그인이 필요해요") }
        }.use {
            compose.onNodeWithText("4번째").assertIsDisplayed()
            compose.runOnIdle { repository.publish(5) }
            compose.onNodeWithText("5번째").assertIsDisplayed()
            compose.onNodeWithText("4번째").assertDoesNotExist()
            compose.onNodeWithText("오늘 하루 시작하기!").performClick()
            compose.onNodeWithText("로그인이 필요해요").assertIsDisplayed()
            assertEquals(listOf("arrival-5"), repository.confirmed)
        }
    }
}

private class RestrictedArrivalRepository : ArrivalResultRepository {
    private val results = mutableMapOf<String, ArrivalResult>()
    private val latest = MutableStateFlow<ArrivalResult?>(null)
    val confirmed = mutableListOf<String>()

    fun publish(days: Int) {
        val result = ArrivalResult(
            occurrenceId = "arrival-$days",
            completedLocalDate = MissionDate.parse("2026-11-09"),
            completedAtEpochMillis = days.toLong(),
            alarmDismissedAtEpochMillis = 0,
            successfulDayCountAtArrival = days,
            grewOnThisArrival = true,
            acknowledgedAtEpochMillis = null,
        )
        results[result.occurrenceId] = result
        latest.value = result
    }

    override fun observeLatestUnacknowledgedResult(): Flow<ArrivalResult?> = latest
    override suspend fun getResult(occurrenceId: String): ArrivalResult? = results[occurrenceId]
    override suspend fun acknowledgeThrough(occurrenceId: String, acknowledgedAtEpochMillis: Long) {
        confirmed += occurrenceId
        if (latest.value?.occurrenceId == occurrenceId) latest.value = null
    }
    override suspend fun getGrowth(): ArrivalGrowth = error("결과 스냅샷을 사용해야 합니다.")
    override fun observeGrowth(): Flow<ArrivalGrowth> = error("결과 스냅샷을 사용해야 합니다.")
}
