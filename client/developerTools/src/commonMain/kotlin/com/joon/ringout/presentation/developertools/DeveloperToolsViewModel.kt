package com.joon.ringout.presentation.developertools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joon.ringout.domain.developertools.AppBuildInfo
import com.joon.ringout.domain.outbox.OutboxReader
import com.joon.ringout.domain.push.PushTokenRegistrationStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class DeveloperToolsViewModel(
    buildInfo: AppBuildInfo,
    private val outboxReader: OutboxReader,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(
        DeveloperToolsUiState(
            versionName = buildInfo.versionName,
            versionCode = buildInfo.versionCode,
            buildType = buildInfo.buildType,
            serverEnvironment = buildInfo.serverEnvironment,
            apiBaseUrl = buildInfo.apiBaseUrl,
            pushTokenRegistrationStatus = PushTokenRegistrationStatus.WaitingForAuthentication,
            isOutboxLoading = true,
        ),
    )
    val uiState = mutableUiState.asStateFlow()

    init {
        refreshOutbox()
    }

    fun refreshOutbox() {
        viewModelScope.launch {
            mutableUiState.update { it.copy(isOutboxLoading = true, outboxLoadError = null) }
            try {
                val records = outboxReader.read()
                mutableUiState.update { it.copy(outboxRecords = records, isOutboxLoading = false) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                mutableUiState.update {
                    it.copy(
                        isOutboxLoading = false,
                        outboxLoadError = error.message ?: "Outbox 조회에 실패했습니다.",
                    )
                }
            }
        }
    }
}
