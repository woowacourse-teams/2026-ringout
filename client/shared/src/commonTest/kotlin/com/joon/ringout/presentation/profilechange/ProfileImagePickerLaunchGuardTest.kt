package com.joon.ringout.presentation.profilechange

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProfileImagePickerLaunchGuardTest {
    @Test
    fun `연속 요청 열 번 중 하나만 선택기를 실행한다`() {
        val guard = ProfileImagePickerLaunchGuard()
        var launchCount = 0
        var resultCount = 0
        var completeLaunch: (() -> Unit)? = null

        repeat(10) {
            if (guard.tryStart()) {
                launchCount += 1
                completeLaunch = {
                    guard.complete()
                    resultCount += 1
                }
            }
        }
        completeLaunch?.invoke()

        assertEquals(1, launchCount)
        assertEquals(1, resultCount)
    }

    @Test
    fun `작업 완료 후 다음 선택기를 실행할 수 있다`() {
        val guard = ProfileImagePickerLaunchGuard()

        assertTrue(guard.tryStart())
        guard.complete()

        assertTrue(guard.tryStart())
    }

    @Test
    fun `폐기된 선택기는 실행되지 않는다`() {
        val guard = ProfileImagePickerLaunchGuard()

        assertTrue(guard.tryStart())
        guard.dispose()
        guard.complete()

        assertFalse(guard.tryStart())
    }
}

class ProfileImagePickerDismissalGateTest {
    @Test
    fun `파일 결과가 닫기 완료보다 먼저 와도 두 완료 후 결과를 한 번 전달한다`() {
        val results = mutableListOf<ProfileImagePickResult>()
        val gate = ProfileImagePickerDismissalGate(results::add)

        gate.markPickerFinished()
        gate.reportResult(ProfileImagePickResult.Failure)
        assertTrue(results.isEmpty())

        gate.markDismissed()
        gate.reportResult(ProfileImagePickResult.Cancelled)

        assertEquals(listOf<ProfileImagePickResult>(ProfileImagePickResult.Failure), results)
    }

    @Test
    fun `닫기 완료가 파일 결과보다 먼저 와도 두 완료 후 결과를 전달한다`() {
        val results = mutableListOf<ProfileImagePickResult>()
        val gate = ProfileImagePickerDismissalGate(results::add)

        gate.markPickerFinished()
        gate.markDismissed()
        assertTrue(results.isEmpty())

        gate.reportResult(ProfileImagePickResult.TooLarge)

        assertEquals(listOf<ProfileImagePickResult>(ProfileImagePickResult.TooLarge), results)
    }

    @Test
    fun `선택 결과 없이 닫히면 취소를 한 번 전달한다`() {
        val results = mutableListOf<ProfileImagePickResult>()
        val gate = ProfileImagePickerDismissalGate(results::add)

        gate.markDismissed()
        gate.markDismissed()

        assertEquals(listOf<ProfileImagePickResult>(ProfileImagePickResult.Cancelled), results)
    }

    @Test
    fun `폐기된 완료 조정기는 결과를 전달하지 않는다`() {
        val results = mutableListOf<ProfileImagePickResult>()
        val gate = ProfileImagePickerDismissalGate(results::add)

        gate.dispose()
        gate.reportResult(ProfileImagePickResult.Failure)
        gate.markDismissed()

        assertTrue(results.isEmpty())
    }
}
