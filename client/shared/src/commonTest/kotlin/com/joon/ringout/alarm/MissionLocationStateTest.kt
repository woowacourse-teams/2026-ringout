package com.joon.ringout.alarm

import kotlin.test.Test
import kotlin.test.assertEquals

class MissionLocationStateTest {
    @Test
    fun requestsPermissionsInWhenInUseThenAlwaysOrder() {
        assertEquals(
            MissionLocationPermissionDecision.EXPLAIN_WHEN_IN_USE,
            state(MissionLocationAuthorizationState.NOT_DETERMINED)
                .permissionDecision(didRequestFullAccuracy = false),
        )
        assertEquals(
            MissionLocationPermissionDecision.EXPLAIN_ALWAYS,
            state(MissionLocationAuthorizationState.WHEN_IN_USE)
                .permissionDecision(didRequestFullAccuracy = false),
        )
        assertEquals(
            MissionLocationPermissionDecision.READY,
            state(MissionLocationAuthorizationState.ALWAYS)
                .permissionDecision(didRequestFullAccuracy = false),
        )
    }

    @Test
    fun reducedAccuracyIsRequestedOnceBeforeMissionStarts() {
        val reduced = state(
            authorization = MissionLocationAuthorizationState.ALWAYS,
            accuracy = MissionLocationAccuracyState.REDUCED,
        )
        assertEquals(
            MissionLocationPermissionDecision.WARN_REDUCED_ACCURACY,
            reduced.permissionDecision(didRequestFullAccuracy = false),
        )
        assertEquals(
            MissionLocationPermissionDecision.READY,
            reduced.permissionDecision(didRequestFullAccuracy = true),
        )
    }

    @Test
    fun disabledServicesAndDeniedPermissionCannotStartMission() {
        assertEquals(
            MissionLocationPermissionDecision.SERVICES_DISABLED,
            state(
                authorization = MissionLocationAuthorizationState.ALWAYS,
                services = MissionLocationServicesState.DISABLED,
            ).permissionDecision(false),
        )
        assertEquals(
            MissionLocationPermissionDecision.DENIED,
            state(MissionLocationAuthorizationState.DENIED).permissionDecision(false),
        )
    }

    @Test
    fun unchangedWhenInUseAfterAlwaysRequestBecomesAnExplicitResult() {
        assertEquals(
            MissionLocationPermissionDecision.CONFIRM_ALWAYS_RESULT,
            state(
                authorization = MissionLocationAuthorizationState.WHEN_IN_USE,
                isAwaitingAlwaysAuthorizationResult = true,
            ).permissionDecision(false),
        )
        assertEquals(
            MissionLocationPermissionDecision.ALWAYS_NOT_GRANTED,
            state(
                authorization = MissionLocationAuthorizationState.WHEN_IN_USE,
                alwaysRequestDidNotUpgrade = true,
            ).permissionDecision(false),
        )
    }

    private fun state(
        authorization: MissionLocationAuthorizationState,
        services: MissionLocationServicesState = MissionLocationServicesState.ENABLED,
        accuracy: MissionLocationAccuracyState = MissionLocationAccuracyState.FULL,
        isAwaitingAlwaysAuthorizationResult: Boolean = false,
        alwaysRequestDidNotUpgrade: Boolean = false,
    ) = MissionLocationState(
        services = services,
        authorization = authorization,
        accuracy = accuracy,
        isAwaitingAlwaysAuthorizationResult = isAwaitingAlwaysAuthorizationResult,
        alwaysRequestDidNotUpgrade = alwaysRequestDidNotUpgrade,
    )
}
