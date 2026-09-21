package com.joon.ringout.presentation.records

import androidx.lifecycle.ViewModel

data class RecordsUiState(val title: String = "기록")

class RecordsViewModel : ViewModel() {
    val uiState = RecordsUiState()
}
