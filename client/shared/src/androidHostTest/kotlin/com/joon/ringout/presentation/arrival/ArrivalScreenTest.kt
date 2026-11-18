package com.joon.ringout.presentation.arrival

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import com.joon.ringout.domain.arrival.ArrivalResult
import com.joon.ringout.domain.missionhistory.MissionDate
import com.joon.ringout.presentation.assertTextFits
import com.joon.ringout.presentation.launchWithFontScale
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
class ArrivalScreenTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test
    fun `라이트와 다크 테마에서 성장 중인 씨앗과 완성된 나무를 표시하고 두 버튼이 각각 동작한다`() {
        ThemeMode.entries.forEach { theme ->
            var state by mutableStateOf(readyState(successfulDays = 4))
            var starts = 0
            var shares = 0
            launchWithFontScale(1f) {
                RingoutTheme(theme) {
                    ArrivalScreen(
                        uiState = state,
                        onStartDay = { starts++ },
                        onShare = { shares++ },
                        onRetry = {},
                    )
                }
            }.use { content ->
                assertArrival(successfulDays = 4, filledSlots = 4, description = GrowingDescription)
                compose.onNodeWithText(StartLabel).assertIsDisplayed()
                compose.onNodeWithText(ShareLabel).assertIsDisplayed()
                compose.runOnIdle { content.captureLayout("arrival-${theme.name.lowercase()}-growing") }

                compose.onNodeWithText(ShareLabel).performClick()
                compose.runOnIdle {
                    assertEquals(1, shares)
                    assertEquals(0, starts)
                    state = readyState(successfulDays = 5)
                }

                assertArrival(successfulDays = 5, filledSlots = 5, description = CompletedDescription)
                compose.onNodeWithText(GrowingDescription).assertDoesNotExist()
                compose.runOnIdle { content.captureLayout("arrival-${theme.name.lowercase()}-tree") }
                compose.onNodeWithText(StartLabel).performClick()
                compose.runOnIdle {
                    assertEquals(1, starts)
                    assertEquals(1, shares)
                }
            }
        }
    }

    @Test
    fun `같은 날 다시 도착하면 저장된 성장 상태를 유지하고 여섯 번째 성공일에는 새 씨앗을 표시한다`() {
        var state by mutableStateOf(readyState(successfulDays = 4))
        launchWithFontScale(1f) {
            ArrivalScreen(uiState = state, onStartDay = {}, onShare = {}, onRetry = {})
        }.use { content ->
            assertArrival(successfulDays = 4, filledSlots = 4, description = GrowingDescription)
            compose.runOnIdle {
                state = readyState(successfulDays = 4, grewOnThisArrival = false)
            }
            assertArrival(successfulDays = 4, filledSlots = 4, description = GrowingDescription)
            compose.onNodeWithText(CompletedDescription).assertDoesNotExist()

            compose.runOnIdle { state = readyState(successfulDays = 6) }
            assertArrival(successfulDays = 6, filledSlots = 1, description = "4일 더 도착하면\n나무로 자라요")
            compose.onNodeWithText(CompletedDescription).assertDoesNotExist()
            compose.runOnIdle { content.captureLayout("arrival-dark-next-seed") }
        }
    }

    @Test
    @Config(qualifiers = "w320dp-h568dp")
    fun `작은 화면과 두 배 글자 크기에서도 성장 안내를 끝까지 읽고 두 버튼을 누를 수 있다`() {
        ThemeMode.entries.forEach { theme ->
            var state by mutableStateOf(readyState(successfulDays = 4))
            var starts = 0
            var shares = 0
            launchWithFontScale(2f) {
                RingoutTheme(theme) {
                    ArrivalScreen(
                        uiState = state,
                        onStartDay = { starts++ },
                        onShare = { shares++ },
                        onRetry = {},
                    )
                }
            }.use { content ->
                val actions = compose.onNodeWithTag("arrival-actions")
                actions.assertIsDisplayed()
                val actionsTop = actions.fetchSemanticsNode().boundsInRoot.top

                listOf(4 to GrowingDescription, 5 to CompletedDescription).forEach { (days, description) ->
                    compose.runOnIdle { state = readyState(successfulDays = days) }
                    awaitProgressImages(filledSlots = days)
                    compose.onNodeWithText("${days}번째", substring = true, useUnmergedTree = true)
                        .performScrollTo().assertIsDisplayed().assertTextFits()
                    compose.onNodeWithText("목적지에 도착했어요!", substring = true, useUnmergedTree = true)
                        .performScrollTo().assertIsDisplayed().assertTextFits()
                    compose.runOnIdle {
                        content.captureLayout("arrival-compact-${theme.name.lowercase()}-$days-title")
                    }

                    val descriptionNode = compose.onNodeWithText(description, useUnmergedTree = true)
                        .performScrollTo().assertIsDisplayed().assertTextFits()
                    actions.assertIsDisplayed()
                    assertEquals(actionsTop, actions.fetchSemanticsNode().boundsInRoot.top, 1f)
                    assertTrue(descriptionNode.fetchSemanticsNode().boundsInRoot.bottom <= actionsTop)
                    compose.onNodeWithText(StartLabel, useUnmergedTree = true).assertIsDisplayed().assertTextFits()
                    compose.onNodeWithText(ShareLabel, useUnmergedTree = true).assertIsDisplayed().assertTextFits()
                    compose.runOnIdle {
                        content.captureLayout("arrival-compact-${theme.name.lowercase()}-$days-description")
                    }

                    val progress = compose.onNodeWithTag("arrival-growth-progress")
                        .performScrollTo().assertIsDisplayed().assert(
                            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "5일 중 ${days}일 도착"),
                        )
                    actions.assertIsDisplayed()
                    assertEquals(actionsTop, actions.fetchSemanticsNode().boundsInRoot.top, 1f)
                    assertTrue(progress.fetchSemanticsNode().boundsInRoot.bottom <= actionsTop)
                    compose.onNodeWithText(StartLabel).assertIsDisplayed()
                    compose.onNodeWithText(ShareLabel).assertIsDisplayed()
                    compose.runOnIdle {
                        content.captureLayout("arrival-compact-${theme.name.lowercase()}-$days-progress")
                    }
                }

                compose.onNodeWithText(ShareLabel).performClick()
                compose.onNodeWithText(StartLabel).performClick()
                compose.runOnIdle {
                    assertEquals(1, shares)
                    assertEquals(1, starts)
                }
            }
        }
    }

    @Test
    fun `조회 중에는 도착 동작을 숨기고 조회 오류에서는 다시 시도할 수 있다`() {
        var state by mutableStateOf(ArrivalUiState())
        var retries = 0
        launchWithFontScale(1f) {
            ArrivalScreen(uiState = state, onStartDay = {}, onShare = {}, onRetry = { retries++ })
        }.use { content ->
            compose.onNodeWithTag("arrival-loading").assertIsDisplayed()
            compose.onNodeWithText(StartLabel).assertDoesNotExist()
            compose.onNodeWithText(ShareLabel).assertDoesNotExist()
            compose.onNodeWithTag("arrival-growth-progress").assertDoesNotExist()

            val message = "도착 기록을 불러오지 못했어요. 다시 시도해 주세요."
            compose.runOnIdle { state = ArrivalUiState(isLoading = false, errorMessage = message) }
            compose.onNodeWithTag("arrival-loading").assertDoesNotExist()
            compose.onNodeWithText(message).assertIsDisplayed().assertTextFits()
            compose.onNodeWithText(StartLabel).assertDoesNotExist()
            compose.onNodeWithText(ShareLabel).assertDoesNotExist()
            compose.onNodeWithText("다시 시도").assertIsDisplayed().performClick()
            compose.runOnIdle {
                assertEquals(1, retries)
                content.captureLayout("arrival-dark-error")
            }
        }
    }

    private fun assertArrival(successfulDays: Int, filledSlots: Int, description: String) {
        awaitProgressImages(filledSlots)
        compose.onNodeWithText("${successfulDays}번째", substring = true, useUnmergedTree = true)
            .assertIsDisplayed().assertTextFits()
        compose.onNodeWithText("목적지에 도착했어요!", substring = true, useUnmergedTree = true)
            .assertIsDisplayed().assertTextFits()
        compose.onNodeWithText(description, useUnmergedTree = true).assertIsDisplayed().assertTextFits()
        compose.onNodeWithTag("arrival-growth-progress").assertIsDisplayed().assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "5일 중 ${filledSlots}일 도착"),
        )
    }

    private fun awaitProgressImages(filledSlots: Int) {
        compose.waitUntil(timeoutMillis = 10_000) {
            val filled = compose.onAllNodesWithTag("arrival-progress-filled-loaded", useUnmergedTree = true)
                .fetchSemanticsNodes().size
            val empty = compose.onAllNodesWithTag("arrival-progress-empty-loaded", useUnmergedTree = true)
                .fetchSemanticsNodes().size
            filled == filledSlots && empty == 5 - filledSlots
        }
        compose.waitForIdle()
    }

    private fun readyState(successfulDays: Int, grewOnThisArrival: Boolean = true) = ArrivalUiState(
        result = ArrivalResult(
            occurrenceId = if (grewOnThisArrival) "arrival-$successfulDays" else "same-day-arrival",
            completedLocalDate = MissionDate.parse("2026-11-09"),
            completedAtEpochMillis = 1_000,
            alarmDismissedAtEpochMillis = 500,
            successfulDayCountAtArrival = successfulDays,
            grewOnThisArrival = grewOnThisArrival,
            acknowledgedAtEpochMillis = null,
        ),
        isLoading = false,
    )

    private companion object {
        const val StartLabel = "오늘 하루 시작하기!"
        const val ShareLabel = "공유하기"
        const val GrowingDescription = "1일 더 도착하면\n나무로 자라요"
        const val CompletedDescription = "씨앗이 모두 자라\n나무가 되었어요!"
    }
}
