package com.joon.ringout.presentation.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Android와 iOS에서 공통으로 사용하는 내비게이션 키다.
 *
 * 경로에는 식별자만 담는다. 편집 상태, 회원가입 토큰, 화면 데이터는
 * 각 상태 관리 객체에 유지한다. 직렬화 이름을 명시해 저장된 키가
 * Kotlin 클래스명과 패키지명에 의존하지 않도록 한다.
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

    // 비동기 저장 결과가 목적지 선택 화면의 어느 진입에 해당하는지 구분한다.
    @Serializable
    @SerialName("destination")
    data class Destination(val requestId: Long) : AppRoute

    @Serializable
    @SerialName("alarm_sound")
    data object AlarmSound : AppRoute

    @Serializable
    @SerialName("my_page")
    data object MyPage : AppRoute

    // TODO(RINGOUT_ACCOUNT): 아래 계정 경로는 다음 로그인 버전에서 그래프에 다시 등록한다.
    // 현재 비로그인 전용 앱에서는 어떤 활성 UI에서도 이 경로로 이동하지 않는다.
    @Serializable
    @SerialName("nickname_change")
    data object NicknameChange : AppRoute

    @Serializable
    @SerialName("login")
    data object Login : AppRoute

    @Serializable
    @SerialName("terms_agreement")
    data object TermsAgreement : AppRoute

    // AlarmRingingUiState.id와 일치하며, iOS에서는 시스템 알람 식별자에 해당한다.
    @Serializable
    @SerialName("alarm_ringing")
    data class AlarmRinging(val alarmId: String) : AppRoute

    // 같은 반복 알람에서 발생한 미션을 회차별로 구분한다.
    @Serializable
    @SerialName("active_alarm_tracking")
    data class ActiveAlarmTracking(val occurrenceId: String) : AppRoute
}
