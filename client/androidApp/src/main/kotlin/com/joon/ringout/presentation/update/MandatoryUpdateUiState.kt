package com.joon.ringout.presentation.update

import com.joon.ringout.domain.update.AppUpdateAccess

internal data class MandatoryUpdateUiState(
    val access: AppUpdateAccess = AppUpdateAccess.Checking,
    val hasStoreOpenError: Boolean = false,
)
