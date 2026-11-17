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
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w402dp-h850dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DestinationEntryTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test
    fun `제거 요청은 확인 전 API를 호출하지 않고 취소하면 목록을 유지한다`() = withEntry(listOf(Destination)) { state ->
        compose.onNodeWithText("목적지 설정").performClick()
        compose.onNodeWithContentDescription("헬스장 제거").performClick()
        compose.onNodeWithText("목적지 삭제").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(listOf(Destination.id), state.deleteRequests)
            assertEquals(0, state.deleteConfirmations)
            assertNull(state.selected)
            assertEquals(0, state.mapEntries)
        }
        compose.onNodeWithText("취소").performClick()
        compose.runOnIdle {
            assertEquals(0, state.deleteConfirmations)
            assertEquals(1, state.deleteDismissals)
        }
        compose.onNodeWithText("저장된 목적지").assertIsDisplayed()
        compose.onNodeWithText("헬스장").assertIsDisplayed()
    }

    @Test
    fun `마지막 목적지를 확인 삭제해도 목록 창을 유지하고 지도를 자동으로 열지 않는다`() = withEntry(listOf(Destination)) { state ->
        compose.onNodeWithText("목적지 설정").performClick()
        compose.onNodeWithContentDescription("헬스장 제거").performClick()
        compose.onNodeWithText("삭제").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("저장된 목적지").assertIsDisplayed()
        compose.onNodeWithText("지도에서 찾기").assertIsDisplayed()
        compose.onNodeWithText("헬스장").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(listOf(Destination.id), state.deleteRequests)
            assertEquals(1, state.deleteConfirmations)
            assertEquals(0, state.mapEntries)
            assertTrue(state.ui.value.destinations.isEmpty())
        }
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
    fun `지도에서 찾기로 화면이 비활성화되면 목록을 닫고 지도를 한 번 연다`() = withEntry(
        listOf(Destination),
        deactivateOnMap = true,
    ) { state ->
        compose.onNodeWithText("목적지 설정").performClick()
        compose.onNodeWithText("지도에서 찾기").performClick()
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(1, state.mapEntries)
            assertNull(state.selected)
        }
        compose.onNodeWithText("저장된 목적지").assertDoesNotExist()
    }

    @Test
    fun `열린 목적지 목록이 화면 비활성화로 닫히면 복귀 후에도 닫힌 상태를 유지한다`() = withEntry(listOf(Destination)) { state ->
        compose.onNodeWithText("목적지 설정").performClick()
        compose.onNodeWithText("저장된 목적지").assertIsDisplayed()

        compose.runOnIdle { state.active.value = false }
        compose.waitForIdle()

        compose.onNodeWithText("저장된 목적지").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(0, state.mapEntries)
            assertNull(state.selected)
            state.active.value = true
        }
        compose.waitForIdle()
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
        List(8) { Destination.copy(id = it + 1L, label = "목적지 ${it + 1}") },
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

    private fun withEntry(
        items: List<SavedDestination>,
        loading: Boolean = false,
        deactivateOnMap: Boolean = false,
        verify: (Fixture) -> Unit,
    ) {
        val state = Fixture(DestinationUiState(destinations = items, isLoading = loading))
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        try {
            activity.get().setContent {
                RingoutTheme {
                    DestinationEntry(
                        uiState = state.ui.value,
                        isActive = state.active.value,
                        onFindInMap = {
                            state.mapEntries++
                            if (deactivateOnMap) state.active.value = false
                        },
                        onSelect = { state.selected = it },
                        openRequestId = state.openRequest.value,
                        onRetry = { state.retries++ },
                        onDeleteRequest = { id ->
                            state.deleteRequests += id
                            state.ui.value = state.ui.value.copy(pendingDeleteDestinationId = id)
                        },
                        onDeleteConfirm = {
                            state.deleteConfirmations++
                            val id = state.ui.value.pendingDeleteDestinationId
                            state.ui.value = state.ui.value.copy(
                                destinations = state.ui.value.destinations.filterNot { it.id == id },
                                pendingDeleteDestinationId = null,
                            )
                        },
                        onDeleteDismiss = {
                            state.deleteDismissals++
                            state.ui.value = state.ui.value.copy(pendingDeleteDestinationId = null)
                        },
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
        val active = mutableStateOf(true)
        var retries = 0
        var mapEntries = 0
        var selected: SavedDestination? = null
        val deleteRequests = mutableListOf<Long>()
        var deleteConfirmations = 0
        var deleteDismissals = 0
    }
}

private val Destination = SavedDestination(1, "헬스장", 37.28, 127.01)
