package com.joon.ringout.presentation.arrival

import com.joon.ringout.domain.auth.AuthSessionState
import com.joon.ringout.domain.firstlaunch.AppEntryDestination
import com.joon.ringout.domain.firstlaunch.determineAppEntryDestination
import com.joon.ringout.domain.firstlaunch.migratedInstallationEntryState
import com.joon.ringout.domain.firstlaunch.shouldShowLimitedRuntimeControls
import com.joon.ringout.domain.preferences.LegacyEntryState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RestrictedArrivalEntryTest {
    @Test
    fun `기존 비로그인 설치의 미션 완료 후 온보딩보다 도착 확인을 먼저 허용한다`() {
        val installation = migratedInstallationEntryState(
            authSessionState = AuthSessionState.Unauthenticated,
            legacy = LegacyEntryState(),
            hasLegacyAlarmOrRuntimeEvidence = true,
        )
        assertTrue(
            shouldShowLimitedRuntimeControls(installation, AuthSessionState.Unauthenticated, hasActiveRuntime = true),
        )
        val destination = determineAppEntryDestination(installation, AuthSessionState.Unauthenticated)

        assertEquals(AppEntryDestination.Onboarding, destination)
        assertTrue(shouldEnableRestrictedArrival(true, destination, areGuideTermsAllowed = false))
    }

    @Test
    fun `로그인과 가이드 약관 제한 중에도 로컬 도착 확인을 허용한다`() {
        assertTrue(shouldEnableRestrictedArrival(true, AppEntryDestination.Login, areGuideTermsAllowed = false))
        assertTrue(shouldEnableRestrictedArrival(true, AppEntryDestination.Guide, areGuideTermsAllowed = false))
    }

    @Test
    fun `앱 초기화와 로그아웃 정리 중에는 도착 확인으로 진입하지 않는다`() {
        AppEntryDestination.entries.forEach { destination ->
            assertFalse(shouldEnableRestrictedArrival(false, destination, areGuideTermsAllowed = false))
        }
        assertFalse(shouldEnableRestrictedArrival(true, AppEntryDestination.LogoutCleaning, areGuideTermsAllowed = false))
        assertFalse(shouldEnableRestrictedArrival(true, null, areGuideTermsAllowed = false))
    }

    @Test
    fun `홈과 약관 동의를 마친 가이드는 일반 내비게이션에서 도착 화면을 처리한다`() {
        assertFalse(shouldEnableRestrictedArrival(true, AppEntryDestination.Home, areGuideTermsAllowed = false))
        assertFalse(shouldEnableRestrictedArrival(true, AppEntryDestination.Guide, areGuideTermsAllowed = true))
    }
}
