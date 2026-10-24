package com.joon.ringout.domain.push

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RoomActivityPushTargetTest {
    @Test
    fun `유효한 모임 ID와 날짜를 정규화해 푸시 목적지를 만든다`() {
        val target = RoomActivityPushTarget.parse(
            mapOf(
                "roomId" to "00041",
                "activityDate" to "2026-10-07",
            ),
        )

        assertEquals(RoomActivityPushTarget.parse(validData("41")), target)
        assertEquals("41", target?.roomId)
        assertEquals("2026-10-07", target?.activityDate)
    }

    @Test
    fun `필수 값이 없거나 공백인 푸시 데이터는 목적지를 만들지 않는다`() {
        assertNull(RoomActivityPushTarget.parse(emptyMap()))
        assertNull(RoomActivityPushTarget.parse(mapOf("roomId" to "41")))
        assertNull(RoomActivityPushTarget.parse(mapOf("activityDate" to "2026-10-07")))
        assertNull(RoomActivityPushTarget.parse(validData(" 41")))
        assertNull(RoomActivityPushTarget.parse(validData("41 ")))
    }

    @Test
    fun `양수가 아니거나 Long 범위를 벗어난 모임 ID는 거부한다`() {
        listOf("", "0", "-1", "+1", "9223372036854775808").forEach { roomId ->
            assertNull(RoomActivityPushTarget.parse(validData(roomId)), roomId)
        }
    }

    @Test
    fun `존재하지 않거나 ISO 형식이 아닌 활동 날짜는 거부한다`() {
        listOf("2026-02-30", "2026-2-03", " 2026-10-07", "2026-10-07 ").forEach { date ->
            assertNull(RoomActivityPushTarget.parse(validData("41", date)), date)
        }
    }

    @Test
    fun `요청 ID가 없는 푸시 클릭은 수락하지 않는다`() {
        val state = RoomActivityPushRequestState()

        assertFalse(state.accept(" ", target()))
        assertNull(state.pendingRequest)
    }

    @Test
    fun `나중에 클릭한 요청이 우선하고 이전 요청의 확인은 새 요청을 지우지 않는다`() {
        val state = RoomActivityPushRequestState()
        val first = target("41", "2026-10-07")
        val second = target("42", "2026-10-08")

        assertTrue(state.accept("click-a", first))
        assertTrue(state.accept("click-b", second))
        assertFalse(state.acknowledge("click-a"))
        assertEquals(RoomActivityPushRequest("click-b", second), state.pendingRequest)
    }

    @Test
    fun `저장된 요청은 같은 launch intent에서 다시 수락하지 않는다`() {
        val target = target()
        val request = RoomActivityPushRequest("click-a", target)
        val restored = RoomActivityPushRequestState(
            pendingRequest = request,
            lastAcceptedRequestId = "click-a",
        )

        assertFalse(restored.accept("click-a", target("42", "2026-10-08")))
        assertEquals(request, restored.pendingRequest)
    }

    @Test
    fun `같은 목적지를 새로 클릭하면 새 요청으로 보관한다`() {
        val state = RoomActivityPushRequestState()
        val target = target()

        assertTrue(state.accept("click-a", target))
        assertTrue(state.acknowledge("click-a"))
        assertTrue(state.accept("click-b", target))
        assertEquals(RoomActivityPushRequest("click-b", target), state.pendingRequest)
    }

    @Test
    fun `잘못된 새 클릭은 이전 대기 요청을 지운다`() {
        val state = RoomActivityPushRequestState()
        state.accept("click-a", target())

        assertTrue(state.accept("click-b", null))
        assertNull(state.pendingRequest)
        assertEquals("click-b", state.lastAcceptedRequestId)
    }

    @Test
    fun `요청을 확인한 뒤 저장 상태에서 복원해도 소비된 요청을 되살리지 않는다`() {
        val state = RoomActivityPushRequestState()
        state.accept("click-a", target())
        assertTrue(state.acknowledge("click-a"))

        val restored = RoomActivityPushRequestState(
            pendingRequest = state.pendingRequest,
            lastAcceptedRequestId = state.lastAcceptedRequestId,
        )

        assertNull(restored.pendingRequest)
        assertFalse(restored.accept("click-a", target()))
    }

    private fun validData(roomId: String, date: String = "2026-10-07") = mapOf(
        "roomId" to roomId,
        "activityDate" to date,
    )

    private fun target(roomId: String = "41", date: String = "2026-10-07") =
        requireNotNull(RoomActivityPushTarget.parse(validData(roomId, date)))
}
