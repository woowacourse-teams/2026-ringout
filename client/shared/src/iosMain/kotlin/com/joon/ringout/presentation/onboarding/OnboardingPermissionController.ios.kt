package com.joon.ringout.presentation.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.joon.ringout.platform.IosOnboardingPermissionService
import com.joon.ringout.platform.LocalIosNativeServices

@Composable
internal actual fun rememberOnboardingPermissionController(): OnboardingPermissionController {
    val nativeServices = LocalIosNativeServices.current
    return remember(nativeServices) {
        IosOnboardingPermissionController(nativeServices.onboardingPermissionService())
    }
}

internal class IosOnboardingPermissionController(
    private val service: IosOnboardingPermissionService,
) : OnboardingPermissionController {
    private var pendingPermission: OnboardingPermission? = null
    private var requestId = 0L
    private val pendingCallbacks = mutableListOf<(OnboardingPermissionStatus) -> Unit>()

    override fun refresh(onResult: (OnboardingPermissionState) -> Unit) {
        var delivered = false
        fun deliver(state: OnboardingPermissionState) {
            if (delivered) return
            delivered = true
            onResult(state)
        }
        try {
            service.refresh(::deliver)
        } catch (_: Exception) {
            deliver(
                OnboardingPermissionState(
                    location = OnboardingPermissionStatus.Unavailable,
                    notifications = OnboardingPermissionStatus.Unavailable,
                ),
            )
        }
    }

    override fun request(
        permission: OnboardingPermission,
        onResult: (OnboardingPermissionStatus) -> Unit,
    ) {
        if (pendingPermission != null) {
            if (pendingPermission == permission) {
                pendingCallbacks += onResult
            } else {
                onResult(OnboardingPermissionStatus.Unavailable)
            }
            return
        }
        pendingPermission = permission
        pendingCallbacks += onResult
        val currentRequestId = ++requestId

        fun finish(status: OnboardingPermissionStatus) {
            if (requestId != currentRequestId || pendingPermission != permission) return
            val callbacks = pendingCallbacks.toList()
            pendingCallbacks.clear()
            pendingPermission = null
            callbacks.forEach { it(status) }
        }
        try {
            service.request(permission, ::finish)
        } catch (_: Exception) {
            finish(OnboardingPermissionStatus.Unavailable)
        }
    }
}
