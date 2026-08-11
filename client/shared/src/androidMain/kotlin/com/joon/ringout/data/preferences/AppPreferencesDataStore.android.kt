package com.joon.ringout.data.preferences

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.core.DataStore
import androidx.datastore.core.FileStorage
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesFileSerializer

@Composable
internal actual fun rememberAppPreferencesDataStore(): DataStore<Preferences> {
    val applicationContext = LocalContext.current.applicationContext
    return remember(applicationContext) {
        AndroidAppPreferencesDataStore.get(applicationContext)
    }
}

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
