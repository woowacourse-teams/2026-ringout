package com.joon.ringout.presentation.social

import androidx.lifecycle.ViewModel

data class SocialUiState(val title: String = "소셜")

class SocialViewModel : ViewModel() {
    val uiState = SocialUiState()
}
