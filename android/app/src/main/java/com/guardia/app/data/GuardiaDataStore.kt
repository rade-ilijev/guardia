package com.guardia.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

/** Single process-wide DataStore shared by all repositories. */
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "guardia")
