package com.joon.ringout.presentation.signup.model

data class SignupUiState(
    val hasPendingSignup: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val completedEventId: Long? = null,
)
