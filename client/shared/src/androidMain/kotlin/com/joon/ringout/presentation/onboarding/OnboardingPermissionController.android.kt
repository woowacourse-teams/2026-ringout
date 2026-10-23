package com.joon.ringout.presentation.onboarding

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.joon.ringout.alarm.NotificationPermissionPrompt
import com.joon.ringout.alarm.hasMissionFineLocationPermission
import com.joon.ringout.alarm.isMissionLocationEnabled
import com.joon.ringout.alarm.missionLocationSettingsIntent

@Composable
internal actual fun rememberOnboardingPermissionController(): OnboardingPermissionController {
    val context = LocalContext.current
    val resultHandler = remember { PermissionResultHandler() }
    val runtimePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { resultHandler.onResult() }
    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { resultHandler.onResult() }
    val access = remember(context) { AndroidOnboardingPermissionAccess(context) }
    val controller = viewModel {
        AndroidOnboardingPermissionController(
            access = access,
            launchRuntimePermissions = runtimePermissionLauncher::launch,
            launchSettings = settingsLauncher::launch,
        )
    }
    controller.bind(access, runtimePermissionLauncher::launch, settingsLauncher::launch)
    // Registering a new launcher can synchronously deliver the result of a recreated Activity.
    resultHandler.onResult = controller::completeRequest
    DisposableEffect(controller, access, resultHandler) {
        onDispose {
            resultHandler.onResult = {}
            controller.unbind(access)
        }
    }
    return controller
}

/** Reads OS state after every result, including a dismissed popup or a return from Settings. */
internal class AndroidOnboardingPermissionController(
    access: AndroidOnboardingPermissionAccess,
    launchRuntimePermissions: (Array<String>) -> Unit,
    launchSettings: (Intent) -> Unit,
) : ViewModel(), OnboardingPermissionController {
    private var binding: AndroidPermissionBinding? = AndroidPermissionBinding(
        access,
        launchRuntimePermissions,
        launchSettings,
    )
    private var pendingRequest: PendingPermissionRequest? = null

    fun bind(
        access: AndroidOnboardingPermissionAccess,
        launchRuntimePermissions: (Array<String>) -> Unit,
        launchSettings: (Intent) -> Unit,
    ) {
        binding = AndroidPermissionBinding(access, launchRuntimePermissions, launchSettings)
    }

    fun unbind(access: AndroidOnboardingPermissionAccess) {
        if (binding?.access === access) binding = null
    }

    override fun refresh(onResult: (OnboardingPermissionState) -> Unit) {
        val access = binding?.access
        onResult(
            OnboardingPermissionState(
                location = access?.status(OnboardingPermission.Location) ?: OnboardingPermissionStatus.Unavailable,
                notifications = access?.status(OnboardingPermission.Notifications) ?: OnboardingPermissionStatus.Unavailable,
            ),
        )
    }

    override fun request(
        permission: OnboardingPermission,
        onResult: (OnboardingPermissionStatus) -> Unit,
    ) {
        if (pendingRequest != null) return
        val binding = binding
        if (binding == null) {
            onResult(OnboardingPermissionStatus.Unavailable)
            return
        }
        val status = binding.access.status(permission)
        if (status == OnboardingPermissionStatus.Granted ||
            status == OnboardingPermissionStatus.Unavailable
        ) {
            onResult(status)
            return
        }
        pendingRequest = PendingPermissionRequest(permission, onResult)
        try {
            when (status) {
                OnboardingPermissionStatus.Requestable -> {
                    binding.access.markRequested(permission)
                    binding.launchRuntimePermissions(permission.runtimePermissions())
                }
                OnboardingPermissionStatus.ServicesDisabled,
                OnboardingPermissionStatus.SettingsRequired,
                OnboardingPermissionStatus.Restricted,
                -> binding.launchSettings(binding.access.settingsIntent(permission, status))
                else -> completeRequest()
            }
        } catch (_: RuntimeException) {
            val pending = pendingRequest
            pendingRequest = null
            pending?.onResult?.invoke(OnboardingPermissionStatus.Unavailable)
        }
    }

    fun completeRequest() {
        val pending = pendingRequest ?: return
        val access = binding?.access ?: return
        pendingRequest = null
        pending.onResult(access.status(pending.permission))
    }

    override fun onCleared() {
        pendingRequest = null
        binding = null
    }
}

internal class AndroidOnboardingPermissionAccess(
    private val context: Context,
    private val isRevokedByPolicy: (String) -> Boolean = { permission ->
        context.packageManager.isPermissionRevokedByPolicy(permission, context.packageName)
    },
    private val shouldShowRationale: (String) -> Boolean = { permission ->
        context.onboardingActivity()?.shouldShowRequestPermissionRationale(permission) == true
    },
) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "ringout_permission_prompts",
        Context.MODE_PRIVATE,
    )

    fun status(permission: OnboardingPermission): OnboardingPermissionStatus = when (permission) {
        OnboardingPermission.Location -> locationStatus()
        OnboardingPermission.Notifications -> notificationStatus()
    }

    fun markRequested(permission: OnboardingPermission) {
        when (permission) {
            OnboardingPermission.Location -> preferences.edit()
                .putBoolean(LocationPermissionRequested, true)
                .apply()
            OnboardingPermission.Notifications -> NotificationPermissionPrompt.markRequested(context)
        }
    }

    fun settingsIntent(
        permission: OnboardingPermission,
        status: OnboardingPermissionStatus,
    ): Intent = when {
        permission == OnboardingPermission.Location &&
            status == OnboardingPermissionStatus.ServicesDisabled -> missionLocationSettingsIntent()
        permission == OnboardingPermission.Notifications -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        else -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.parse("package:${context.packageName}"))
    }

    private fun locationStatus(): OnboardingPermissionStatus {
        if (!context.hasMissionFineLocationPermission()) {
            return requestStatus(
                Manifest.permission.ACCESS_FINE_LOCATION,
                preferences.getBoolean(LocationPermissionRequested, false),
            )
        }
        return if (context.isMissionLocationEnabled()) {
            OnboardingPermissionStatus.Granted
        } else {
            OnboardingPermissionStatus.ServicesDisabled
        }
    }

    private fun notificationStatus(): OnboardingPermissionStatus {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return requestStatus(
                Manifest.permission.POST_NOTIFICATIONS,
                NotificationPermissionPrompt.hasRequested(context),
            )
        }
        val notificationsEnabled = context.getSystemService(NotificationManager::class.java)
            .areNotificationsEnabled()
        return if (notificationsEnabled) {
            OnboardingPermissionStatus.Granted
        } else {
            OnboardingPermissionStatus.SettingsRequired
        }
    }

    private fun requestStatus(permission: String, hasRequested: Boolean): OnboardingPermissionStatus = when {
        isRevokedByPolicy(permission) -> OnboardingPermissionStatus.Restricted
        !hasRequested || shouldShowRationale(permission) -> OnboardingPermissionStatus.Requestable
        else -> OnboardingPermissionStatus.SettingsRequired
    }
}

private fun OnboardingPermission.runtimePermissions(): Array<String> = when (this) {
    OnboardingPermission.Location -> arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    )
    OnboardingPermission.Notifications -> arrayOf(Manifest.permission.POST_NOTIFICATIONS)
}

private tailrec fun Context.onboardingActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.onboardingActivity()
    else -> null
}

private class PermissionResultHandler {
    var onResult: () -> Unit = {}
}

private data class PendingPermissionRequest(
    val permission: OnboardingPermission,
    val onResult: (OnboardingPermissionStatus) -> Unit,
)

private data class AndroidPermissionBinding(
    val access: AndroidOnboardingPermissionAccess,
    val launchRuntimePermissions: (Array<String>) -> Unit,
    val launchSettings: (Intent) -> Unit,
)

private const val LocationPermissionRequested = "onboarding_location_permission_requested"
