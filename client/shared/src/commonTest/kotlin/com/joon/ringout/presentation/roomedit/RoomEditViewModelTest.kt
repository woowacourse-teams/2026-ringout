package com.joon.ringout.presentation.roomedit

import com.joon.ringout.analytics.AnalyticsEvent
import com.joon.ringout.analytics.AnalyticsParameterName
import com.joon.ringout.analytics.AnalyticsParameterValue
import com.joon.ringout.analytics.ProductAnalyticsRecorder
import com.joon.ringout.analytics.RoomAnalyticsEvent
import com.joon.ringout.analytics.roomTestRecorder
import com.joon.ringout.domain.auth.AuthSession
import com.joon.ringout.domain.room.MaxRoomImageBytes
import com.joon.ringout.domain.room.RoomImageUpload
import com.joon.ringout.domain.room.RoomMemberDetails
import com.joon.ringout.domain.room.RoomMembershipDetails
import com.joon.ringout.domain.room.RoomMembershipRole
import com.joon.ringout.domain.room.RoomRepositoryException
import com.joon.ringout.domain.room.RoomSummary
import com.joon.ringout.domain.room.RoomUpdateInput
import com.joon.ringout.domain.room.RoomUpdateResult
import com.joon.ringout.presentation.roomedit.model.validateRoomEditDescription
import com.joon.ringout.presentation.roomedit.model.validateRoomEditName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class RoomEditViewModelTest {
    @Test
    fun `조회한 활동 일정으로 초기화하고 시간과 요일 선택을 반영한다`() = runTest {
        val viewModel = loadedViewModel()
        val original = viewModel.uiState
        assertEquals(original.original?.activityDays, original.selectedDays)
        assertEquals(roomDetails().room.activityTime.take(5), original.time24Hour)
        assertFalse(original.scheduleChanged)

        viewModel.updateAmPm(false)
        viewModel.updateHour(12)
        viewModel.updateMinute(35)
        assertEquals("12:35", viewModel.uiState.time24Hour)
        viewModel.updateAmPm(true)
        assertEquals("00:35", viewModel.uiState.time24Hour)

        val day = "월"
        viewModel.toggleDay(day)
        assertEquals(day !in original.selectedDays, day in viewModel.uiState.selectedDays)
        assertTrue(viewModel.uiState.scheduleChanged)
    }

    @Test
    fun `방장 상세를 한 번 조회해 원본으로 채우고 변경 전 저장을 막는다`() = runTest {
        var loadCount = 0
        val viewModel = viewModel(
            loadRoom = {
                loadCount += 1
                roomDetails()
            },
        )

        viewModel.onRouteVisible("7")
        advanceUntilIdle()
        viewModel.onRouteVisible("7")
        advanceUntilIdle()

        assertEquals(1, loadCount)
        assertEquals("아침 러닝", viewModel.uiState.nameInput)
        assertEquals("함께 달려요.", viewModel.uiState.introductionInput)
        assertFalse(viewModel.uiState.canSave)
        assertNull(viewModel.uiState.loadErrorMessage)
    }

    @Test
    fun `이름 검증은 내부 공백을 허용하고 이모지는 막는다`() {
        val valid = validateRoomEditName("  아침 러닝 2  ")
        val invalid = validateRoomEditName("러닝🙂")

        assertTrue(valid.isValid)
        assertEquals("아침 러닝 2", valid.normalizedValue)
        assertFalse(invalid.isValid)
        assertFalse(invalid.hasOnlyAllowedCharacters)
    }

    @Test
    fun `이름 검증은 다듬은 한 글자와 스물한 글자 및 문장부호를 막는다`() {
        assertFalse(validateRoomEditName("  가  ").isLengthValid)
        assertFalse(validateRoomEditName("가".repeat(21)).isLengthValid)
        assertFalse(validateRoomEditName("러닝!").hasOnlyAllowedCharacters)
    }

    @Test
    fun `소개는 빈 값을 허용하고 이모지는 UTF16 길이 기준으로 삼백자를 넘기면 막는다`() {
        assertTrue(validateRoomEditDescription("").isValid)
        assertTrue(validateRoomEditDescription("🙂".repeat(150)).isValid)
        assertFalse(validateRoomEditDescription("🙂".repeat(151)).isValid)
    }

    @Test
    fun `저장은 변경 필드와 원본 이미지 업로드 객체를 그대로 전달하고 완료는 한 번만 소비한다`() = runTest {
        var capturedInput: RoomUpdateInput? = null
        val upload = RoomImageUpload(byteArrayOf(1, 2, 3), "image/png", "room.png")
        val viewModel = loadedViewModel(
            updateRoom = { roomId, input ->
                capturedInput = input
                RoomUpdateResult(roomId, input.name ?: "아침 러닝", input.description, "https://cdn.test/room.png")
            },
        )

        viewModel.updateName(" 저녁 러닝 ")
        viewModel.updateIntroduction("")
        viewModel.onImageSelected(3L, upload)
        viewModel.saveChanges()
        advanceUntilIdle()

        val input = assertNotNull(capturedInput)
        assertEquals("저녁 러닝", input.name)
        assertEquals("", input.description)
        assertSame(upload, input.image)
        assertFalse(input.removeImage)
        val completionId = assertNotNull(viewModel.uiState.completionId)
        val completion = assertNotNull(viewModel.consumeSuccessfulUpdate(completionId))
        assertEquals(7L, completion.result.roomId)
        assertNull(viewModel.consumeSuccessfulUpdate(completionId))
        assertFalse(viewModel.uiState.canSave)
    }

    @Test
    fun `정상 저장 결과를 적용한 뒤 방장 수정 이벤트를 한 번 기록한다`() = runTest {
        val events = mutableListOf<AnalyticsEvent>()
        val pending = CompletableDeferred<RoomUpdateResult>()
        val viewModel = loadedViewModel(
            updateRoom = { _, _ -> pending.await() },
            analytics = roomTestRecorder(events),
        )
        viewModel.updateName("저녁 러닝")
        viewModel.saveChanges()
        advanceUntilIdle()

        assertTrue(events.isEmpty())
        pending.complete(RoomUpdateResult(7, "저녁 러닝", "함께 달려요.", null))
        advanceUntilIdle()
        viewModel.consumeSuccessfulUpdate(assertNotNull(viewModel.uiState.completionId))

        assertEquals(1, events.size)
        assertEquals("room_updated", events.single().name.wireName)
        assertEquals(
            mapOf(AnalyticsParameterName.MembershipRole to AnalyticsParameterValue.Text("owner")),
            events.single().parameters,
        )
    }

    @Test
    fun `분석 기록기가 예외를 던져도 정상 저장 완료를 유지한다`() = runTest {
        val recorder = roomTestRecorder(mutableListOf())
        val throwingRecorder = object : ProductAnalyticsRecorder by recorder {
            override fun recordRoomEvent(event: RoomAnalyticsEvent) {
                error("analytics unavailable")
            }
        }
        val viewModel = loadedViewModel(
            updateRoom = { roomId, input -> RoomUpdateResult(roomId, input.name!!, input.description, null) },
            analytics = throwingRecorder,
        )

        viewModel.updateName("저녁 러닝")
        viewModel.saveChanges()
        advanceUntilIdle()

        assertEquals("저녁 러닝", viewModel.uiState.successfulUpdate?.result?.name)
        assertNull(viewModel.uiState.saveErrorMessage)
        assertFalse(viewModel.uiState.isSaving)
    }

    @Test
    fun `사백 오류는 서버 메시지를 보여주고 입력과 저장 가능 상태를 유지한다`() = runTest {
        val events = mutableListOf<AnalyticsEvent>()
        val viewModel = loadedViewModel(
            updateRoom = { _, _ -> throw RoomRepositoryException(400, "ROOM400", "모임 이름을 확인해 주세요.", "raw-result") },
            analytics = roomTestRecorder(events),
        )

        viewModel.updateName("저녁 러닝")
        viewModel.saveChanges()
        advanceUntilIdle()

        assertEquals("저녁 러닝", viewModel.uiState.nameInput)
        assertEquals("모임 이름을 확인해 주세요.", viewModel.uiState.saveErrorMessage)
        assertTrue(viewModel.uiState.canSave)
        assertTrue(events.isEmpty())
    }

    @Test
    fun `오백 오류는 고정 안내를 보여주고 재시도할 수 있게 입력을 보존한다`() = runTest {
        val viewModel = loadedViewModel(
            updateRoom = { _, _ -> throw RoomRepositoryException(500, "ROOM500", "stack detail") },
        )

        viewModel.updateIntroduction("새 소개")
        viewModel.saveChanges()
        advanceUntilIdle()

        assertEquals("새 소개", viewModel.uiState.introductionInput)
        assertEquals("서버 문제로 모임 정보를 저장하지 못했어요. 잠시 후 다시 시도해 주세요.", viewModel.uiState.saveErrorMessage)
        assertTrue(viewModel.uiState.canSave)
    }

    @Test
    fun `권한 오류는 안내를 유지하고 추가 편집 뒤에도 저장을 막는다`() = runTest {
        val viewModel = loadedViewModel(
            updateRoom = { _, _ -> throw RoomRepositoryException(403, "COMMON403", "금지") },
        )

        viewModel.updateName("저녁 러닝")
        viewModel.saveChanges()
        advanceUntilIdle()
        viewModel.updateName("밤 러닝")

        assertEquals("모임을 수정할 수 있는 방장 권한이 없어요.", viewModel.uiState.saveErrorMessage)
        assertFalse(viewModel.uiState.canSave)
    }

    @Test
    fun `계정 변경 뒤 늦게 끝난 저장 응답은 완료 이벤트로 남기지 않는다`() = runTest {
        val pending = CompletableDeferred<RoomUpdateResult>()
        val events = mutableListOf<AnalyticsEvent>()
        val session = authenticatedSession()
        val viewModel = loadedViewModel(
            authSession = session,
            updateRoom = { _, _ -> pending.await() },
            analytics = roomTestRecorder(events),
        )

        viewModel.updateName("저녁 러닝")
        viewModel.saveChanges()
        session.startNewSession()
        pending.complete(RoomUpdateResult(7, "저녁 러닝", "함께 달려요.", null))
        advanceUntilIdle()

        assertNull(viewModel.uiState.successfulUpdate)
        assertTrue(events.isEmpty())
    }

    @Test
    fun `저장 중 연속 저장은 패치를 한 번만 보낸다`() = runTest {
        val pending = CompletableDeferred<RoomUpdateResult>()
        var saveCount = 0
        val viewModel = loadedViewModel(
            updateRoom = { roomId, input ->
                saveCount += 1
                pending.await()
                RoomUpdateResult(roomId, input.name ?: "아침 러닝", input.description, null)
            },
        )

        viewModel.updateName("저녁 러닝")
        viewModel.saveChanges()
        viewModel.saveChanges()
        advanceUntilIdle()

        assertEquals(1, saveCount)
        pending.complete(RoomUpdateResult(7, "저녁 러닝", "함께 달려요.", null))
        advanceUntilIdle()
    }

    @Test
    fun `네트워크 실패 뒤 재시도해도 선택한 이미지 업로드와 토큰을 유지한다`() = runTest {
        val upload = RoomImageUpload(byteArrayOf(1, 2, 3), "image/png", "room.png")
        val capturedUploads = mutableListOf<RoomImageUpload?>()
        val events = mutableListOf<AnalyticsEvent>()
        var fail = true
        val viewModel = loadedViewModel(
            analytics = roomTestRecorder(events),
            updateRoom = { roomId, input ->
                capturedUploads += input.image
                if (fail) {
                    fail = false
                    throw IllegalStateException("network")
                }
                RoomUpdateResult(roomId, input.name ?: "아침 러닝", input.description, "new-url")
            },
        )

        viewModel.onImageSelected(4L, upload)
        viewModel.saveChanges()
        advanceUntilIdle()
        assertEquals(4L, viewModel.uiState.imageSelectionToken)
        assertTrue(viewModel.uiState.canSave)

        viewModel.saveChanges()
        advanceUntilIdle()

        assertEquals(2, capturedUploads.size)
        assertSame(upload, capturedUploads[0])
        assertSame(upload, capturedUploads[1])
        assertNotNull(viewModel.uiState.successfulUpdate)
        assertEquals(1, events.size)
        assertEquals("room_updated", events.single().name.wireName)
    }

    @Test
    fun `방장이 아니면 원본을 노출하지 않고 재시도 없는 권한 안내를 보여준다`() = runTest {
        val viewModel = viewModel(loadRoom = { roomDetails(role = RoomMembershipRole.MEMBER) })

        viewModel.onRouteVisible("7")
        advanceUntilIdle()

        assertFalse(viewModel.uiState.isOriginalLoaded)
        assertEquals("모임을 수정할 수 있는 방장 권한이 없어요.", viewModel.uiState.loadErrorMessage)
        assertFalse(viewModel.uiState.canRetryLoad)
    }

    @Test
    fun `이미지만 수정하면 유효하지 않은 원본 이름과 긴 원본 소개가 있어도 저장할 수 있다`() = runTest {
        var capturedInput: RoomUpdateInput? = null
        val upload = RoomImageUpload(byteArrayOf(9), "image/png", "room.png")
        val viewModel = viewModel(
            loadRoom = {
                roomDetails().copy(
                    room = roomDetails().room.copy(
                        name = "나쁜🙂이름",
                        description = "🙂".repeat(151),
                    ),
                )
            },
            updateRoom = { roomId, input ->
                capturedInput = input
                RoomUpdateResult(roomId, input.name ?: "나쁜🙂이름", input.description ?: "🙂".repeat(151), "new-url")
            },
        )
        viewModel.onRouteVisible("7")
        advanceUntilIdle()

        viewModel.onImageSelected(1L, upload)
        viewModel.saveChanges()
        advanceUntilIdle()

        val input = assertNotNull(capturedInput)
        assertTrue(input.hasChanges)
        assertNull(input.name)
        assertNull(input.description)
        assertSame(upload, input.image)
    }

    @Test
    fun `모임 이미지 업로드는 5MB 경계까지 허용하고 초과하면 거부한다`() {
        RoomImageUpload(ByteArray(MaxRoomImageBytes.toInt()), "image/jpeg", "room.jpg")

        assertFailsWith<IllegalArgumentException> {
            RoomImageUpload(ByteArray(MaxRoomImageBytes.toInt() + 1), "image/jpeg", "room.jpg")
        }
    }

    private suspend fun TestScope.loadedViewModel(
        authSession: AuthSession = authenticatedSession(),
        analytics: ProductAnalyticsRecorder? = null,
        updateRoom: suspend (Long, RoomUpdateInput) -> RoomUpdateResult = { roomId, input ->
            RoomUpdateResult(roomId, input.name ?: "아침 러닝", input.description ?: "함께 달려요.", null)
        },
    ): RoomEditViewModel {
        val viewModel = viewModel(authSession = authSession, updateRoom = updateRoom, analytics = analytics)
        viewModel.onRouteVisible("7")
        advanceUntilIdle()
        return viewModel
    }

    private fun TestScope.viewModel(
        authSession: AuthSession = authenticatedSession(),
        loadRoom: suspend (Long) -> RoomMembershipDetails = { roomDetails() },
        analytics: ProductAnalyticsRecorder? = null,
        updateRoom: suspend (Long, RoomUpdateInput) -> RoomUpdateResult = { roomId, input ->
            RoomUpdateResult(roomId, input.name ?: "아침 러닝", input.description ?: "함께 달려요.", null)
        },
    ) = RoomEditViewModel(
        loadRoom = loadRoom,
        updateRoom = updateRoom,
        authSession = authSession,
        coroutineScope = this,
        analytics = analytics,
    )

    private fun authenticatedSession() = AuthSession().apply { startNewSession() }

    private fun roomDetails(
        role: RoomMembershipRole = RoomMembershipRole.OWNER,
    ) = RoomMembershipDetails(
        room = RoomSummary(
            id = 7,
            name = "아침 러닝",
            description = "함께 달려요.",
            imageUrl = null,
            activityDays = listOf("MONDAY", "WEDNESDAY", "FRIDAY"),
            activityTime = "08:00",
            memberCount = 1,
            isJoined = true,
            createdAt = "2026-09-15T09:00:00",
        ),
        membershipRole = role,
        members = listOf(RoomMemberDetails(1, "방장")),
    )
}
