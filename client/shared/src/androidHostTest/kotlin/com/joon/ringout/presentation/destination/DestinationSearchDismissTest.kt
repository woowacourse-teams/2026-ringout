package com.joon.ringout.presentation.destination

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.unit.dp
import com.joon.ringout.RingoutTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DestinationSearchDismissTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    @Test
    fun `검색창 바깥의 빈 공간을 누르면 검색 모드가 닫힌다`() {
        withSearchLayout { state ->
            compose.onNodeWithContentDescription("검색창 바깥 영역").performTouchInput {
                click(Offset(width / 2f, height * 0.75f))
            }
            compose.runOnIdle { assertFalse(state.isOpen.value) }
            compose.onNodeWithContentDescription("장소 또는 주소 검색").assertDoesNotExist()
            compose.onNodeWithContentDescription("검색창 바깥 영역").assertDoesNotExist()
        }
    }

    @Test
    fun `결과창 안의 빈 공간과 검색 입력란은 검색창을 닫지 않는다`() {
        withSearchLayout { state ->
            val attribution = compose.onNodeWithText("Powered by Google").fetchSemanticsNode().boundsInRoot
            compose.onRoot().performTouchInput {
                click(Offset(attribution.center.x, attribution.bottom + 160.dp.toPx()))
            }
            compose.onNodeWithContentDescription("장소 또는 주소 검색")
                .performTouchInput { click(center) }
                .performTextInput("강남역")
            compose.runOnIdle {
                assertTrue(state.isOpen.value)
                assertEquals("강남역", state.query.value)
            }
        }
    }

    @Test
    fun `결과 항목을 누르면 바깥 닫기 대신 목적지를 선택한다`() {
        withSearchLayout { state ->
            compose.onNodeWithText(DefaultDestinationSelection.name).performTouchInput { click(center) }
            compose.runOnIdle {
                assertEquals(DefaultDestinationSelection, state.selected)
                assertTrue(state.isOpen.value)
            }
        }
    }

    private fun withSearchLayout(verify: (SearchFixture) -> Unit) {
        val state = SearchFixture()
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        try {
            activity.get().setContent {
                RingoutTheme {
                    DestinationMapLayout(
                        selection = DefaultDestinationSelection.copy(name = "지도 위치"),
                        isCameraMoving = false,
                        isResolvingAddress = false,
                        mapError = null,
                        onBackClick = {},
                        isSearchOpen = state.isOpen.value,
                        searchQuery = state.query.value,
                        searchResults = listOf(DefaultDestinationSelection),
                        isSearching = false,
                        searchError = null,
                        savedDestinations = emptyList(),
                        isLocatingCurrentLocation = false,
                        isSaveInProgress = false,
                        isDestinationActionEnabled = true,
                        currentLocationError = null,
                        onSearchQueryChange = { state.query.value = it },
                        onSearchClick = { state.isOpen.value = true },
                        onSearchClose = { state.isOpen.value = false },
                        onSearchSubmit = {},
                        onSearchResultClick = { state.selected = it },
                        onCurrentLocationClick = {},
                        onDestinationManagementClick = {},
                        onSavedDestinationClick = { _, _ -> },
                        onConfirmClick = {},
                        mapContent = { Box(it) },
                    )
                }
            }
            verify(state)
        } finally {
            activity.pause().stop().destroy()
        }
    }

    private class SearchFixture {
        val isOpen = mutableStateOf(true)
        val query = mutableStateOf("")
        var selected: DestinationSelection? = null
    }
}
