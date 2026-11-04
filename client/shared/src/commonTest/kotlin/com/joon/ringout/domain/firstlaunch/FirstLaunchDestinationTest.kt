package com.joon.ringout.domain.firstlaunch

import com.joon.ringout.domain.auth.AuthSessionState
import com.joon.ringout.domain.preferences.InstallationEntryState
import com.joon.ringout.domain.preferences.LegacyEntryState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FirstLaunchDestinationTest {
    @Test
    fun `로그아웃 정리가 대기 중이면 인증 복원과 온보딩보다 정리 화면이 우선한다`() {
        assertEquals(
            AppEntryDestination.LogoutCleaning,
            determineAppEntryDestination(
                InstallationEntryState(isLogoutCleanupPending = true),
                AuthSessionState.Restoring,
            ),
        )
    }

    @Test
    fun `인증을 복원하는 동안 진입 화면을 결정하지 않는다`() {
        assertNull(determineAppEntryDestination(InstallationEntryState(), AuthSessionState.Restoring))
    }

    @Test
    fun `온보딩을 완료하지 않으면 인증 여부와 무관하게 서비스 소개로 진입한다`() {
        listOf(AuthSessionState.Unauthenticated, AuthSessionState.Authenticated).forEach { auth ->
            assertEquals(AppEntryDestination.Onboarding, determineAppEntryDestination(InstallationEntryState(), auth))
        }
    }

    @Test
    fun `온보딩을 완료한 미인증 사용자와 재인증 대상자는 로그인으로 진입한다`() {
        listOf(AuthSessionState.Unauthenticated, AuthSessionState.ReauthenticationRequired).forEach { auth ->
            assertEquals(
                AppEntryDestination.Login,
                determineAppEntryDestination(InstallationEntryState(isOnboardingCompleted = true), auth),
            )
        }
    }

    @Test
    fun `인증이 완료되어도 인증 화면의 완료 인계 전까지 로그인 화면을 유지한다`() {
        assertEquals(
            AppEntryDestination.Login,
            determineAppEntryDestination(
                InstallationEntryState(isOnboardingCompleted = true),
                AuthSessionState.Authenticated,
                isAuthFlowInProgress = true,
            ),
        )
    }

    @Test
    fun `인증된 사용자는 설치 가이드 완료 여부에 따라 가이드와 홈으로 진입한다`() {
        val installation = InstallationEntryState(isOnboardingCompleted = true)
        assertEquals(AppEntryDestination.Guide, determineAppEntryDestination(installation, AuthSessionState.Authenticated))
        assertEquals(
            AppEntryDestination.Home,
            determineAppEntryDestination(
                installation.copy(isAlarmCreationGuideCompleted = true),
                AuthSessionState.Authenticated,
            ),
        )
    }

    @Test
    fun `인증 복원 전에는 설치 정책을 마이그레이션할 수 없다`() {
        assertFailsWith<IllegalArgumentException> {
            migratedInstallationEntryState(AuthSessionState.Restoring, LegacyEntryState(), false)
        }
    }

    @Test
    fun `새 미인증 설치는 온보딩과 가이드를 완료하지 않은 상태로 시작한다`() {
        assertEquals(
            InstallationEntryState(policyVersion = EntryPolicyVersion),
            migratedInstallationEntryState(AuthSessionState.Unauthenticated, LegacyEntryState(), false),
        )
    }

    @Test
    fun `미인증 기존 설치의 키나 알람 흔적은 예외만 남기고 온보딩과 가이드를 초기화한다`() {
        val legacyCases = listOf(
            LegacyEntryState(hasOnboardingKey = true, isOnboardingCompleted = true),
            LegacyEntryState(hasOnboardingKey = true, isOnboardingCompleted = false),
            LegacyEntryState(hasAlarmGuidePendingKey = true, isAlarmGuidePending = false),
        )
        legacyCases.forEach { legacy ->
            assertEquals(
                InstallationEntryState(policyVersion = EntryPolicyVersion, hasLegacyRuntimeExceptionPending = true),
                migratedInstallationEntryState(AuthSessionState.Unauthenticated, legacy, false),
            )
        }
        assertTrue(migratedInstallationEntryState(
            AuthSessionState.Unauthenticated, LegacyEntryState(), true,
        ).hasLegacyRuntimeExceptionPending)
    }

    @Test
    fun `기존 인증 설치는 온보딩을 건너뛰고 명시된 가이드 대기 상태를 우선 보존한다`() {
        listOf(AuthSessionState.Authenticated, AuthSessionState.ReauthenticationRequired).forEach { auth ->
            val pending = migratedInstallationEntryState(
                auth,
                LegacyEntryState(isOnboardingCompleted = true, isAlarmGuidePending = true),
                true,
            )
            assertTrue(pending.isOnboardingCompleted)
            assertFalse(pending.isAlarmCreationGuideCompleted)
            assertFalse(pending.hasLegacyRuntimeExceptionPending)
            val completed = migratedInstallationEntryState(
                auth, LegacyEntryState(isAlarmGuidePending = false), false,
            )
            assertTrue(completed.isAlarmCreationGuideCompleted)
        }
    }

    @Test
    fun `기존 인증 설치에 가이드 키가 없으면 과거 온보딩 완료 값으로 가이드 완료를 추론한다`() {
        assertTrue(migratedInstallationEntryState(
            AuthSessionState.Authenticated, LegacyEntryState(isOnboardingCompleted = true), false,
        ).isAlarmCreationGuideCompleted)
        assertFalse(migratedInstallationEntryState(
            AuthSessionState.Authenticated, LegacyEntryState(), false,
        ).isAlarmCreationGuideCompleted)
    }

    @Test
    fun `기존 미인증 설치에서 울리는 알람이나 진행 중 미션만 제한적으로 노출한다`() {
        val legacyInstall = InstallationEntryState(
            policyVersion = EntryPolicyVersion,
            hasLegacyRuntimeExceptionPending = true,
        )
        assertTrue(shouldShowLimitedRuntimeControls(legacyInstall, AuthSessionState.Unauthenticated, true))
        assertTrue(shouldShowLimitedRuntimeControls(
            legacyInstall.copy(hasLegacyRuntimeExceptionPending = false, isOnboardingCompleted = true),
            AuthSessionState.ReauthenticationRequired,
            true,
        ))
        assertFalse(shouldShowLimitedRuntimeControls(legacyInstall, AuthSessionState.Unauthenticated, false))
    }

    @Test
    fun `새 설치와 로그아웃 정리 중에는 미인증 런타임을 열지 않는다`() {
        assertFalse(shouldShowLimitedRuntimeControls(
            InstallationEntryState(policyVersion = EntryPolicyVersion),
            AuthSessionState.Unauthenticated,
            true,
        ))
        assertFalse(shouldShowLimitedRuntimeControls(
            InstallationEntryState(
                policyVersion = EntryPolicyVersion,
                hasLegacyRuntimeExceptionPending = true,
                isLogoutCleanupPending = true,
            ),
            AuthSessionState.Unauthenticated,
            true,
        ))
    }
}
