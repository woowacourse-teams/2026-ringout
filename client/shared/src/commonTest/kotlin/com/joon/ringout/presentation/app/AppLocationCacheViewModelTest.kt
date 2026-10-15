package com.joon.ringout.presentation.app

import com.joon.ringout.domain.auth.AuthSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class AppLocationCacheViewModelTest {
    @Test
    fun `최초 비로그인 세션 복원이 완료되어도 이미 구한 현재 위치를 유지한다`() = runTest {
        val authSession = AuthSession()
        val viewModel = AppLocationCacheViewModel(authSession, backgroundScope)
        viewModel.cacheCurrentLocation()

        runCurrent()
        authSession.clear()
        runCurrent()

        viewModel.assertCachedCurrentLocation()
    }

    @Test
    fun `비로그인 상태를 반복해서 반영해도 현재 위치를 유지한다`() = runTest {
        val authSession = AuthSession().apply { clear() }
        val viewModel = AppLocationCacheViewModel(authSession, backgroundScope)
        runCurrent()
        viewModel.cacheCurrentLocation()

        authSession.clear()
        runCurrent()
        authSession.clear()
        runCurrent()

        viewModel.assertCachedCurrentLocation()
    }

    @Test
    fun `로그아웃하면 이전 로그인 세션의 현재 위치 캐시를 비운다`() = runTest {
        val authSession = AuthSession().apply { startNewSession() }
        val viewModel = AppLocationCacheViewModel(authSession, backgroundScope)
        runCurrent()
        viewModel.cacheCurrentLocation()

        authSession.clear()
        runCurrent()

        assertNull(viewModel.destinationCurrentLocationCache.initialLocation())
    }

    @Test
    fun `로그인 계정이 바뀌면 이전 계정의 현재 위치 캐시를 비운다`() = runTest {
        val authSession = AuthSession().apply { startNewSession() }
        val viewModel = AppLocationCacheViewModel(authSession, backgroundScope)
        runCurrent()
        viewModel.cacheCurrentLocation()

        authSession.startNewSession()
        runCurrent()

        assertNull(viewModel.destinationCurrentLocationCache.initialLocation())
    }

    @Test
    fun `재인증이 필요해지면 기존 세션의 현재 위치 캐시를 비운다`() = runTest {
        val authSession = AuthSession().apply { startNewSession() }
        val viewModel = AppLocationCacheViewModel(authSession, backgroundScope)
        runCurrent()
        viewModel.cacheCurrentLocation()

        authSession.requireReauthentication()
        runCurrent()

        assertNull(viewModel.destinationCurrentLocationCache.initialLocation())
    }

    @Test
    fun `비로그인 사용자가 로그인하면 이전 세션의 현재 위치 캐시를 비운다`() = runTest {
        val authSession = AuthSession().apply { clear() }
        val viewModel = AppLocationCacheViewModel(authSession, backgroundScope)
        runCurrent()
        viewModel.cacheCurrentLocation()

        authSession.startNewSession()
        runCurrent()

        assertNull(viewModel.destinationCurrentLocationCache.initialLocation())
    }

    @Test
    fun `동일 계정의 인증 상태를 갱신하면 현재 위치 캐시를 유지한다`() = runTest {
        val authSession = AuthSession().apply { startNewSession() }
        val viewModel = AppLocationCacheViewModel(authSession, backgroundScope)
        runCurrent()
        viewModel.cacheCurrentLocation()

        authSession.markAuthenticated()
        runCurrent()

        viewModel.assertCachedCurrentLocation()
    }

    @Test
    fun `앱 루트를 새로 생성하면 이전 앱의 현재 위치 캐시를 재사용하지 않는다`() = runTest {
        val authSession = AuthSession().apply { clear() }
        val previousApp = AppLocationCacheViewModel(authSession, backgroundScope)
        runCurrent()
        previousApp.cacheCurrentLocation()

        val newApp = AppLocationCacheViewModel(authSession, backgroundScope)
        runCurrent()

        assertNull(newApp.destinationCurrentLocationCache.initialLocation())
        previousApp.assertCachedCurrentLocation()
    }
}

private fun AppLocationCacheViewModel.cacheCurrentLocation() {
    destinationCurrentLocationCache.update(latitude = 37.5665, longitude = 126.9780)
}

private fun AppLocationCacheViewModel.assertCachedCurrentLocation() {
    val location = assertNotNull(destinationCurrentLocationCache.initialLocation())
    assertEquals(37.5665, location.latitude)
    assertEquals(126.9780, location.longitude)
}
