package com.joon.ringout.data.preferences

import androidx.compose.runtime.Composable
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Storage
import androidx.datastore.preferences.core.Preferences

internal const val AppPreferencesFileName = "ringout.preferences_pb"

internal fun createAppPreferencesDataStore(
    storage: Storage<Preferences>,
): DataStore<Preferences> = DataStoreFactory.create(storage = storage)

@Composable
internal expect fun rememberAppPreferencesDataStore(): DataStore<Preferences>
