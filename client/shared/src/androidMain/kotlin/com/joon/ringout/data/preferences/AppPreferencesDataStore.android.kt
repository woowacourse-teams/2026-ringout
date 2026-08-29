package com.joon.ringout.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.FileStorage
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesFileSerializer

internal fun getAppPreferencesDataStore(context: Context): DataStore<Preferences> =
    AndroidAppPreferencesDataStore.get(context.applicationContext)

private object AndroidAppPreferencesDataStore {
    @Volatile
    private var instance: DataStore<Preferences>? = null

    fun get(context: Context): DataStore<Preferences> =
        instance ?: synchronized(this) {
            instance ?: createAppPreferencesDataStore(
                storage = FileStorage(
                    serializer = PreferencesFileSerializer,
                    produceFile = { context.filesDir.resolve(AppPreferencesFileName) },
                ),
            ).also { dataStore -> instance = dataStore }
        }
}
