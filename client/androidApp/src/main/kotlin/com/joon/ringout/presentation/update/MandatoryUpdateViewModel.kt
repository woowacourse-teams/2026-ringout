package com.joon.ringout.presentation.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joon.ringout.domain.update.AppUpdateAccess
import com.joon.ringout.domain.update.evaluateAppUpdateAccess
import com.joon.ringout.remoteconfig.RemoteConfigState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class MandatoryUpdateViewModel(
    private val remoteConfigState: StateFlow<RemoteConfigState>,
    private val currentVersionCode: Int,
    coroutineScope: CoroutineScope? = null,
) : ViewModel() {
    private var isAlarmInProgress = false
    private val mutableState = MutableStateFlow(
        MandatoryUpdateUiState(access = access(remoteConfigState.value)),
    )
    val uiState: StateFlow<MandatoryUpdateUiState> = mutableState.asStateFlow()

    init {
        (coroutineScope ?: viewModelScope).launch {
            remoteConfigState.collect(::updateAccess)
        }
    }

    fun setAlarmInProgress(isAlarmInProgress: Boolean) {
        this.isAlarmInProgress = isAlarmInProgress
        updateAccess(remoteConfigState.value)
    }

    /** Opening the store never satisfies the minimum-version requirement. */
    fun onStoreOpenResult(success: Boolean) {
        mutableState.update { current ->
            current.copy(hasStoreOpenError = !success && current.access == AppUpdateAccess.Required)
        }
    }

    private fun updateAccess(config: RemoteConfigState) {
        val access = access(config)
        mutableState.update { current ->
            current.copy(
                access = access,
                hasStoreOpenError = current.hasStoreOpenError && access == AppUpdateAccess.Required,
            )
        }
    }

    private fun access(config: RemoteConfigState): AppUpdateAccess =
        evaluateAppUpdateAccess(
            currentVersionCode = currentVersionCode,
            minVersionCode = config.minVersionCode,
            isConfigInitialized = config.isInitialized,
            isAlarmInProgress = isAlarmInProgress,
        )
}
