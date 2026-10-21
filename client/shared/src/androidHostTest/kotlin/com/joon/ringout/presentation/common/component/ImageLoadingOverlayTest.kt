package com.joon.ringout.presentation.common.component

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.joon.ringout.RingoutTheme
import com.joon.ringout.presentation.profilechange.component.profileimage.ProfileImageEditor
import com.joon.ringout.presentation.roomedit.component.RoomEditImageSection
import com.joon.ringout.presentation.roomlist.model.RoomUiModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ImageLoadingOverlayTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    @Test
    fun `로딩 상태에서는 기본 미리보기 안에 스피너를 표시하고 재선택을 차단한다`() {
        verifyLoading(selectedImage = null)
    }

    @Test
    fun `선택한 사진 위에 업로드 스피너를 표시하고 종료 후 재선택을 허용한다`() {
        verifyLoading(selectedImage = ImageBitmap(16, 16))
    }

    private fun verifyLoading(selectedImage: ImageBitmap?) {
        val loading = mutableStateOf(false)
        var selections = 0
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        try {
            activity.get().setContent {
                RingoutTheme {
                    Column {
                        ProfileImageEditor(
                            profileImage = selectedImage,
                            errorMessage = null,
                            onProfileImageChangeClick = { selections++ },
                            modifier = Modifier.testTag("profile"),
                            isLoading = loading.value,
                        )
                        RoomEditImageSection(
                            room = PreviewRoom,
                            selectedImage = selectedImage,
                            onChangeClick = { selections++ },
                            modifier = Modifier.testTag("room"),
                            isLoading = loading.value,
                        )
                    }
                }
            }
            val tags = listOf("profile", "room")
            val bounds = tags.map { compose.onNodeWithTag(it).fetchSemanticsNode().boundsInRoot }
            compose.onAllNodesWithContentDescription("사진 처리 중").assertCountEquals(0)
            compose.runOnIdle { loading.value = true }
            val spinners = compose.onAllNodesWithContentDescription("사진 처리 중")
            spinners.assertCountEquals(2)
            val buttons = compose.onAllNodes(hasClickAction())
            buttons.assertCountEquals(2)
            tags.forEachIndexed { index, tag ->
                spinners[index].assertIsDisplayed()
                val spinnerBounds = spinners[index].fetchSemanticsNode().boundsInRoot
                assertTrue(bounds[index].contains(spinnerBounds.topLeft))
                assertTrue(bounds[index].contains(spinnerBounds.bottomRight))
                assertEquals(bounds[index], compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot)
                buttons[index].assertIsNotEnabled()
            }
            compose.runOnIdle { loading.value = false }
            compose.onAllNodesWithContentDescription("사진 처리 중").assertCountEquals(0)
            repeat(2) { buttons[it].assertIsEnabled().performClick() }
            assertEquals(2, selections)
        } finally {
            activity.pause().stop().destroy()
        }
    }
}

private val PreviewRoom = RoomUiModel(
    id = "7",
    representativeImage = null,
    name = "아침 러닝",
    description = "함께 달려요.",
    createdAt = "2026-09-15T09:00:00",
    activityDays = listOf("월", "수", "금"),
    activityTimeText = "오전 8:00",
    participantCount = 3,
    isJoined = true,
)
