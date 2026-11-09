package com.joon.ringout.presentation.arrival

import com.joon.ringout.domain.arrival.ArrivalResult

data class ArrivalUiState(
    val result: ArrivalResult? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val isConfirming: Boolean = false,
    val confirmationErrorMessage: String? = null,
    val isAcknowledged: Boolean = false,
)
