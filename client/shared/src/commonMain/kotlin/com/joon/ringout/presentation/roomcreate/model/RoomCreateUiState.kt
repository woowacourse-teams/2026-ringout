package com.joon.ringout.presentation.roomcreate.model

import com.joon.ringout.presentation.common.WeekdayOrder
import com.joon.ringout.presentation.roomcreate.validateRoomIntroduction
import com.joon.ringout.presentation.roomcreate.validateRoomName

internal data class RoomCreateUiState(
    val step: Int = 1,
    val name: String = "",
    val introduction: String = "",
    val selectedDays: List<String> = WeekdayOrder,
    val time24Hour: String? = null,
    val submitErrorMessage: String? = null,
) {
    val nameValidation
        get() = validateRoomName(name)

    val introductionValidation
        get() = validateRoomIntroduction(introduction)

    val canContinue: Boolean
        get() = when (step) {
            1 -> nameValidation.isValid
            2 -> nameValidation.isValid && introductionValidation.isValid
            3 -> nameValidation.isValid &&
                introductionValidation.isValid &&
                selectedDays.isNotEmpty() &&
                time24Hour != null
            else -> false
        }
}

internal data class RoomCreateDraft(
    val name: String,
    val introduction: String,
    val selectedDays: List<String>,
    val time24Hour: String,
)
