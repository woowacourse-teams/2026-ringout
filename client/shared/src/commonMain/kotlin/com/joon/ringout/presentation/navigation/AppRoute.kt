package com.joon.ringout.presentation.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Navigation keys shared by Android and iOS.
 *
 * Routes carry identifiers only. Editing state, signup tokens, and screen data
 * remain in their state holders. Serial names keep saved keys independent of
 * Kotlin class and package names.
 */
@Serializable
internal sealed interface AppRoute : NavKey {
    @Serializable
    @SerialName("onboarding")
    data object Onboarding : AppRoute

    @Serializable
    @SerialName("home")
    data object Home : AppRoute

    @Serializable
    @SerialName("add_alarm")
    data object AddAlarm : AppRoute

    @Serializable
    @SerialName("edit_alarm")
    data class EditAlarm(val alarmId: String) : AppRoute

    // Matches asynchronous save results to this visit to the destination picker.
    @Serializable
    @SerialName("destination")
    data class Destination(val requestId: Long) : AppRoute

    @Serializable
    @SerialName("alarm_sound")
    data object AlarmSound : AppRoute

    // The legacy Settings destination also displays MyPageScreen.
    @Serializable
    @SerialName("my_page")
    data object MyPage : AppRoute

    @Serializable
    @SerialName("nickname_change")
    data object NicknameChange : AppRoute

    @Serializable
    @SerialName("login")
    data object Login : AppRoute

    @Serializable
    @SerialName("terms_agreement")
    data object TermsAgreement : AppRoute

    // Matches AlarmRingingUiState.id, which is a system alarm ID on iOS.
    @Serializable
    @SerialName("alarm_ringing")
    data class AlarmRinging(val alarmId: String) : AppRoute

    // Distinguishes individual mission occurrences of the same repeating alarm.
    @Serializable
    @SerialName("active_alarm_tracking")
    data class ActiveAlarmTracking(val occurrenceId: String) : AppRoute
}
