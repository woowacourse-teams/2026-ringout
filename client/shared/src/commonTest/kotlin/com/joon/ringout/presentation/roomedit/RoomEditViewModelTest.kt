package com.joon.ringout.presentation.roomedit

import com.joon.ringout.presentation.roomedit.model.RoomEditImageChange
import com.joon.ringout.presentation.roomlist.model.RoomUiModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RoomEditViewModelTest {
    @Test
    fun `초기 편집값은 비어 있고 원본이 도착할 때까지 저장할 수 없다`() {
        val viewModel = RoomEditViewModel()
        viewModel.initialize("room-1", null)

        assertEquals("", viewModel.uiState.nameInput)
        assertEquals("", viewModel.uiState.introductionInput)
        assertFalse(viewModel.uiState.isOriginalLoaded)
        assertFalse(viewModel.uiState.canSave)
        assertNull(viewModel.createDraft())

        viewModel.initialize("room-1", sampleRoom())

        assertTrue(viewModel.uiState.isOriginalLoaded)
        assertEquals("아침러닝", viewModel.uiState.effectiveName)
        assertEquals("함께 달리며 달려요.", viewModel.uiState.effectiveIntroduction)
        assertFalse(viewModel.uiState.canSave)
    }

    @Test
    fun `원본은 한 번 고정되고 같은 모임 데이터 재전달이 편집값을 덮지 않는다`() {
        val viewModel = RoomEditViewModel()
        viewModel.initialize("room-1", sampleRoom())
        viewModel.updateName("새모임")
        viewModel.updateIntroduction("새 소개입니다.")

        viewModel.initialize(
            "room-1",
            sampleRoom().copy(name = "서버에서 다시 온 이름", description = "서버에서 다시 온 소개입니다."),
        )

        assertEquals("아침러닝", viewModel.uiState.original?.name)
        assertEquals("새모임", viewModel.uiState.nameInput)
        assertEquals("새 소개입니다.", viewModel.uiState.introductionInput)
    }

    @Test
    fun `입력한 값을 모두 지우면 원본을 유지하고 변경을 취소한다`() {
        val viewModel = initializedViewModel()
        viewModel.updateName("새모임")
        viewModel.updateIntroduction("새 소개입니다.")
        assertTrue(viewModel.uiState.hasChanges)

        viewModel.updateName("")
        viewModel.updateIntroduction("")

        assertEquals("아침러닝", viewModel.uiState.effectiveName)
        assertEquals("함께 달리며 달려요.", viewModel.uiState.effectiveIntroduction)
        assertFalse(viewModel.uiState.hasChanges)
        assertFalse(viewModel.uiState.canSave)
        assertNull(viewModel.createDraft())
    }

    @Test
    fun `이름만 수정한 초안은 이름을 정규화하고 미수정 소개와 이미지를 유지한다`() {
        val viewModel = initializedViewModel()
        viewModel.updateName("  새모임  ")

        val draft = viewModel.createDraft()

        assertEquals("room-1", draft?.roomId)
        assertEquals("새모임", draft?.name)
        assertEquals("함께 달리며 달려요.", draft?.introduction)
        assertEquals(RoomEditImageChange.Unchanged, draft?.imageChange)
    }

    @Test
    fun `정규화된 원본과 같은 이름은 변경으로 세지 않지만 소개 편집은 저장할 수 있다`() {
        val viewModel = initializedViewModel()
        viewModel.updateName("  아침러닝  ")
        assertFalse(viewModel.uiState.nameChanged)
        assertFalse(viewModel.uiState.canSave)

        viewModel.updateIntroduction("새 소개입니다. ")

        assertTrue(viewModel.uiState.canSave)
        assertEquals("아침러닝", viewModel.createDraft()?.name)
        assertEquals("새 소개입니다. ", viewModel.createDraft()?.introduction)
    }

    @Test
    fun `공백만 입력한 이름이나 소개는 다른 변경이 있어도 저장을 막는다`() {
        val nameViewModel = initializedViewModel()
        nameViewModel.updateName(" ")
        nameViewModel.onImageSelected(1L)
        assertTrue(nameViewModel.uiState.hasChanges)
        assertFalse(nameViewModel.uiState.canSave)
        assertNull(nameViewModel.createDraft())

        val introductionViewModel = initializedViewModel()
        introductionViewModel.updateIntroduction("\n ")
        introductionViewModel.updateName("새모임")
        assertFalse(introductionViewModel.uiState.canSave)
        assertNull(introductionViewModel.createDraft())
    }

    @Test
    fun `소개만 수정하면 이름 원문을 보존하고 소개는 입력 원문을 전달한다`() {
        val viewModel = initializedViewModel()
        viewModel.updateIntroduction("  매일 같이 달려요.\n")

        val draft = viewModel.createDraft()

        assertEquals("아침러닝", draft?.name)
        assertEquals("  매일 같이 달려요.\n", draft?.introduction)
    }

    @Test
    fun `이미지 선택은 토큰으로 변경을 표현하고 preview 유실 시 저장을 취소한다`() {
        val viewModel = initializedViewModel()
        viewModel.onImageSelected(12L)

        assertTrue(viewModel.uiState.canSave)
        assertEquals(RoomEditImageChange.Replace(12L), viewModel.createDraft()?.imageChange)

        viewModel.onImagePreviewLost(11L)
        assertEquals(12L, viewModel.uiState.imageSelectionToken)
        viewModel.onImagePreviewLost(12L)

        assertFalse(viewModel.uiState.hasChanges)
        assertFalse(viewModel.uiState.canSave)
        assertNull(viewModel.createDraft())
    }

    @Test
    fun `다른 모임을 초기화하면 이전 모임의 입력 상태를 노출하지 않는다`() {
        val viewModel = initializedViewModel()
        viewModel.updateName("새모임")

        viewModel.initialize("room-2", sampleRoom("room-2").copy(name = "다른모임"))

        assertEquals("room-2", viewModel.uiState.roomId)
        assertEquals("", viewModel.uiState.nameInput)
        assertEquals("다른모임", viewModel.uiState.effectiveName)
        assertFalse(viewModel.uiState.canSave)
    }

    @Test
    fun `유효하지 않은 원본 필드도 유효한 새 값으로 고치기 전에는 저장할 수 없다`() {
        val viewModel = RoomEditViewModel()
        viewModel.initialize("room-1", sampleRoom().copy(name = "아침 러닝"))
        viewModel.updateIntroduction("소개 변경")

        assertFalse(viewModel.uiState.canSave)
        assertNull(viewModel.createDraft())

        viewModel.updateName("아침러닝")
        assertTrue(viewModel.uiState.canSave)
        assertEquals("아침러닝", viewModel.createDraft()?.name)
    }

    private fun initializedViewModel(): RoomEditViewModel = RoomEditViewModel().also {
        it.initialize("room-1", sampleRoom())
    }

    private fun sampleRoom(id: String = "room-1") = RoomUiModel(
        id = id,
        representativeImage = null,
        name = "아침러닝",
        description = "함께 달리며 달려요.",
        createdAt = "2026-09-15T09:00:00",
        activityDays = listOf("월", "수", "금"),
        activityTimeText = "오전 8:00",
        participantCount = 3,
        isJoined = true,
    )
}
