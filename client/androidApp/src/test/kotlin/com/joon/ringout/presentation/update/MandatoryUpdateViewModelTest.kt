package com.joon.ringout.presentation.update

import com.joon.ringout.domain.update.AppUpdateAccess
import com.joon.ringout.remoteconfig.RemoteConfigFetchStatus
import com.joon.ringout.remoteconfig.RemoteConfigState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MandatoryUpdateViewModelTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val remoteConfigState = MutableStateFlow(RemoteConfigState())

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `저장된 설정을 읽기 전에는 기본값으로 앱 사용을 허용하지 않는다`() {
        val viewModel = createViewModel()

        assertEquals(AppUpdateAccess.Checking, viewModel.uiState.value.access)

        remoteConfigState.value = RemoteConfigState(
            isInitialized = true,
            minVersionCode = 101,
            fetchStatus = RemoteConfigFetchStatus.FETCHING,
        )

        assertEquals(AppUpdateAccess.Required, viewModel.uiState.value.access)
    }

    @Test
    fun `현재 버전이 최소 버전 미만일 때만 업데이트를 강제한다`() {
        val viewModel = createViewModel()

        remoteConfigState.value = RemoteConfigState(isInitialized = true, minVersionCode = 101)
        assertEquals(AppUpdateAccess.Required, viewModel.uiState.value.access)

        remoteConfigState.value = RemoteConfigState(isInitialized = true, minVersionCode = 100)
        assertEquals(AppUpdateAccess.Allowed, viewModel.uiState.value.access)

        remoteConfigState.value = RemoteConfigState(isInitialized = true, minVersionCode = 99)
        assertEquals(AppUpdateAccess.Allowed, viewModel.uiState.value.access)
    }

    @Test
    fun `첫 실행 오프라인 기본값으로는 앱 사용을 허용한다`() {
        remoteConfigState.value = RemoteConfigState(
            isInitialized = true,
            fetchStatus = RemoteConfigFetchStatus.FAILURE,
        )

        assertEquals(AppUpdateAccess.Allowed, createViewModel().uiState.value.access)
    }

    @Test
    fun `최소 버전이 올라가면 차단하고 원격 기준을 되돌리면 즉시 해제한다`() {
        remoteConfigState.value = RemoteConfigState(isInitialized = true, minVersionCode = 100)
        val viewModel = createViewModel()

        remoteConfigState.value = remoteConfigState.value.copy(minVersionCode = 101)
        assertEquals(AppUpdateAccess.Required, viewModel.uiState.value.access)
        viewModel.onStoreOpenResult(success = false)
        assertTrue(viewModel.uiState.value.hasStoreOpenError)

        remoteConfigState.value = remoteConfigState.value.copy(minVersionCode = 100)

        assertEquals(AppUpdateAccess.Allowed, viewModel.uiState.value.access)
        assertFalse(viewModel.uiState.value.hasStoreOpenError)
    }

    @Test
    fun `실시간 설정과 알람 종료를 반영하여 미션이 끝난 뒤 차단한다`() {
        remoteConfigState.value = RemoteConfigState(isInitialized = true, minVersionCode = 100)
        val viewModel = createViewModel()
        viewModel.setAlarmInProgress(true)

        remoteConfigState.value = remoteConfigState.value.copy(minVersionCode = 101)
        assertEquals(AppUpdateAccess.DeferredForAlarm, viewModel.uiState.value.access)

        viewModel.setAlarmInProgress(false)

        assertEquals(AppUpdateAccess.Required, viewModel.uiState.value.access)
    }

    @Test
    fun `설정 초기화 중에도 진행 중인 알람은 완료할 수 있다`() {
        val viewModel = createViewModel()
        viewModel.setAlarmInProgress(true)

        assertEquals(AppUpdateAccess.DeferredForAlarm, viewModel.uiState.value.access)

        viewModel.setAlarmInProgress(false)
        assertEquals(AppUpdateAccess.Checking, viewModel.uiState.value.access)

        remoteConfigState.value = RemoteConfigState(isInitialized = true)
        assertEquals(AppUpdateAccess.Allowed, viewModel.uiState.value.access)
    }

    @Test
    fun `스토어 이동 성공이나 실패로는 업데이트 차단이 해제되지 않는다`() {
        remoteConfigState.value = RemoteConfigState(isInitialized = true, minVersionCode = 101)
        val viewModel = createViewModel()

        viewModel.onStoreOpenResult(success = false)
        assertTrue(viewModel.uiState.value.hasStoreOpenError)
        assertEquals(AppUpdateAccess.Required, viewModel.uiState.value.access)

        viewModel.onStoreOpenResult(success = true)
        assertFalse(viewModel.uiState.value.hasStoreOpenError)
        assertEquals(AppUpdateAccess.Required, viewModel.uiState.value.access)
    }

    @Test
    fun `설치 버전이 갱신된 앱을 새로 실행하면 기존 최소 버전을 충족한다`() {
        remoteConfigState.value = RemoteConfigState(isInitialized = true, minVersionCode = 101)

        assertEquals(AppUpdateAccess.Required, createViewModel(currentVersionCode = 100).uiState.value.access)
        assertEquals(AppUpdateAccess.Allowed, createViewModel(currentVersionCode = 101).uiState.value.access)
    }

    private fun createViewModel(currentVersionCode: Int = 100) = MandatoryUpdateViewModel(
        remoteConfigState = remoteConfigState,
        currentVersionCode = currentVersionCode,
        coroutineScope = scope,
    )
}
