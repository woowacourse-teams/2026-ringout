package com.joon.ringout.presentation.onboarding

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OnboardingStateTest {
    @Test
    fun `권한 페이지에 완전히 도착한 뒤 최초 한 번만 자동으로 요청한다`() {
        val flow = locationFlow()
        assertEquals(OnboardingAdvance.None, flow.requestPermissionOnEntry())
        flow.onStepVisible(OnboardingStep.LocationPermission.ordinal)
        assertEquals(OnboardingAdvance.RequestLocationPermission, flow.requestPermissionOnEntry())
        repeat(3) {
            flow.onStepVisible(OnboardingStep.LocationPermission.ordinal)
            assertEquals(OnboardingAdvance.None, flow.requestPermissionOnEntry())
        }
        assertEquals(1, flow.uiState.value.permissionRequestId)
    }

    @Test
    fun `페이지 진입 후 권한 상태가 늦게 확인되어도 자동 요청한다`() {
        val flow = OnboardingViewModel()
        flow.onPageChanged(OnboardingStep.LocationPermission.ordinal)
        flow.onStepVisible(OnboardingStep.LocationPermission.ordinal)
        assertEquals(OnboardingAdvance.None, flow.requestPermissionOnEntry())
        flow.onPermissionsRefreshed(requestable)
        assertEquals(OnboardingAdvance.RequestLocationPermission, flow.requestPermissionOnEntry())
    }

    @Test
    fun `자동 요청을 거부하면 복귀와 재방문에도 다시 요청하지 않고 다음 버튼으로 진행한다`() {
        val flow = locationFlow()
        flow.onStepVisible(OnboardingStep.LocationPermission.ordinal)
        flow.requestPermissionOnEntry()
        flow.respond(OnboardingPermission.Location, requestable)
        repeat(3) {
            flow.onPermissionsRefreshed(requestable)
            flow.onStepVisible(OnboardingStep.LocationPermission.ordinal)
            assertEquals(OnboardingAdvance.None, flow.requestPermissionOnEntry())
        }
        flow.back()
        flow.onStepVisible(OnboardingStep.Departure.ordinal)
        flow.requestNext()
        flow.onStepVisible(OnboardingStep.LocationPermission.ordinal)
        assertEquals(OnboardingAdvance.None, flow.requestPermissionOnEntry())
        assertEquals(OnboardingAdvance.None, flow.requestNext())
        assertEquals(OnboardingStep.NotificationPermission, flow.uiState.value.step)
        assertEquals(1, flow.uiState.value.permissionRequestId)
    }

    @Test
    fun `허용된 권한과 설정이 필요한 권한은 진입만으로 요청하거나 설정을 열지 않는다`() {
        listOf(
            OnboardingPermissionStatus.Granted,
            OnboardingPermissionStatus.SettingsRequired,
            OnboardingPermissionStatus.Restricted,
            OnboardingPermissionStatus.ServicesDisabled,
            OnboardingPermissionStatus.Unavailable,
        ).forEach { status ->
            val flow = locationFlow()
            flow.onPermissionsRefreshed(requestable.copy(location = status))
            flow.onStepVisible(OnboardingStep.LocationPermission.ordinal)
            assertEquals(OnboardingAdvance.None, flow.requestPermissionOnEntry())
            assertNull(flow.uiState.value.requestingPermission)
            assertEquals(OnboardingStep.LocationPermission, flow.uiState.value.step)
        }
    }

    @Test
    fun `자동 요청이 시작되기 전 다음 버튼을 눌러도 최초 요청을 건너뛰지 않는다`() {
        listOf(locationFlow(), notificationFlow()).forEach { flow ->
            val initialStep = flow.uiState.value.step
            repeat(3) { assertEquals(OnboardingAdvance.None, flow.requestNext()) }
            flow.onStepVisible(initialStep.ordinal)
            assertEquals(OnboardingAdvance.None, flow.requestNext())
            assertEquals(initialStep, flow.uiState.value.step)
            assertNull(flow.uiState.value.requestingPermission)
            flow.requestPermissionOnEntry()
            assertEquals(initialStep.permission, flow.uiState.value.requestingPermission)
            assertEquals(1, flow.uiState.value.permissionRequestId)
        }
    }

    @Test
    fun `위치를 허용해도 알림 화면 전환이 끝나기 전에는 알림을 요청하지 않는다`() {
        val flow = locationFlow()
        flow.onStepVisible(OnboardingStep.LocationPermission.ordinal)
        flow.requestPermissionOnEntry()
        flow.respond(OnboardingPermission.Location, locationGranted)
        assertEquals(OnboardingStep.NotificationPermission, flow.uiState.value.step)
        assertEquals(OnboardingAdvance.None, flow.requestPermissionOnEntry())
        flow.onStepVisible(OnboardingStep.LocationPermission.ordinal)
        assertEquals(OnboardingAdvance.None, flow.requestPermissionOnEntry())
        flow.onStepVisible(OnboardingStep.NotificationPermission.ordinal)
        assertEquals(OnboardingAdvance.RequestNotificationPermission, flow.requestPermissionOnEntry())
    }

    @Test
    fun `서비스 소개 네 페이지 뒤 위치와 알림 권한을 순서대로 허용하면 자동으로 완료한다`() {
        val flow = OnboardingViewModel()
        flow.onPermissionsRefreshed(requestable)
        OnboardingStep.entries.take(4).forEachIndexed { index, step ->
            assertEquals(step, flow.uiState.value.step)
            assertEquals(index, flow.uiState.value.currentStepIndex)
            assertFalse(flow.uiState.value.isLastStep)
            assertEquals(OnboardingAdvance.None, flow.requestNext())
        }
        assertEquals(OnboardingStep.LocationPermission, flow.uiState.value.step)
        flow.onStepVisible(OnboardingStep.LocationPermission.ordinal)
        assertEquals(OnboardingAdvance.RequestLocationPermission, flow.requestPermissionOnEntry())
        assertEquals(OnboardingAdvance.None, flow.respond(OnboardingPermission.Location, locationGranted))
        assertEquals(OnboardingStep.NotificationPermission, flow.uiState.value.step)
        assertFalse(flow.uiState.value.isCompletionRequested)
        flow.onStepVisible(OnboardingStep.NotificationPermission.ordinal)
        assertEquals(OnboardingAdvance.RequestNotificationPermission, flow.requestPermissionOnEntry())
        assertEquals(OnboardingAdvance.Complete, flow.respond(OnboardingPermission.Notifications, granted))
        assertTrue(flow.uiState.value.isCompletionRequested)
    }

    @Test
    fun `두 권한을 모두 거부해도 각각 다음 버튼을 누르면 완료한다`() {
        val flow = locationFlow()
        flow.startAutomaticRequest()
        flow.respond(OnboardingPermission.Location, requestable)
        assertEquals(OnboardingAdvance.None, flow.requestNext())
        assertEquals(OnboardingStep.NotificationPermission, flow.uiState.value.step)
        flow.startAutomaticRequest()
        flow.respond(OnboardingPermission.Notifications, requestable)
        assertFalse(flow.uiState.value.isCompletionRequested)
        assertEquals(OnboardingAdvance.Complete, flow.requestNext())
        assertTrue(flow.uiState.value.isCompletionRequested)
        assertEquals(requestable, flow.uiState.value.permissions)
        assertEquals(2, flow.uiState.value.permissionRequestId)
    }

    @Test
    fun `이미 허용되거나 설정이 필요한 권한은 다음 버튼으로 요청 없이 진행한다`() {
        listOf(
            OnboardingPermissionStatus.Granted,
            OnboardingPermissionStatus.SettingsRequired,
            OnboardingPermissionStatus.Restricted,
            OnboardingPermissionStatus.ServicesDisabled,
            OnboardingPermissionStatus.Unavailable,
        ).forEach { status ->
            val flow = locationFlow()
            flow.onPermissionsRefreshed(OnboardingPermissionState(status, status))
            assertEquals(OnboardingAdvance.None, flow.requestNext())
            assertEquals(OnboardingStep.NotificationPermission, flow.uiState.value.step)
            assertEquals(OnboardingAdvance.Complete, flow.requestNext())
            assertNull(flow.uiState.value.requestingPermission)
            assertEquals(0, flow.uiState.value.permissionRequestId)
        }
    }

    @Test
    fun `권한 상태 조회 전에는 권한 요청과 완료를 시작하지 않는다`() {
        val flow = OnboardingViewModel()
        listOf(OnboardingStep.LocationPermission, OnboardingStep.NotificationPermission).forEach { step ->
            flow.onPageChanged(step.ordinal)
            assertEquals(OnboardingAdvance.None, flow.requestNext())
            assertEquals(step, flow.uiState.value.step)
            assertNull(flow.uiState.value.requestingPermission)
            assertFalse(flow.uiState.value.isCompletionRequested)
        }
    }

    @Test
    fun `권한 응답을 기다리는 동안 연속 클릭과 뒤로 가기는 단계를 바꾸지 않는다`() {
        val flow = locationFlow()
        assertEquals(OnboardingAdvance.RequestLocationPermission, flow.startAutomaticRequest())
        repeat(3) { assertEquals(OnboardingAdvance.None, flow.requestNext()) }
        flow.back()
        flow.onPageChanged(0)
        assertEquals(OnboardingStep.LocationPermission, flow.uiState.value.step)
        assertEquals(1, flow.uiState.value.permissionRequestId)
    }

    @Test
    fun `위치 거부와 서비스 꺼짐과 요청 오류는 완료하지 않고 같은 화면에 남는다`() {
        listOf(
            OnboardingPermissionStatus.Requestable,
            OnboardingPermissionStatus.SettingsRequired,
            OnboardingPermissionStatus.Restricted,
            OnboardingPermissionStatus.ServicesDisabled,
            OnboardingPermissionStatus.Unavailable,
        ).forEach { status ->
            val flow = locationFlow()
            flow.startAutomaticRequest()
            assertEquals(OnboardingAdvance.None, flow.respond(OnboardingPermission.Location, requestable.copy(location = status)))
            assertEquals(OnboardingStep.LocationPermission, flow.uiState.value.step)
            assertEquals(status, flow.uiState.value.permissionStatus)
            assertNull(flow.uiState.value.requestingPermission)
            assertFalse(flow.uiState.value.isCompletionRequested)
            assertEquals(OnboardingAdvance.None, flow.requestNext())
            assertEquals(OnboardingStep.NotificationPermission, flow.uiState.value.step)
            assertNull(flow.uiState.value.requestingPermission)
        }
    }

    @Test
    fun `알림 거부 후 다음 버튼을 누르면 권한 재요청 없이 완료한다`() {
        val flow = notificationFlow()
        flow.startAutomaticRequest()
        assertEquals(OnboardingAdvance.None, flow.respond(OnboardingPermission.Notifications, locationGranted.copy(
            notifications = OnboardingPermissionStatus.SettingsRequired,
        )))
        assertEquals(OnboardingStep.NotificationPermission, flow.uiState.value.step)
        assertFalse(flow.uiState.value.isCompletionRequested)
        assertEquals(OnboardingAdvance.Complete, flow.requestNext())
        assertEquals(1, flow.uiState.value.permissionRequestId)
        assertNull(flow.uiState.value.requestingPermission)
    }

    @Test
    fun `위치 권한을 거부한 뒤 알림 권한을 허용해도 자동으로 완료한다`() {
        val flow = locationFlow()
        flow.startAutomaticRequest()
        val locationDenied = requestable.copy(location = OnboardingPermissionStatus.SettingsRequired)
        flow.respond(OnboardingPermission.Location, locationDenied)
        flow.requestNext()
        flow.startAutomaticRequest()
        assertEquals(OnboardingAdvance.Complete, flow.respond(OnboardingPermission.Notifications, locationDenied.copy(
            notifications = OnboardingPermissionStatus.Granted,
        )))
        assertEquals(OnboardingStep.NotificationPermission, flow.uiState.value.step)
        assertTrue(flow.uiState.value.isCompletionRequested)
    }

    @Test
    fun `이전 요청의 늦은 응답이나 중복 응답은 다음 요청을 완료하지 않는다`() {
        val flow = locationFlow()
        flow.startAutomaticRequest()
        val previousId = flow.uiState.value.permissionRequestId
        flow.respond(OnboardingPermission.Location, requestable)
        flow.requestNext()
        flow.startAutomaticRequest()
        assertEquals(OnboardingAdvance.None, flow.onPermissionResult(OnboardingPermission.Location, previousId, granted))
        assertEquals(OnboardingAdvance.None, flow.onPermissionResult(OnboardingPermission.Notifications, previousId, granted))
        assertEquals(OnboardingStep.NotificationPermission, flow.uiState.value.step)
        assertEquals(OnboardingPermission.Notifications, flow.uiState.value.requestingPermission)
        assertFalse(flow.uiState.value.isCompletionRequested)
        flow.respond(OnboardingPermission.Notifications, requestable)
        assertEquals(OnboardingAdvance.None, flow.respond(OnboardingPermission.Notifications, granted))
        assertFalse(flow.uiState.value.isCompletionRequested)
    }

    @Test
    fun `권한 요청 전 시작한 조회 결과가 늦게 와도 승인 결과를 덮어쓰지 않는다`() {
        val flow = locationFlow()
        val oldRequestId = flow.uiState.value.permissionRequestId
        flow.startAutomaticRequest()
        flow.onPermissionsRefreshed(granted, oldRequestId)
        assertEquals(OnboardingPermissionStatus.Requestable, flow.uiState.value.permissions.location)
        flow.respond(OnboardingPermission.Location, locationGranted)
        flow.onPermissionsRefreshed(requestable, oldRequestId)
        assertEquals(OnboardingPermissionStatus.Granted, flow.uiState.value.permissions.location)
    }

    @Test
    fun `소개 페이지의 스와이프와 다음 버튼과 뒤로 가기를 이어서 사용할 수 있다`() {
        val flow = OnboardingViewModel()
        flow.onPageChanged(2)
        flow.back()
        assertEquals(OnboardingStep.Destination, flow.uiState.value.step)
        assertEquals(OnboardingAdvance.None, flow.requestNext())
        assertEquals(OnboardingStep.Interval, flow.uiState.value.step)
        flow.onPageChanged(0)
        assertEquals(OnboardingStep.Introduction, flow.uiState.value.step)
    }

    @Test
    fun `첫 페이지의 뒤로 가기와 범위를 벗어난 페이지 변경은 무시한다`() {
        val flow = OnboardingViewModel()
        flow.back()
        flow.onPageChanged(-1)
        flow.onPageChanged(6)
        assertEquals(OnboardingStep.Introduction, flow.uiState.value.step)
        flow.onPageChanged(2)
        flow.onPageChanged(Int.MAX_VALUE)
        assertEquals(OnboardingStep.Interval, flow.uiState.value.step)
    }

    @Test
    fun `완료 저장 중에는 같은 토큰의 재요청과 페이지 이동을 무시한다`() {
        val flow = notificationFlow()
        flow.startAutomaticRequest()
        assertEquals(OnboardingAdvance.Complete, flow.respond(OnboardingPermission.Notifications, granted))
        repeat(3) { assertEquals(OnboardingAdvance.None, flow.requestNext()) }
        flow.back()
        flow.onPageChanged(0)
        assertEquals(OnboardingStep.NotificationPermission, flow.uiState.value.step)
        assertTrue(flow.uiState.value.isCompletionRequested)
    }

    @Test
    fun `완료 저장 실패 후에는 새 토큰만 재시도할 수 있다`() {
        val flow = notificationFlow()
        flow.startAutomaticRequest()
        flow.respond(OnboardingPermission.Notifications, granted)
        assertEquals(OnboardingAdvance.Complete, flow.requestNext(1))
        assertEquals(OnboardingAdvance.None, flow.requestNext(1))
        assertEquals(OnboardingAdvance.None, flow.requestNext(0))
        assertEquals(OnboardingAdvance.Complete, flow.requestNext(2))
    }

    @Test
    fun `권한 없이 완료해도 저장 실패는 새 토큰으로만 재시도한다`() {
        val flow = notificationFlow()
        flow.onPermissionsRefreshed(OnboardingPermissionState(
            OnboardingPermissionStatus.SettingsRequired,
            OnboardingPermissionStatus.SettingsRequired,
        ))
        assertEquals(OnboardingAdvance.Complete, flow.requestNext())
        assertTrue(flow.onSubmitAccepted())
        assertFalse(flow.onSubmitAccepted())
        assertEquals(OnboardingAdvance.None, flow.requestNext())
        assertEquals(OnboardingAdvance.Complete, flow.requestNext(1))
        assertTrue(flow.onSubmitAccepted())
        assertEquals(OnboardingAdvance.None, flow.requestNext(1))
        assertEquals(OnboardingAdvance.None, flow.requestNext(0))
        assertEquals(0, flow.uiState.value.permissionRequestId)
    }

    @Test
    fun `음수 재시도 토큰으로는 권한이 허용되어도 완료하지 않는다`() {
        val flow = notificationFlow()
        flow.startAutomaticRequest()
        assertEquals(OnboardingAdvance.None, flow.onPermissionResult(
            OnboardingPermission.Notifications, flow.uiState.value.permissionRequestId, granted, -1,
        ))
        assertFalse(flow.uiState.value.isCompletionRequested)
    }

    private fun locationFlow() = OnboardingViewModel().apply {
        onPermissionsRefreshed(requestable)
        onPageChanged(OnboardingStep.LocationPermission.ordinal)
    }

    private fun notificationFlow() = OnboardingViewModel().apply {
        onPermissionsRefreshed(locationGranted)
        onPageChanged(OnboardingStep.NotificationPermission.ordinal)
    }

    private fun OnboardingViewModel.startAutomaticRequest(): OnboardingAdvance {
        onStepVisible(uiState.value.currentStepIndex)
        return requestPermissionOnEntry()
    }

    private fun OnboardingViewModel.respond(permission: OnboardingPermission, permissions: OnboardingPermissionState) =
        onPermissionResult(permission, uiState.value.permissionRequestId, permissions)

    private val requestable = OnboardingPermissionState(
        OnboardingPermissionStatus.Requestable, OnboardingPermissionStatus.Requestable,
    )
    private val locationGranted = requestable.copy(location = OnboardingPermissionStatus.Granted)
    private val granted = OnboardingPermissionState(OnboardingPermissionStatus.Granted, OnboardingPermissionStatus.Granted)
}
