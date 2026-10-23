package com.joon.ringout.presentation.onboarding

import com.joon.ringout.platform.IosOnboardingPermissionService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IosOnboardingPermissionControllerTest {
    @Test
    fun `현재 위치와 알림 권한 조회 결과를 한 번만 전달한다`() {
        val service = FakePermissionService()
        val controller = IosOnboardingPermissionController(service)
        val results = mutableListOf<OnboardingPermissionState>()
        val state = OnboardingPermissionState(
            location = OnboardingPermissionStatus.Granted,
            notifications = OnboardingPermissionStatus.SettingsRequired,
        )

        controller.refresh(results::add)
        service.refreshCallbacks.single()(state)
        service.refreshCallbacks.single()(state.copy(location = OnboardingPermissionStatus.Unknown))

        assertEquals(listOf(state), results)
        assertTrue(service.requests.isEmpty())
    }

    @Test
    fun `설정 화면에서 복귀한 실제 권한 결과를 받을 때까지 요청을 완료하지 않는다`() {
        val service = FakePermissionService()
        val controller = IosOnboardingPermissionController(service)
        val results = mutableListOf<OnboardingPermissionStatus>()

        controller.request(OnboardingPermission.Location, results::add)
        assertTrue(results.isEmpty())

        service.requests.single().callback(OnboardingPermissionStatus.SettingsRequired)

        assertEquals(listOf(OnboardingPermissionStatus.SettingsRequired), results)
    }

    @Test
    fun `이미 허용된 권한은 네이티브의 즉시 승인 결과를 전달한다`() {
        val service = FakePermissionService().apply {
            immediateResult = OnboardingPermissionStatus.Granted
        }
        val controller = IosOnboardingPermissionController(service)
        val results = mutableListOf<OnboardingPermissionStatus>()

        controller.request(OnboardingPermission.Notifications, results::add)

        assertEquals(listOf(OnboardingPermissionStatus.Granted), results)
    }

    @Test
    fun `같은 권한을 연속 요청해도 시스템 요청은 하나만 시작한다`() {
        val service = FakePermissionService()
        val controller = IosOnboardingPermissionController(service)
        val results = mutableListOf<OnboardingPermissionStatus>()

        controller.request(OnboardingPermission.Location, results::add)
        controller.request(OnboardingPermission.Location, results::add)
        assertEquals(1, service.requests.size)
        service.requests.single().callback(OnboardingPermissionStatus.Granted)

        assertEquals(List(2) { OnboardingPermissionStatus.Granted }, results)
    }

    @Test
    fun `위치 권한 요청 중 알림을 요청해도 두 시스템 요청을 겹치지 않는다`() {
        val service = FakePermissionService()
        val controller = IosOnboardingPermissionController(service)
        val locationResults = mutableListOf<OnboardingPermissionStatus>()
        val notificationResults = mutableListOf<OnboardingPermissionStatus>()

        controller.request(OnboardingPermission.Location, locationResults::add)
        controller.request(OnboardingPermission.Notifications, notificationResults::add)

        assertEquals(listOf(OnboardingPermission.Location), service.requests.map { it.permission })
        assertEquals(listOf(OnboardingPermissionStatus.Unavailable), notificationResults)
        assertTrue(locationResults.isEmpty())

        service.requests.single().callback(OnboardingPermissionStatus.Granted)
        controller.request(OnboardingPermission.Notifications, notificationResults::add)

        assertEquals(OnboardingPermission.Notifications, service.requests.last().permission)
    }

    @Test
    fun `종료된 요청의 중복 콜백이 새 요청을 완료시키지 않는다`() {
        val service = FakePermissionService()
        val controller = IosOnboardingPermissionController(service)
        val firstResults = mutableListOf<OnboardingPermissionStatus>()
        val nextResults = mutableListOf<OnboardingPermissionStatus>()

        controller.request(OnboardingPermission.Location, firstResults::add)
        val firstRequest = service.requests.single()
        firstRequest.callback(OnboardingPermissionStatus.SettingsRequired)
        controller.request(OnboardingPermission.Location, nextResults::add)
        firstRequest.callback(OnboardingPermissionStatus.Granted)

        assertEquals(listOf(OnboardingPermissionStatus.SettingsRequired), firstResults)
        assertTrue(nextResults.isEmpty())
        service.requests.last().callback(OnboardingPermissionStatus.Granted)
        assertEquals(listOf(OnboardingPermissionStatus.Granted), nextResults)
    }

    @Test
    fun `네이티브 조회 실패를 미허용 오류 상태로 전달한다`() {
        val service = FakePermissionService().apply { failRefresh = true }
        val controller = IosOnboardingPermissionController(service)
        val results = mutableListOf<OnboardingPermissionState>()

        controller.refresh(results::add)

        assertEquals(
            listOf(
                OnboardingPermissionState(
                    location = OnboardingPermissionStatus.Unavailable,
                    notifications = OnboardingPermissionStatus.Unavailable,
                ),
            ),
            results,
        )
    }

    @Test
    fun `네이티브 요청 실패 뒤 다시 요청할 수 있다`() {
        val service = FakePermissionService().apply { failRequest = true }
        val controller = IosOnboardingPermissionController(service)
        val results = mutableListOf<OnboardingPermissionStatus>()

        controller.request(OnboardingPermission.Location, results::add)
        assertEquals(listOf(OnboardingPermissionStatus.Unavailable), results)

        service.failRequest = false
        controller.request(OnboardingPermission.Location, results::add)
        service.requests.last().callback(OnboardingPermissionStatus.Granted)

        assertEquals(
            listOf(OnboardingPermissionStatus.Unavailable, OnboardingPermissionStatus.Granted),
            results,
        )
    }
}

private class FakePermissionService : IosOnboardingPermissionService {
    data class Request(
        val permission: OnboardingPermission,
        val callback: (OnboardingPermissionStatus) -> Unit,
    )

    val refreshCallbacks = mutableListOf<(OnboardingPermissionState) -> Unit>()
    val requests = mutableListOf<Request>()
    var immediateResult: OnboardingPermissionStatus? = null
    var failRefresh = false
    var failRequest = false

    override fun refresh(callback: (OnboardingPermissionState) -> Unit) {
        if (failRefresh) error("조회 실패")
        refreshCallbacks += callback
    }

    override fun request(
        permission: OnboardingPermission,
        callback: (OnboardingPermissionStatus) -> Unit,
    ) {
        if (failRequest) error("요청 실패")
        requests += Request(permission, callback)
        immediateResult?.let(callback)
    }
}
