package com.joon.ringout.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Storage
import androidx.datastore.preferences.core.Preferences

internal const val InstallationPreferencesFileName = "ringout.installation.preferences_pb"

internal fun createInstallationPreferencesDataStore(
    storage: Storage<Preferences>,
): DataStore<Preferences> = DataStoreFactory.create(storage = storage)
