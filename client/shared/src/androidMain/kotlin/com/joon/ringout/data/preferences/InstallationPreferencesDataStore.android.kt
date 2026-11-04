package com.joon.ringout.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.FileStorage
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesFileSerializer

internal fun getInstallationPreferencesDataStore(context: Context): DataStore<Preferences> =
    AndroidInstallationPreferencesDataStore.get(context.applicationContext)

private object AndroidInstallationPreferencesDataStore {
    @Volatile
    private var instance: DataStore<Preferences>? = null

    fun get(context: Context): DataStore<Preferences> =
        instance ?: synchronized(this) {
            instance ?: createInstallationPreferencesDataStore(
                storage = FileStorage(
                    serializer = PreferencesFileSerializer,
                    produceFile = { context.noBackupFilesDir.resolve(InstallationPreferencesFileName) },
                ),
            ).also { dataStore -> instance = dataStore }
        }
}
