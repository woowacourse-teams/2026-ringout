package com.joon.ringout.presentation.destination.component

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import com.joon.ringout.RingoutTheme
import com.joon.ringout.domain.destination.SavedDestination
import com.joon.ringout.presentation.destination.DestinationUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w402dp-h850dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DestinationEntryTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test
    fun `마지막 목적지 삭제 후 복귀 요청이 남아 있어도 지도를 열지 않고 다음 직접 선택 때만 연다`() = withEntry(listOf(Destination)) { state ->
        compose.onNodeWithText("목적지 설정").performClick()
        compose.onNodeWithContentDescription("헬스장 관리").performClick()
        compose.runOnIdle { state.active.value = false }
        compose.waitForIdle()
        compose.runOnIdle {
            state.ui.value = DestinationUiState(isLoading = false)
            state.openRequest.value = 1L
            state.active.value = true
        }
        compose.waitForIdle()
        compose.onNodeWithText("저장된 목적지").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, state.mapEntries) }
        compose.onNodeWithText("목적지 설정").performClick()
        compose.runOnIdle { assertEquals(1, state.mapEntries) }
    }

    @Test
    fun `관리는 목적지를 선택하지 않고 관리 화면을 요청하고 복귀 시 목록을 다시 표시한다`() = withEntry(listOf(Destination)) { state ->
        compose.onNodeWithText("목적지 설정").performClick()
        compose.onNodeWithContentDescription("헬스장 관리").performClick()
        compose.runOnIdle {
            assertEquals(Destination, state.managed)
            assertNull(state.selected)
            assertEquals(0, state.mapEntries)
            state.active.value = false
        }
        compose.onNodeWithText("저장된 목적지").assertDoesNotExist()
        compose.runOnIdle {
            state.openRequest.value += 1L
            state.active.value = true
        }
        compose.onNodeWithText("저장된 목적지").assertIsDisplayed()
        compose.onNodeWithContentDescription("저장된 목적지 닫기").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("저장된 목적지").assertDoesNotExist()
    }

    @Test
    fun `저장된 목적지가 없으면 다이얼로그 없이 지도로 이동한다`() = withEntry(emptyList()) { state ->
        compose.onNodeWithText("목적지 설정").performClick()
        compose.runOnIdle { assertEquals(1, state.mapEntries) }
        compose.onNodeWithText("저장된 목적지").assertDoesNotExist()
    }

    @Test
    fun `저장된 목적지를 선택하면 지도 없이 선택 결과를 전달한다`() = withEntry(listOf(Destination)) { state ->
        compose.onNodeWithText("목적지 설정").performClick()
        compose.onNodeWithText("저장된 목적지").assertIsDisplayed()
        compose.onNodeWithContentDescription("헬스장 선택").performClick()
        compose.runOnIdle {
            assertEquals(Destination, state.selected)
            assertEquals(0, state.mapEntries)
        }
        compose.onNodeWithText("저장된 목적지").assertDoesNotExist()
    }

    @Test
    fun `지도에서 찾기를 누르면 다이얼로그를 닫고 지도로 이동한다`() = withEntry(listOf(Destination)) { state ->
        compose.onNodeWithText("목적지 설정").performClick()
        compose.onNodeWithText("지도에서 찾기").performClick()
        compose.runOnIdle {
            assertEquals(1, state.mapEntries)
            assertNull(state.selected)
        }
        compose.onNodeWithText("저장된 목적지").assertDoesNotExist()
    }

    @Test
    fun `다이얼로그를 닫으면 목적지를 변경하지 않고 다시 열 수 있다`() = withEntry(listOf(Destination)) { state ->
        compose.onNodeWithText("목적지 설정").performClick()
        compose.onNodeWithContentDescription("저장된 목적지 닫기").performClick()
        compose.runOnIdle {
            assertEquals(0, state.mapEntries)
            assertNull(state.selected)
        }
        compose.onNodeWithText("목적지 설정").performClick()
        compose.onNodeWithText("저장된 목적지").assertIsDisplayed()
    }

    @Test
    fun `목록을 불러오는 동안 기다리고 저장된 목적지가 있으면 선택창을 유지한다`() = withEntry(emptyList(), loading = true) { state ->
        compose.onNodeWithText("목적지 설정").performClick()
        compose.onNodeWithContentDescription("저장된 목적지 불러오는 중").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(0, state.mapEntries)
            state.ui.value = DestinationUiState(destinations = listOf(Destination), isLoading = false)
        }
        compose.onNodeWithText("헬스장").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, state.mapEntries) }
    }

    @Test
    fun `목록 조회가 끝나고 비어 있으면 지도로 한 번만 이동한다`() = withEntry(emptyList(), loading = true) { state ->
        compose.onNodeWithText("목적지 설정").performClick()
        compose.runOnIdle { state.ui.value = DestinationUiState(isLoading = false) }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(1, state.mapEntries) }
        compose.onNodeWithText("저장된 목적지").assertDoesNotExist()
    }

    @Test
    fun `조회 중 선택창을 닫으면 늦게 도착한 빈 목록으로 지도를 열지 않는다`() = withEntry(emptyList(), loading = true) { state ->
        compose.onNodeWithText("목적지 설정").performClick()
        compose.onNodeWithContentDescription("저장된 목적지 닫기").performClick()
        compose.runOnIdle { state.ui.value = DestinationUiState(isLoading = false) }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(0, state.mapEntries) }
    }

    @Test
    fun `목적지가 많으면 목록을 아래위로 스크롤하고 하단 버튼은 고정된다`() = withEntry(
        List(8) { Destination.copy(id = it + 1L, name = "목적지 ${it + 1}") },
    ) {
        compose.onNodeWithText("목적지 설정").performClick()
        compose.onNodeWithText("목적지 1").assertIsDisplayed()
        compose.onNodeWithText("목적지 3").assertIsDisplayed()
        compose.onNodeWithContentDescription("저장된 목적지 목록").performScrollToIndex(7)
        compose.onNodeWithText("목적지 8").assertIsDisplayed()
        compose.onNodeWithText("지도에서 찾기").assertIsDisplayed()
        compose.onNodeWithContentDescription("저장된 목적지 목록").performScrollToIndex(0)
        compose.onNodeWithText("목적지 1").assertIsDisplayed()
    }

    @Test
    fun `조회 오류는 빈 목록으로 간주하지 않고 재시도할 수 있다`() = withEntry(emptyList(), loading = true) { state ->
        compose.onNodeWithText("목적지 설정").performClick()
        compose.runOnIdle { state.ui.value = DestinationUiState(isLoading = false, errorMessage = "조회 실패") }
        compose.onNodeWithText("다시 시도").performClick()
        compose.runOnIdle {
            assertEquals(1, state.retries)
            assertEquals(0, state.mapEntries)
        }
    }

    @Test
    fun `선택 중 원래 화면이 비활성화되면 다이얼로그와 미처리 진입을 취소한다`() = withEntry(emptyList(), loading = true) { state ->
        compose.onNodeWithText("목적지 설정").performClick()
        compose.runOnIdle { state.active.value = false }
        compose.onNodeWithText("저장된 목적지").assertDoesNotExist()
        compose.runOnIdle {
            state.ui.value = DestinationUiState(isLoading = false)
            state.active.value = true
        }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(0, state.mapEntries) }
    }

    private fun withEntry(items: List<SavedDestination>, loading: Boolean = false, verify: (Fixture) -> Unit) {
        val state = Fixture(DestinationUiState(destinations = items, isLoading = loading))
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        try {
            activity.get().setContent {
                RingoutTheme {
                    DestinationEntry(
                        uiState = state.ui.value,
                        isActive = state.active.value,
                        onFindInMap = { state.mapEntries++ },
                        onSelect = { state.selected = it },
                        onManage = { state.managed = it },
                        openRequestId = state.openRequest.value,
                        onRetry = { state.retries++ },
                    ) { onClick -> TextButton(onClick = onClick) { Text("목적지 설정") } }
                }
            }
            verify(state)
        } finally {
            activity.pause().stop().destroy()
        }
    }

    private class Fixture(initial: DestinationUiState) {
        val ui = mutableStateOf(initial)
        val openRequest = mutableStateOf(0L)
        var managed: SavedDestination? = null
        val active = mutableStateOf(true)
        var retries = 0
        var mapEntries = 0
        var selected: SavedDestination? = null
    }
}

private val Destination = SavedDestination(1, "헬스장", "대한민국 경기도 수원시 팔달구", 37.28, 127.01)
