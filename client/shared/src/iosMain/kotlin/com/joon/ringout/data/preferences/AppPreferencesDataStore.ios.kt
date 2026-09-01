package com.joon.ringout.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.core.okio.OkioStorage
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesSerializer
import kotlinx.cinterop.ExperimentalForeignApi
import okio.FileSystem
import okio.Path.Companion.toPath
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

internal fun getAppPreferencesDataStore(): DataStore<Preferences> =
    IosAppPreferencesDataStore.instance

private object IosAppPreferencesDataStore {
    @OptIn(ExperimentalForeignApi::class)
    val instance: DataStore<Preferences> by lazy {
        createAppPreferencesDataStore(
            storage = OkioStorage(
                fileSystem = FileSystem.SYSTEM,
                serializer = PreferencesSerializer,
                producePath = {
                    val documentsDirectory: NSURL? = NSFileManager.defaultManager.URLForDirectory(
                        directory = NSDocumentDirectory,
                        inDomain = NSUserDomainMask,
                        appropriateForURL = null,
                        create = false,
                        error = null,
                    )
                    (requireNotNull(documentsDirectory?.path) + "/$AppPreferencesFileName")
                        .toPath()
                },
            ),
        )
    }
}
