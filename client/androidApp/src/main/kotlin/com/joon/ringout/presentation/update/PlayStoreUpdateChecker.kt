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
    private val appUpdateManager = AppUpdateManagerFactory.create(activity.applicationContext)

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

    fun openStore() {
        val packageName = activity.packageName
        val marketIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("market://details?id=$packageName"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            activity.startActivity(marketIntent)
        } catch (_: ActivityNotFoundException) {
            activity.startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=$packageName"),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}
