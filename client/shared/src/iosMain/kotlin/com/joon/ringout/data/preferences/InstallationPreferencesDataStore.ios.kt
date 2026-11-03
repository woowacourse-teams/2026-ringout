@file:OptIn(
    kotlinx.cinterop.BetaInteropApi::class,
    kotlinx.cinterop.ExperimentalForeignApi::class,
)

package com.joon.ringout.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.core.okio.OkioStorage
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesSerializer
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import okio.FileSystem
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUserDomainMask

internal fun getInstallationPreferencesDataStore(): DataStore<Preferences> =
    IosInstallationPreferencesDataStore.instance

private object IosInstallationPreferencesDataStore {
    val instance: DataStore<Preferences> by lazy {
        createInstallationPreferencesDataStore(
            storage = OkioStorage(
                fileSystem = FileSystem.SYSTEM,
                serializer = PreferencesSerializer,
                producePath = ::installationPreferencesPath,
            ),
        )
    }
}

private fun installationPreferencesPath(): Path = memScoped {
    val error = alloc<ObjCObjectVar<NSError?>>()
    error.value = null
    val applicationSupportDirectory = NSFileManager.defaultManager.URLForDirectory(
        directory = NSApplicationSupportDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = true,
        error = error.ptr,
    ) ?: throw IOException(
        error.value?.localizedDescription ?: "Cannot locate Application Support for installation state.",
    )
    prepareInstallationPreferencesPath(applicationSupportDirectory)
}

internal fun prepareInstallationPreferencesPath(applicationSupportDirectory: NSURL): Path = memScoped {
    val directory = applicationSupportDirectory.URLByAppendingPathComponent(
        InstallationPreferencesDirectoryName,
        isDirectory = true,
    ) ?: throw IOException("Cannot build installation state directory URL.")
    val error = alloc<ObjCObjectVar<NSError?>>()
    error.value = null
    if (!NSFileManager.defaultManager.createDirectoryAtURL(
            url = directory,
            withIntermediateDirectories = true,
            attributes = null,
            error = error.ptr,
        )
    ) {
        throw IOException(error.value?.localizedDescription ?: "Cannot create installation state directory.")
    }
    error.value = null
    if (!directory.setResourceValue(
            value = NSNumber(bool = true),
            forKey = NSURLIsExcludedFromBackupKey,
            error = error.ptr,
        )
    ) {
        throw IOException(error.value?.localizedDescription ?: "Cannot exclude installation state from backup.")
    }
    val directoryPath = directory.path
        ?: throw IOException("Installation state directory has no filesystem path.")
    // The directory retains its exclusion when DataStore atomically replaces the file.
    "$directoryPath/$InstallationPreferencesFileName".toPath()
}

private const val InstallationPreferencesDirectoryName = "ringout.installation"
