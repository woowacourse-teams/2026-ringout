package com.joon.ringout.presentation.update

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.UpdateAvailability

internal class PlayStoreUpdateChecker(
    private val activity: Activity,
    private val currentVersionCode: Int,
) {
    private val appUpdateManager by lazy { AppUpdateManagerFactory.create(activity.applicationContext) }

    fun check(onUpdateAvailable: () -> Unit) {
        appUpdateManager.appUpdateInfo
            .addOnSuccessListener(activity) { updateInfo ->
                if (
                    updateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                    updateInfo.availableVersionCode() > currentVersionCode
                ) {
                    onUpdateAvailable()
                }
            }
    }

    fun openStore(): Boolean {
        val packageName = activity.packageName
        return tryOpen("market://details?id=$packageName") ||
            tryOpen("https://play.google.com/store/apps/details?id=$packageName")
    }

    private fun tryOpen(uri: String): Boolean =
        try {
            activity.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(uri))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
}
