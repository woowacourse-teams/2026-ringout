package com.joon.ringout.presentation.onboarding

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.provider.Settings
import com.joon.ringout.alarm.NotificationPermissionPrompt
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidOnboardingPermissionControllerTest {
    private val context = RuntimeEnvironment.getApplication()
    private val runtimeRequests = mutableListOf<Array<String>>()
    private val settingsRequests = mutableListOf<Intent>()
    private val results = mutableListOf<OnboardingPermissionStatus>()
    private var showRationale = false
    private val access = AndroidOnboardingPermissionAccess(context) { showRationale }
    private val controller = AndroidOnboardingPermissionController(
        access = access,
        launchRuntimePermissions = { runtimeRequests += it },
        launchSettings = { settingsRequests += it },
    )

    @Before
    fun setUp() {
        context.getSharedPreferences("ringout_permission_prompts", Context.MODE_PRIVATE)
            .edit().clear().commit()
        shadowOf(context).denyPermissions(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS,
        )
        shadowOf(context.getSystemService(LocationManager::class.java)).setLocationEnabled(true)
        shadowOf(context.getSystemService(NotificationManager::class.java)).setNotificationsEnabled(true)
    }

    @Test
    fun `위치는 정밀 위치와 대략적 위치를 함께 요청하고 결과가 올 때까지 완료하지 않는다`() {
        controller.request(OnboardingPermission.Location, results::add)
        controller.request(OnboardingPermission.Location, results::add)

        assertEquals(1, runtimeRequests.size)
        assertContentEquals(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            runtimeRequests.single(),
        )
        assertTrue(results.isEmpty())

        shadowOf(context).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        controller.completeRequest()
        controller.completeRequest()
        assertEquals(listOf(OnboardingPermissionStatus.Granted), results)
    }

    @Test
    fun `대략적 위치만 허용하면 위치 단계를 완료하지 않는다`() {
        controller.request(OnboardingPermission.Location, results::add)
        shadowOf(context).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
        showRationale = true
        controller.completeRequest()

        assertEquals(listOf(OnboardingPermissionStatus.Requestable), results)
    }

    @Test
    fun `다시 요청할 수 없는 위치 권한은 앱 설정을 열고 복귀 후 실제 상태를 확인한다`() {
        access.markRequested(OnboardingPermission.Location)
        controller.request(OnboardingPermission.Location, results::add)

        assertTrue(runtimeRequests.isEmpty())
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, settingsRequests.single().action)
        assertEquals("package:${context.packageName}", settingsRequests.single().data.toString())
        assertTrue(results.isEmpty())

        shadowOf(context).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        controller.completeRequest()
        assertEquals(listOf(OnboardingPermissionStatus.Granted), results)
    }

    @Test
    fun `정밀 위치를 허용해도 기기 위치가 꺼져 있으면 위치 설정으로 안내한다`() {
        shadowOf(context).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        val locationManager = shadowOf(context.getSystemService(LocationManager::class.java))
        locationManager.setLocationEnabled(false)

        assertEquals(OnboardingPermissionStatus.ServicesDisabled, access.status(OnboardingPermission.Location))
        controller.request(OnboardingPermission.Location, results::add)
        assertEquals(Settings.ACTION_LOCATION_SOURCE_SETTINGS, settingsRequests.single().action)

        controller.completeRequest()
        assertEquals(listOf(OnboardingPermissionStatus.ServicesDisabled), results)
        locationManager.setLocationEnabled(true)
        controller.request(OnboardingPermission.Location, results::add)
        assertEquals(OnboardingPermissionStatus.Granted, results.last())
    }

    @Test
    fun `알림 요청 기록을 공유해서 홈과 알람 저장에서 자동으로 중복 요청하지 않는다`() {
        controller.request(OnboardingPermission.Notifications, results::add)

        assertContentEquals(arrayOf(Manifest.permission.POST_NOTIFICATIONS), runtimeRequests.single())
        assertTrue(NotificationPermissionPrompt.hasRequested(context))
        assertFalse(NotificationPermissionPrompt.tryAcquire(context))

        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        controller.completeRequest()
        assertEquals(listOf(OnboardingPermissionStatus.Granted), results)
    }

    @Test
    fun `알림 팝업을 거부하거나 닫으면 허용 완료로 처리하지 않는다`() {
        controller.request(OnboardingPermission.Notifications, results::add)
        controller.completeRequest()

        assertEquals(listOf(OnboardingPermissionStatus.SettingsRequired), results)
    }

    @Test
    fun `다시 요청 가능한 알림 권한은 사용자 버튼으로 재요청할 수 있다`() {
        access.markRequested(OnboardingPermission.Notifications)
        showRationale = true
        controller.request(OnboardingPermission.Notifications, results::add)

        assertEquals(1, runtimeRequests.size)
        assertTrue(settingsRequests.isEmpty())
    }

    @Test
    fun `알림 재요청이 막혔으면 앱 알림 설정으로 안내한다`() {
        access.markRequested(OnboardingPermission.Notifications)
        controller.request(OnboardingPermission.Notifications, results::add)

        assertTrue(runtimeRequests.isEmpty())
        assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, settingsRequests.single().action)
        assertEquals(context.packageName, settingsRequests.single().getStringExtra(Settings.EXTRA_APP_PACKAGE))
    }

    @Test
    fun `알림 권한이 있어도 앱 알림이 꺼져 있으면 설정이 필요하다`() {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        shadowOf(context.getSystemService(NotificationManager::class.java)).setNotificationsEnabled(false)

        assertEquals(OnboardingPermissionStatus.SettingsRequired, access.status(OnboardingPermission.Notifications))
    }

    @Test
    @Config(sdk = [32])
    fun `안드로이드 십이에서는 알림 런타임 권한 팝업 없이 허용 상태를 반환한다`() {
        controller.request(OnboardingPermission.Notifications, results::add)

        assertEquals(listOf(OnboardingPermissionStatus.Granted), results)
        assertTrue(runtimeRequests.isEmpty())
        assertTrue(settingsRequests.isEmpty())
    }

    @Test
    @Config(sdk = [32])
    fun `안드로이드 십이에서 차단한 알림은 런타임 요청 대신 설정으로 안내한다`() {
        shadowOf(context.getSystemService(NotificationManager::class.java)).setNotificationsEnabled(false)
        controller.request(OnboardingPermission.Notifications, results::add)

        assertTrue(runtimeRequests.isEmpty())
        assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, settingsRequests.single().action)
        controller.completeRequest()
        assertEquals(listOf(OnboardingPermissionStatus.SettingsRequired), results)
    }

    @Test
    fun `새로고침은 이전 기록 대신 현재 운영체제의 두 권한 상태를 반환한다`() {
        access.markRequested(OnboardingPermission.Location)
        access.markRequested(OnboardingPermission.Notifications)
        shadowOf(context).grantPermissions(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS,
        )
        var state = OnboardingPermissionState()
        controller.refresh { state = it }

        assertEquals(
            OnboardingPermissionState(OnboardingPermissionStatus.Granted, OnboardingPermissionStatus.Granted),
            state,
        )
    }

    @Test
    fun `설정 화면을 열지 못하면 대기를 끝내고 다시 시도할 수 있다`() {
        access.markRequested(OnboardingPermission.Location)
        val controller = AndroidOnboardingPermissionController(
            access = access,
            launchRuntimePermissions = {},
            launchSettings = { throw IllegalStateException("Settings unavailable") },
        )

        controller.request(OnboardingPermission.Location, results::add)
        controller.request(OnboardingPermission.Location, results::add)
        assertEquals(List(2) { OnboardingPermissionStatus.Unavailable }, results)
    }

    @Test
    fun `액티비티 재생성 중인 권한 요청은 새 화면에 연결한 뒤 한 번만 완료한다`() {
        controller.request(OnboardingPermission.Notifications, results::add)
        controller.unbind(access)
        controller.completeRequest()
        assertTrue(results.isEmpty())

        controller.bind(
            access = AndroidOnboardingPermissionAccess(context),
            launchRuntimePermissions = { runtimeRequests += it },
            launchSettings = { settingsRequests += it },
        )
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        controller.completeRequest()
        controller.completeRequest()

        assertEquals(listOf(OnboardingPermissionStatus.Granted), results)
        assertEquals(1, runtimeRequests.size)
    }

    @Test
    fun `재생성 뒤에는 이전 액티비티 대신 새 권한 상태와 런처를 사용한다`() {
        showRationale = true
        controller.request(OnboardingPermission.Location, results::add)
        controller.unbind(access)
        val newSettingsRequests = mutableListOf<Intent>()
        controller.bind(
            access = AndroidOnboardingPermissionAccess(context) { false },
            launchRuntimePermissions = { error("새 화면에서는 다시 요청할 수 없습니다") },
            launchSettings = { newSettingsRequests += it },
        )

        controller.completeRequest()
        assertEquals(listOf(OnboardingPermissionStatus.SettingsRequired), results)
        controller.request(OnboardingPermission.Location, results::add)

        assertEquals(1, newSettingsRequests.size)
        assertTrue(settingsRequests.isEmpty())
    }

    @Test
    fun `이전 화면의 정리가 늦게 호출되어도 새 화면의 권한 연결을 해제하지 않는다`() {
        controller.request(OnboardingPermission.Location, results::add)
        controller.bind(
            access = AndroidOnboardingPermissionAccess(context),
            launchRuntimePermissions = {},
            launchSettings = {},
        )
        controller.unbind(access)
        shadowOf(context).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        controller.completeRequest()

        assertEquals(listOf(OnboardingPermissionStatus.Granted), results)
    }

    @Test
    fun `관리자 정책으로 제한된 위치도 설정 열기를 누르면 설정 화면을 표시한다`() {
        val restrictedController = AndroidOnboardingPermissionController(
            access = AndroidOnboardingPermissionAccess(context, isRevokedByPolicy = { true }),
            launchRuntimePermissions = { runtimeRequests += it },
            launchSettings = { settingsRequests += it },
        )

        restrictedController.request(OnboardingPermission.Location, results::add)
        assertTrue(runtimeRequests.isEmpty())
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, settingsRequests.single().action)
        assertTrue(results.isEmpty())

        restrictedController.completeRequest()
        assertEquals(listOf(OnboardingPermissionStatus.Restricted), results)
    }
}
