package com.joon.ringout.push

import android.content.Intent
import androidx.lifecycle.SavedStateHandle
import com.joon.ringout.domain.push.RoomActivityPushTarget
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RoomActivityPushClickViewModelTest {
    @Test
    fun `업데이트 차단 중 알람에서 앱으로 돌아와도 대기 중인 알림 요청을 보존한다`() {
        val savedState = SavedStateHandle()
        val viewModel = RoomActivityPushClickViewModel(savedState)
        viewModel.receive(
            Intent()
                .putExtra(RoomActivityPushTarget.RoomIdKey, "41")
                .putExtra(RoomActivityPushTarget.ActivityDateKey, "2026-10-07")
                .putExtra("google.message_id", "waiting-message"),
            isNewIntent = true,
            preservePendingRequest = true,
        )
        val pending = viewModel.pendingRequest

        viewModel.receive(Intent(Intent.ACTION_MAIN), isNewIntent = true, preservePendingRequest = true)
        val restored = RoomActivityPushClickViewModel(
            SavedStateHandle(savedState.keys().associateWith { savedState.get<Any?>(it) }),
        )

        assertEquals("fcm:waiting-message", pending?.requestId)
        assertEquals(pending, restored.pendingRequest)
        restored.acknowledge(requireNotNull(pending).requestId)
        assertNull(restored.pendingRequest)
    }

    @Test
    fun `FCM 시작 intent를 한 번 보관하고 저장 상태 복원 후 중복 수락하지 않는다`() {
        val intent = Intent()
            .putExtra(RoomActivityPushTarget.RoomIdKey, "41")
            .putExtra(RoomActivityPushTarget.ActivityDateKey, "2026-10-07")
            .putExtra("google.message_id", "message-a")
        val savedState = SavedStateHandle()
        val firstViewModel = RoomActivityPushClickViewModel(savedState)

        firstViewModel.receive(intent, isNewIntent = false)

        val request = firstViewModel.pendingRequest
        assertEquals("fcm:message-a", request?.requestId)
        assertEquals("41", request?.target?.roomId)
        assertEquals("2026-10-07", request?.target?.activityDate)

        val restoredState = SavedStateHandle(
            savedState.keys().associateWith { key -> savedState.get<Any?>(key) },
        )
        val restoredViewModel = RoomActivityPushClickViewModel(restoredState)
        restoredViewModel.receive(intent, isNewIntent = false)

        assertEquals(request, restoredViewModel.pendingRequest)
        restoredViewModel.acknowledge("old-click")
        assertEquals(request, restoredViewModel.pendingRequest)
        restoredViewModel.acknowledge("fcm:message-a")
        assertNull(restoredViewModel.pendingRequest)

        val afterConsumption = SavedStateHandle(
            restoredState.keys().associateWith { key -> restoredState.get<Any?>(key) },
        )
        val consumedViewModel = RoomActivityPushClickViewModel(afterConsumption)
        consumedViewModel.receive(intent, isNewIntent = false)
        assertNull(consumedViewModel.pendingRequest)
    }

    @Test
    fun `마지막 클릭이 우선하고 이전 확인은 새 클릭을 소비하지 않는다`() {
        val viewModel = RoomActivityPushClickViewModel(SavedStateHandle())
        val first = Intent()
            .putExtra(PushNotifications.EXTRA_REQUEST_ID, "click-a")
            .putExtra(RoomActivityPushTarget.RoomIdKey, "41")
            .putExtra(RoomActivityPushTarget.ActivityDateKey, "2026-10-07")
        val second = Intent()
            .putExtra(PushNotifications.EXTRA_REQUEST_ID, "click-b")
            .putExtra(RoomActivityPushTarget.RoomIdKey, "42")
            .putExtra(RoomActivityPushTarget.ActivityDateKey, "2026-10-08")

        viewModel.receive(first, isNewIntent = true)
        viewModel.receive(second, isNewIntent = true)
        viewModel.acknowledge("click-a")

        assertEquals("click-b", viewModel.pendingRequest?.requestId)
        assertEquals("42", viewModel.pendingRequest?.target?.roomId)
    }

    @Test
    fun `잘못된 새 알림 클릭은 이전 대기 요청을 지우고 일반 진입으로 남는다`() {
        val viewModel = RoomActivityPushClickViewModel(SavedStateHandle())
        viewModel.receive(
            Intent()
                .putExtra(PushNotifications.EXTRA_REQUEST_ID, "click-a")
                .putExtra(RoomActivityPushTarget.RoomIdKey, "41")
                .putExtra(RoomActivityPushTarget.ActivityDateKey, "2026-10-07"),
            isNewIntent = true,
        )

        viewModel.receive(
            Intent()
                .putExtra(PushNotifications.EXTRA_REQUEST_ID, "click-b")
                .putExtra(RoomActivityPushTarget.RoomIdKey, "not-a-room"),
            isNewIntent = true,
        )

        assertNull(viewModel.pendingRequest)
    }
}
