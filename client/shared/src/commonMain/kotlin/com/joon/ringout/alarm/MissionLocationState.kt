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
    val alwaysAuthorizationRequestFailed: Boolean = false,
    val revision: Long = 0L,
) {
    val canStartContinuousTracking: Boolean
        get() = services == MissionLocationServicesState.ENABLED &&
            (
                authorization == MissionLocationAuthorizationState.WHEN_IN_USE ||
                    authorization == MissionLocationAuthorizationState.ALWAYS
            )
}

internal enum class MissionLocationPermissionDecision {
    READY,
    EXPLAIN_WHEN_IN_USE,
    EXPLAIN_ALWAYS,
    CONFIRM_ALWAYS_RESULT,
    WARN_REDUCED_ACCURACY,
    SERVICES_DISABLED,
    DENIED,
    ALWAYS_REQUEST_FAILED,
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

    alwaysAuthorizationRequestFailed ->
        MissionLocationPermissionDecision.ALWAYS_REQUEST_FAILED

    isAwaitingAlwaysAuthorizationResult ->
        MissionLocationPermissionDecision.CONFIRM_ALWAYS_RESULT

    authorization == MissionLocationAuthorizationState.WHEN_IN_USE &&
        !alwaysRequestDidNotUpgrade ->
        MissionLocationPermissionDecision.EXPLAIN_ALWAYS

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
