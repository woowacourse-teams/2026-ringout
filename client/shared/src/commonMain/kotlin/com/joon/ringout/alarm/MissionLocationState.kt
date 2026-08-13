package com.joon.ringout.alarm

enum class MissionLocationServicesState {
    UNKNOWN,
    ENABLED,
    DISABLED,
}

enum class MissionLocationAuthorizationState {
    NOT_DETERMINED,
    WHEN_IN_USE,
    ALWAYS,
    DENIED,
    RESTRICTED,
}

enum class MissionLocationAccuracyState {
    UNKNOWN,
    FULL,
    REDUCED,
}

data class MissionLocationState(
    val services: MissionLocationServicesState = MissionLocationServicesState.UNKNOWN,
    val authorization: MissionLocationAuthorizationState =
        MissionLocationAuthorizationState.NOT_DETERMINED,
    val accuracy: MissionLocationAccuracyState = MissionLocationAccuracyState.UNKNOWN,
    val isTracking: Boolean = false,
    val isAwaitingAlwaysAuthorizationResult: Boolean = false,
    val alwaysRequestDidNotUpgrade: Boolean = false,
    val revision: Long = 0L,
) {
    val canTrackInBackground: Boolean
        get() = services == MissionLocationServicesState.ENABLED &&
            authorization == MissionLocationAuthorizationState.ALWAYS
}

internal enum class MissionLocationPermissionDecision {
    READY,
    EXPLAIN_WHEN_IN_USE,
    EXPLAIN_ALWAYS,
    CONFIRM_ALWAYS_RESULT,
    WARN_REDUCED_ACCURACY,
    SERVICES_DISABLED,
    DENIED,
    ALWAYS_NOT_GRANTED,
    RESTRICTED,
}

internal fun MissionLocationState.permissionDecision(
    didRequestFullAccuracy: Boolean,
): MissionLocationPermissionDecision = when {
    services == MissionLocationServicesState.DISABLED ->
        MissionLocationPermissionDecision.SERVICES_DISABLED

    authorization == MissionLocationAuthorizationState.DENIED ->
        MissionLocationPermissionDecision.DENIED

    authorization == MissionLocationAuthorizationState.RESTRICTED ->
        MissionLocationPermissionDecision.RESTRICTED

    authorization == MissionLocationAuthorizationState.NOT_DETERMINED ->
        MissionLocationPermissionDecision.EXPLAIN_WHEN_IN_USE

    authorization == MissionLocationAuthorizationState.WHEN_IN_USE ->
        when {
            isAwaitingAlwaysAuthorizationResult ->
                MissionLocationPermissionDecision.CONFIRM_ALWAYS_RESULT

            alwaysRequestDidNotUpgrade ->
                MissionLocationPermissionDecision.ALWAYS_NOT_GRANTED

            else -> MissionLocationPermissionDecision.EXPLAIN_ALWAYS
        }

    authorization == MissionLocationAuthorizationState.ALWAYS &&
        accuracy == MissionLocationAccuracyState.REDUCED &&
        !didRequestFullAccuracy ->
        MissionLocationPermissionDecision.WARN_REDUCED_ACCURACY

    else -> MissionLocationPermissionDecision.READY
}

val DefaultMissionLocationState = MissionLocationState(
    services = MissionLocationServicesState.ENABLED,
    authorization = MissionLocationAuthorizationState.ALWAYS,
    accuracy = MissionLocationAccuracyState.FULL,
)
